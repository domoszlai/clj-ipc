(ns rawBuffer.world-server
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [org.dlacko.async-ipc :as ipc]))

(defn to-bytes
  [str]
  (bytes (byte-array (map (comp byte int) str))))

(defn -main []
  (let [server (ipc/serve "world" :rawBuffer true)]
    (loop []
      (when-let [{:keys [in out]} (async/<!! (:connections server))]
        (async/go
          (async/>! out (to-bytes "hello"))
          (loop []
            (if-let [data (async/<! in)]
              (let [str (apply str (map char data))]
                (log/info "Got a message" str)
                (async/>! out (to-bytes "goodbye")))
              (recur))))
        (recur)))))
