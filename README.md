# clj-ipc

A node-ipc compatible Clojure library for asynchronous inter-process communication.

## Releases

`clj-ipc` is published to [clojars.org](https://clojars.org). The latest stable release is `0.1.0`.

[Leiningen](http://leiningen.org) dependency information:

```
[org.dlacko/clj-ipc "0.1.0"]
```

## Usage

This library permits the creation of socket servers and remote socket clients in idiomatic Clojure. It uses
`clojure.core.async`  channels for receiving and sending socket data but otherwise leans heavily on `java.net.Socket`
and `java.net.ServerSocket` for most of the heavy lifting. At the moment, it uses a line-based idiom for all socket
interactions; remote data is received and sent a line at a time, and flushed on write. Pull requests gratefully
accepted.

Servers are created with `(socket-server <port> [<backlog>] [<bind-addr>])`. The latter two arguments are both optional
and behave as specified by `java.net.SocketServer`. The function returns a record with a channel named `:connections`, 
which yields one `AsyncSocket` per incoming client connection.

Clients are created with `(socket-client <port> [<address>])`; `address` is `localhost` by default. Each socket, either
created explicitly using this function or yielded by the `:connections` fields of an `AsyncSocketServer`, exposes two
channels, an `:in` channel for receiving messages and an `:out` channel for sending messages. The raw `java.net.Socket` 
object is also available as `:socket`, should you need it.

To connect and send a message to a remote server `example.com`:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [clojure.core.async :as async]))

(let [socket (socket-client "example.com" 12345)]
  (async/>!! (:out socket) "Hello, World")
  (close-socket-client socket))
```

To start an asynchronous socket server, which in this case echoes every input received:

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [clojure.core.async :as async]))
   
(defn echo-everything [socket]
  (async/go-loop []
    (when-let [line (async/<! (:in socket))]
      (async/>! (:out socket) (str "ECHO: " line))
      (recur))))
   
(let [server (socket-server 12345)]
  (async/go-loop []
    (when-let [connection (async/<! (:connections server))] 
      (echo-everything connection)
      (recur))))
```

When the underlying socket closes, each channel will close automatically, both for outgoing (client) and incoming
(server) sockets.
