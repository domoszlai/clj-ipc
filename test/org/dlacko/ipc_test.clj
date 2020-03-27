(ns org.dlacko.ipc-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :as async]
            [org.dlacko.async-ipc :as ipc]))

(def id "test")

(deftest test-echo-server
  (let [server      (ipc/serve id)
        client-sock (ipc/connect-to id)
        server-sock (async/<!! (:connections server))]

    (try
      (async/go-loop []
        (when-let [input (async/<! (:in server-sock))]
          (async/>! (:out server-sock) (str "ECHO: " input))
          (recur)))

      (async/>!! (:out client-sock) "Hello, I'm Guybrush Threepwood")
      (is (= "ECHO: Hello, I'm Guybrush Threepwood" (async/<!! (:in client-sock))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))

(deftest test-many-clients
  (let [connections 50
        done-ch (async/chan 1)
        tasks-to-done (atom connections)
        server (ipc/serve id)]

    (try
      (async/go-loop []
        (when-let [server-sock (async/<! (:connections server))]
          (async/go
            (when-let [input (async/<! (:in server-sock))]
              (async/>! (:out server-sock) (str "ECHO: " input))))
          (recur)))

      (doseq [i (range connections)]
        (async/go
          (let [client-sock (ipc/connect-to id)]
            (async/>! (:out client-sock) (str "client-" (inc i)))
            (let [_ (async/<! (:in client-sock))]
              (ipc/disconnect client-sock)
              (swap! tasks-to-done dec)
              (when (= 0 @tasks-to-done)
                (async/>! done-ch :done))))))

      (async/<!! done-ch)

      (finally
        (ipc/stop-server server)))))
