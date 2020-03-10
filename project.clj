(defproject org.dlacko/async-unix-sockets "0.1.0"
  :description "A node-ipc compaitble Clojure library for working with unix domain sockets using core.async channels."
  :url "https://github.com/domoszlai/async-unix-sockets"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.3.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [clj-time "0.8.0"]]
                   :global-vars {*warn-on-reflection* true}}})
