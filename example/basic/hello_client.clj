(ns basic.hello-client
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [org.dlacko.async-ipc :as ipc]))

;; Compatible with https://github.com/RIAEvangelist/node-ipc/blob/master/example/unixWindowsSocket/basic/world-server.js

(defn -main []
  (let [{:keys [out in]} (ipc/connect-to "world")]
    (async/>!! out {:type "app.message"
                    :data {:id "hello"
                           :message "hello"}})
    (let [{:keys [data]} (async/<!! in)]
      (log/info "got a message from world" data))))
