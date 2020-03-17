(ns org.dlacko.async-ipc
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import  [java.net Socket ServerSocket SocketException]
            [java.io File BufferedReader InputStream OutputStream ByteArrayOutputStream]
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
   ;; the maximum queue length of incoming connection indications
   :backlog 50
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

(defn- socket-read-object [^Socket socket ^InputStream is]
  (when (socket-open? socket)
    (try
      (let [msg (read-object-data-packet (io/reader is))]
        (if (> (count msg) 0)
          (json/decode msg true)
          nil))
      (catch SocketException e
        (log/error e)))))

(defn- socket-read-bytes [^Socket socket ^InputStream is]
  (when (socket-open? socket)
    (try
      (let [bytes (read-raw-data-packet is)]
        (if (> (count bytes) 0)
          bytes
          nil))
      (catch SocketException e
        (log/error e)))))

(defn- socket-write-object [^Socket socket ^OutputStream os msg]
  (if (socket-open? socket)
    (try
      (let [out (io/writer os)]
        (.write out (json/encode msg))
        (.write out (int (:delimiter config)))
        (.flush out)
        true)
      (catch SocketException e
        (log/error e)
        false))
    false))

(defn- socket-write-bytes [^Socket socket ^OutputStream os bytes]
  (if (socket-open? socket)
    (try
      (.write os bytes)
      (.flush os)
      true
      (catch SocketException e
        (log/error e)
        false))
    false))

(defrecord AsyncSocket
           [^Socket socket address in out])
(defrecord AsyncSocketServer
           [^ServerSocket server address connections])

(defn disconnect [{:keys [in out socket address] :as this}]
  (log/info "Closing async socket on address" address)
  (when-not (.isInputShutdown socket)  (.shutdownInput socket))
  (when-not (.isOutputShutdown socket) (.shutdownOutput socket))
  (when-not (.isClosed socket)         (.close socket))
  (async/close! in)
  (async/close! out)
  (assoc this :socket nil :in nil :out nil))

(defn- init-async-socket [^Socket socket address]
  (let [^InputStream is (io/input-stream socket)
        ^OutputStream os (io/output-stream socket)
        in-ch (async/chan)
        out-ch (async/chan)
        public-socket (map->AsyncSocket {:socket socket :address address :in in-ch :out out-ch})]

    (async/go-loop []
      (let [msg (if (:rawBuffer config)
                  (socket-read-bytes socket is)
                  (socket-read-object socket is))]
        (if-not msg
          (disconnect public-socket)
          (do
            (async/>! in-ch msg)
            (recur)))))

    (async/go-loop []
      (let [msg (and (socket-open? socket) (async/<! out-ch))]
        (if-not (if (:rawBuffer config)
                  (socket-write-bytes socket os msg)
                  (socket-write-object socket os msg))
          (disconnect public-socket)
          (recur))))

    (log/info "New async socket opened on address" address)
    public-socket))

(defn connect-to
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to, returns an AsyncSocket which must be explicitly
   started and stopped by the consumer."
  ([id]
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
    (.close server)
    (assoc this :server nil :connections nil)))

(defn serve
  "Used for connecting as a client to local Unix Sockets and Windows Sockets.
   Given a string id of the socket being connected to, starts and returns a socket server
   and a :connections channel that yields a new socket client on each connection."
  [id]
  (let [unix-socket-path (make-path id)
        socket-file (File. unix-socket-path)
        java-server (AFUNIXServerSocket/newInstance)
        conns (async/chan (:backlog config))
        public-server (map->AsyncSocketServer
                       {:address     unix-socket-path
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
              (stop-server public-server)))
          (recur))
        (stop-server public-server)))

    public-server))
