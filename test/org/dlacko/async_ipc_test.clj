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

(defn receive-until-secs-elapsed [limit out-chan socket]
  (let [start-time (time/now)]
    (async/go-loop [n 0]
      (let [secs-elapsed (time/in-seconds (time/interval start-time (time/now)))
            msg (async/<! (:in socket))]
        (if (or (nil? msg) (= secs-elapsed limit))
          (do (ipc/disconnect socket) (async/>! out-chan n))
          (recur (inc n)))))))

(defn send-indefinitely [socket id]
  (async/go-loop [n 0]
    (when (= 0 (mod n 100000)) (println "Sent" n "messages on socket" id))
    (async/>! (:out socket) (str "message " n))
    (recur (inc n))))

(defn perftest-sockets [socket-count secs-limit]
  (let [server        (ipc/serve id)
        client-socks  (map ipc/connect-to (range socket-count))
        server-socks  (map (fn [_] (async/<!! (:connections server))) (range socket-count))
        limit-minutes (/ 60 secs-limit)
        out-chan      (async/chan socket-count)]

    (try

      (doall
       (map-indexed
        (fn [idx sock] (send-indefinitely sock idx))
        client-socks))

      (doall
       (map (partial receive-until-secs-elapsed secs-limit out-chan) server-socks))

      (loop [n 0]
        (when (< n socket-count)
          (let [msgs-received (async/<!! out-chan)
                msgs-per-minute (* msgs-received limit-minutes)]
            (println (format "Socket %d received %d msgs in %d seconds (%f msgs/minute)" n msgs-received secs-limit (float msgs-per-minute)))
            (recur (inc n)))))

      (finally
        (ipc/stop-server server)
        (doall (map ipc/disconnect client-socks))))))
