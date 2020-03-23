(defproject org.dlacko/clj-ipc "0.0.1-SNAPSHOT"
  :description "A node-ipc compatible Clojure library for working with Unix Domain Sockets using core.async channels."
  :url "https://github.com/domoszlai/clj-ipc"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.0.567"]
                 [org.clojure/tools.logging "1.0.0"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]
                 [cheshire "5.10.0"]])
