(ns org.dlacko.async-ipc
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import  [java.net Socket ServerSocket SocketException]
            [java.io File BufferedReader BufferedWriter]
            [org.newsclub.net.unix AFUNIXSocket AFUNIXServerSocket AFUNIXSocketAddress]))

(def ^:dynamic config
  {;; used for Unix Socket (Unix Domain Socket) namespacing. If not set specifically,
   ;; the Unix Domain Socket will combine the socketRoot, appspace, and id to form the
   ;; Unix Socket Path for creation or binding. This is available in case you have many
   ;; apps running on your system, you may have several sockets with the same id, but if
   ;; you change the appspace, you will still have app specic unique sockets.
   :appspace "app"
   ;; the directory in which to create or bind to a Unix Socket
   :socketRoot "/tmp/"
   ;; the delimiter at the end of each data packet.
   :delimiter \formfeed})

(defn- make-path
  [id]
  (str (:socketRoot config) (:appspace config) "." id))

(defn- socket-open? [^Socket socket]
  (not (or (.isClosed socket) (.isInputShutdown socket) (.isOutputShutdown socket))))

(defn- read-data-packet
  [^BufferedReader in]
  (loop [chars []]
    (let [byte (.read in)]
      (if (or (= byte -1) (= byte (int (:delimiter config))))
        (apply str chars)
        (recur (conj chars (char byte)))))))

(defn- socket-read-msg-or-nil [^Socket socket ^BufferedReader in]
  (when (socket-open? socket)
    (try
      (read-data-packet in)
      (catch SocketException e
        (log/error e)))))

(defn- socket-write-msg [^Socket socket ^BufferedWriter out msg]
  (if (socket-open? socket)
    (try
      (.write out (str msg "\f"))
      (when *flush-on-newline* (.flush out))
      true
      (catch SocketException e
        (log/error e)
        false))
    false))

(defrecord AsyncSocket
           [^Socket socket address in out])
(defrecord AsyncSocketServer
           [^Integer backlog address ^ServerSocket server connections])

(def ^Integer default-server-backlog 50) ;; derived from SocketServer.java

(defn close-socket-client [{:keys [in out socket address] :as this}]
  (log/info "Closing async socket on address" address)
  (when-not (.isInputShutdown socket)  (.shutdownInput socket))
  (when-not (.isOutputShutdown socket) (.shutdownOutput socket))
  (when-not (.isClosed socket)         (.close socket))
  (async/close! in)
  (async/close! out)
  (assoc this :socket nil :in nil :out nil))

(defn- init-async-socket [^Socket socket address]
  (let [^BufferedReader in (io/reader socket)
        ^BufferedWriter out (io/writer socket)
        in-ch (async/chan)
        out-ch (async/chan)
        public-socket (map->AsyncSocket {:socket socket :address address :in in-ch :out out-ch})]

    (async/go-loop []
      (let [line (socket-read-msg-or-nil socket in)
            msg (json/decode line true)]
        (if-not msg
          (close-socket-client public-socket)
          (do
            (async/>! in-ch msg)
            (recur)))))

    (async/go-loop []
      (let [line (and (socket-open? socket) (async/<! out-ch))]
        (if-not (socket-write-msg socket out (json/encode line))
          (close-socket-client public-socket)
          (recur))))

    (log/info "New async socket opened on address" address)
    public-socket))

(defn socket-client
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to, returns an AsyncSocket which must be explicitly
   started and stopped by the consumer. Observes value of *flush-on-newline* var for purposes of socket flushing."
  ([id]
   (let [unix-socket-path (make-path id)
         socket-file (File. unix-socket-path)
         socket (AFUNIXSocket/newInstance)]

     (.connect socket (AFUNIXSocketAddress. socket-file))
     (init-async-socket socket unix-socket-path))))

(defn server-running? [{:keys [^ServerSocket server]}]
  (and server (not (.isClosed server))))

(defn stop-socket-server [{:keys [^ServerSocket server connections address] :as this}]
  (when (server-running? this)
    (log/info "Stopping async socket server on address" address)
    (async/close! connections)
    (.close server)
    (assoc this :server nil :connections nil)))

(defn socket-server
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to and optional backlog (the maximum queue
   length of incoming connection indications, 50 by default), starts and returns a socket server
   and a :connections channel that yields a new socket client on each connection.
   Observes value of *flush-on-newline* var for purposes of socket flushing."
  ([id]
   (socket-server id default-server-backlog))
  ([id backlog]
   (let [unix-socket-path (make-path id)
         socket-file (File. unix-socket-path)
         java-server (AFUNIXServerSocket/newInstance)
         conns (async/chan backlog)
         public-server (map->AsyncSocketServer
                        {:backlog     (int backlog)
                         :address     unix-socket-path
                         :connections conns
                         :server      java-server})]

     (.bind java-server (AFUNIXSocketAddress. socket-file))

     (log/info "Starting async socket server on address" unix-socket-path)

     (async/go-loop []
       (if (and (not (.isClosed java-server)) (.isBound java-server))
         (do
           (try
             (async/>! conns
                       (init-async-socket (.accept java-server) unix-socket-path))
             (catch SocketException e
               (log/error e)
               (stop-socket-server public-server)))
           (recur))
         (stop-socket-server public-server)))

     public-server)))
