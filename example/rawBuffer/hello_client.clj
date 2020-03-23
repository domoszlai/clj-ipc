(ns rawBuffer.hello-client
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [org.dlacko.async-ipc :as ipc]))

(defn to-bytes
  [str]
  (bytes (byte-array (map (comp byte int) str))))

(defn -main []
  (let [{:keys [out in]} (ipc/connect-to "world" :rawBuffer true)]
    (async/>!! out (to-bytes "hello"))
    (loop []
      (when-let [data (async/<!! in)]
        (let [str (apply str (map char data))]
          (log/info "got a message from world" str))
        (recur)))))
