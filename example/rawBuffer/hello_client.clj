(ns rawBuffer.hello-client
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [org.dlacko.async-ipc :as ipc]))

;; Compatibe with https://github.com/RIAEvangelist/node-ipc/blob/master/example/unixWindowsSocket/rawBuffer/world.server.js

(defn ->bytes
  [str]
  (bytes (byte-array (map (comp byte int) str))))

(defn ->str
  [data]
  (apply str (map char data)))

(defn -main []
  (let [{:keys [out in]} (ipc/connect-to "world" :rawBuffer true)]
    (async/>!! out (->bytes "hello"))
    (loop []
      (when-let [data (async/<!! in)]
        (log/info "got a message from world" (->str data))
        (recur)))))
