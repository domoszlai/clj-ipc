(ns rawBuffer.world-server
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [org.dlacko.async-ipc :as ipc]))

;; Compatibe with https://github.com/RIAEvangelist/node-ipc/blob/master/example/unixWindowsSocket/rawBuffer/hello-client.js

(defn ->bytes
  [str]
  (bytes (byte-array (map (comp byte int) str))))

(defn ->str
  [data]
  (apply str (map char data)))

(defn -main []
  (let [server (ipc/serve "world" :rawBuffer true)]
    (loop []
      (when-let [{:keys [in out]} (async/<!! (:connections server))]
        (async/go
          (async/>! out (->bytes "hello"))
          (loop []
            (if-let [data (async/<! in)]
              (let [str (->str data)]
                (log/info "Got a message" str)
                (async/>! out (->bytes "goodbye")))
              (recur))))
        (recur)))))
