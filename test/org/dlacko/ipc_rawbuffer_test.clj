(ns org.dlacko.ipc-rawbuffer-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :as async]
            [org.dlacko.async-ipc :as ipc]))

(def id "test")

(defn ->bytes
  [str]
  (bytes (byte-array (map (comp byte int) str))))

(defn ->str
  [data]
  (apply str (map char data)))

(deftest test-server-in-out
  (let [server      (ipc/serve id :rawBuffer true)
        client-sock (ipc/connect-to id :rawBuffer true)
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) (->bytes "Ping"))
      (is (= "Ping" (->str (async/<!! (:in server-sock)))))

      (async/>!! (:out server-sock) (->bytes "Pong"))
      (is (= "Pong" (->str (async/<!! (:in client-sock)))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))

(deftest test-server-repeated-messages
  (let [server      (ipc/serve id :rawBuffer true)
        client-sock (ipc/connect-to id :rawBuffer true)
        server-sock (async/<!! (:connections server))]

    (try
      (async/go
        (async/>! (:out client-sock) (->bytes "Ping 1"))
        (async/>! (:out client-sock) (->bytes "Ping 2"))
        (async/>! (:out client-sock) (->bytes "Ping 3")))

      (is (= "Ping 1" (->str (async/<!! (:in server-sock)))))
      (is (= "Ping 2" (->str (async/<!! (:in server-sock)))))
      (is (= "Ping 3" (->str (async/<!! (:in server-sock)))))

      (async/go
        (async/>! (:out server-sock) (->bytes "Pong 1"))
        (async/>! (:out server-sock) (->bytes "Pong 2"))
        (async/>! (:out server-sock) (->bytes "Pong 3")))

      (is (= "Pong 1" (->str (async/<!! (:in client-sock)))))
      (is (= "Pong 2" (->str (async/<!! (:in client-sock)))))
      (is (= "Pong 3" (->str (async/<!! (:in client-sock)))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))

(deftest test-raw-in-out
  (let [server      (ipc/serve id :rawBuffer true)
        client-sock (ipc/connect-to id :rawBuffer true)
        server-sock (async/<!! (:connections server))]

    (try
      (async/>!! (:out client-sock) (bytes (byte-array [1 2 4 8 16 32])))
      (is (= [1 2 4 8 16 32] (seq (async/<!! (:in server-sock)))))

      (async/>!! (:out server-sock) (bytes (byte-array [32 16 8 4 2 1])))
      (is (= [32 16 8 4 2 1] (seq (async/<!! (:in client-sock)))))

      (finally
        (ipc/stop-server server)
        (ipc/disconnect client-sock)))))
