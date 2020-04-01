(ns org.dlacko.async-ipc
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.core.async.impl.protocols]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import  [java.net Socket ServerSocket SocketException]
            [java.io File BufferedReader InputStream OutputStream]
            [java.util Arrays]
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
   :delimiter \formfeed
   ;; if true, data will be sent and received as a raw node Buffer NOT an Object as JSON.
   ;; This is great for Binary or hex IPC, and communicating with other processes in languages like C and C++
   :rawBuffer	false
   ;; maximum amount of bytes receiving at once when rawBuffer is true
   :max-buffer-size 8192})

(defn- make-path
  [id]
  (str (:socketRoot config) (:appspace config) "." id))

(defn- socket-open? [^Socket socket]
  (not (or (.isClosed socket) (.isInputShutdown socket) (.isOutputShutdown socket))))

(defn- read-object-data-packet
  [^BufferedReader in]
  (let [delimiter (int (:delimiter config))
        buffer (StringBuilder.)]
    (loop []
      (let [ichr (.read in)]
        (if (or (= ichr -1) (= ichr delimiter))
          (.toString buffer)
          (do
            (.append buffer (char ichr))
            (recur)))))))

(defn- read-raw-data-packet
  [^InputStream in]
  (let [avail (.available in)
        buf-size (if (= 0 avail)
                   (:max-buffer-size config)
                   (min avail (:max-buffer-size config)))
        buf (byte-array buf-size)
        r (.read in buf)]
    (cond
      (= r -1) nil
      (= r buf-size) buf
      :else (Arrays/copyOf buf r))))

(defn- socket-read-object [^BufferedReader in]
  (let [msg (read-object-data-packet in)]
    (if (> (count msg) 0)
      (json/decode msg true)
      nil)))

(defn- socket-read-bytes [^InputStream is]
  (let [bytes (read-raw-data-packet is)]
    (if (> (count bytes) 0)
      bytes
      nil)))

(defn- socket-write-object [^OutputStream os msg]
  (let [out (io/writer os)]
    (.write out (json/encode msg))
    (.write out (int (:delimiter config)))
    (.flush out)))

(defn- socket-write-bytes [^OutputStream os bytes]
  (.write os bytes)
  (.flush os))

(defrecord AsyncSocket
           [^Socket socket address in out error])
(defrecord AsyncSocketServer
           [^ServerSocket server address connections error])

(defn disconnect [{:keys [in out socket address] :as this}]
  (log/info "Closing async socket client on address" address)
  (async/close! in)
  (async/close! out)
  (when-not (.isInputShutdown socket)
    (try (.shutdownInput socket) (catch Exception _)))
  (when-not (.isOutputShutdown socket)
    (try (.shutdownOutput socket) (catch Exception _)))
  (when-not (.isClosed socket)
    (try (.close socket) (catch Exception _)))
  (assoc this :socket nil :in nil :out nil :error nil))

(defn- init-async-socket [^Socket socket address]
  (let [^InputStream is (io/input-stream socket)
        ^OutputStream os (io/output-stream socket)
        in-ch (async/chan)
        out-ch (async/chan)
        error-ch (async/chan)
        public-socket (map->AsyncSocket {:socket socket
                                         :address address
                                         :in in-ch
                                         :out out-ch
                                         :error error-ch})]

    (let [read-msg (if (:rawBuffer config)
                     #(socket-read-bytes is)
                     (partial socket-read-object (io/reader is)))]
      (async/thread
        (loop []
          (let [[close?, msg] (try
                                (let [msg (read-msg)]
                                  [(nil? msg), msg])
                                (catch SocketException e
                                  (async/>!! error-ch e)
                                  [true, nil])
                                (catch Exception e
                                  (async/>!! error-ch e)
                                  [false, nil]))]
            (if close?
              ; If conns is closed, shutdown was explicit by disconnect
              (when-not (clojure.core.async.impl.protocols/closed? in-ch)
                (disconnect public-socket))
              (do
                (when msg
                  (async/>!! in-ch msg))
                (recur)))))))

    (async/thread
      (loop []
        (let [msg (async/<!! out-ch)]
          (when (and msg (socket-open? socket))
            (try
              (if (:rawBuffer config)
                (socket-write-bytes os msg)
                (socket-write-object os msg))
              (catch SocketException e
                (async/>!! error-ch e)
                (disconnect public-socket)))
            (recur)))))

    (log/info "New async socket client opened on address" address)
    public-socket))

(defn connect-to
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to, returns an AsyncSocket which must be explicitly
   started and stopped by the consumer."
  [id & {:as options}]
  (with-bindings {#'config (merge config options)}
    (let [unix-socket-path (make-path id)
          socket-file (File. unix-socket-path)
          socket (AFUNIXSocket/newInstance)]

      (.connect socket (AFUNIXSocketAddress. socket-file))
      (init-async-socket socket unix-socket-path))))

(defn server-running? [{:keys [^ServerSocket server]}]
  (and server (not (.isClosed server))))

(defn stop-server [{:keys [^ServerSocket server connections address] :as this}]
  (when (server-running? this)
    (log/info "Stopping async socket server on address" address)
    (async/close! connections)
    (try
      (.close server)
      (catch Exception _))
    (assoc this :server nil :connections nil :error nil)))

(defn serve
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to, starts and returns a socket server
   and a :connections channel that yields a new socket client on each connection."
  [id & {:as options}]
  (with-bindings {#'config (merge config options)}
    (let [unix-socket-path (make-path id)
          socket-file (File. unix-socket-path)
          java-server (AFUNIXServerSocket/newInstance)
          conns-ch (async/chan)
          error-ch (async/chan)
          public-server (map->AsyncSocketServer
                         {:address     unix-socket-path
                          :connections conns-ch
                          :server      java-server
                          :error       error-ch})]

      (.bind java-server (AFUNIXSocketAddress. socket-file))

      (log/info "Starting async socket server on address" unix-socket-path)

      (async/thread
        (loop []
          (when
           (try
             ; >!! returns true if succeeds
             (async/>!! conns-ch
                        (init-async-socket (.accept java-server) unix-socket-path))
             (catch SocketException e
               ; If conns is closed, shutdown was explicit by stop-server
               (when-not (clojure.core.async.impl.protocols/closed? conns-ch)
                 (async/>!! error-ch e)
                 (try
                   (stop-server public-server)
                   (catch Exception _)))))
            (recur))))

      public-server)))
