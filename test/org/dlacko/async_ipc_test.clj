(ns org.dlacko.async-ipc-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :as async]
            [org.dlacko.async-ipc :as ipc]
            [clj-time.core :as time]))

(def id "test")

(deftest test-server-in-out
  (let [server      (ipc/serve id)
        client-sock (ipc/connect-to id)
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) "Ping")
      (is (= "Ping" (async/<!! (:in server-sock))))

      (async/>!! (:out server-sock) "Pong")
      (is (= "Pong" (async/<!! (:in client-sock))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))

(deftest test-server-repeated-messages
  (let [server      (ipc/serve id)
        client-sock (ipc/connect-to id)
        server-sock (async/<!! (:connections server))]

    (try
      (async/go
        (async/>! (:out client-sock) "Ping 1")
        (async/>! (:out client-sock) "Ping 2")
        (async/>! (:out client-sock) "Ping 3"))

      (is (= "Ping 1" (async/<!! (:in server-sock))))
      (is (= "Ping 2" (async/<!! (:in server-sock))))
      (is (= "Ping 3" (async/<!! (:in server-sock))))

      (async/go
        (async/>! (:out server-sock) "Pong 1")
        (async/>! (:out server-sock) "Pong 2")
        (async/>! (:out server-sock) "Pong 3"))

      (is (= "Pong 1" (async/<!! (:in client-sock))))
      (is (= "Pong 2" (async/<!! (:in client-sock))))
      (is (= "Pong 3" (async/<!! (:in client-sock))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))

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
