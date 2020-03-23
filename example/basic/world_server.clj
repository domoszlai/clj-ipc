(ns basic.world-server
  (:require [clojure.core.async :as async]
            [org.dlacko.async-ipc :as ipc]))

;; Compatible with https://github.com/RIAEvangelist/node-ipc/blob/master/example/unixWindowsSocket/basic/hello-client.js

(defn -main []
  (let [server (ipc/serve "world")]
    (loop []
      (if-let [conn (async/<!! (:connections server))]
        (do
          (async/go-loop []
            (if-let [{:keys [data]} (async/<! (:in conn))]
              (async/>! (:out conn) {:type "app.message"
                                     :data {:id "world"
                                            :message (str (:message data) " world!")}})))
          (recur))))))
