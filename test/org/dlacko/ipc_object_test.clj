(ns org.dlacko.ipc-object-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :as async]
            [org.dlacko.async-ipc :as ipc]))

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

(deftest test-object-in-out
  (let [server      (ipc/serve id)
        client-sock (ipc/connect-to id)
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) {:ping true
                                     :pong false})
      (is (= {:ping true
              :pong false} (async/<!! (:in server-sock))))

      (async/>!! (:out server-sock) {:ping false
                                     :pong true})
      (is (= {:ping false
              :pong true} (async/<!! (:in client-sock))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))
