# clj-ipc

A node-ipc compatible Clojure library for asynchronous inter-process communication. It was forked
from `bguthrie/async-sockets` and was modified to be able to handle Unix Domain Sockets and to be
compatible with `node-ipc`. Although, the scope of the project is to handle all kind of inter-process
communication, it currently handles Unix Domain Sockets only. It would be easy to add TCP/UDP
support as well. Using Windows Sockets should be also possible. If you need any of these, please send me a message.

## Releases

`clj-ipc` is work in progress. Will be released to [clojars.org](https://clojars.org) as soon 
as it is in production ready state.

## Usage

This library permits the creation of IPC connections in idiomatic Clojure. It uses
`clojure.core.async` channels for receiving and sending socket data but otherwise leans heavily on `java.net.Socket`
and `java.net.ServerSocket` for most of the heavy lifting. It depends on `junixsocket` for
using Unix Domain Sockets. The library uses JSON for message passing.

The library currenty supports Unix Domain Socket connections only. Servers and clients are created by a string id,
which is further extended into a file path using the `appspace` and `socketRoot` fields of the `config` map (`path = socketRoot + appspace + "." id`). The `create-server` function returns a record with a channel named `:connections`, which yields one `AsyncSocket` per incoming client connection. Clients are created with
`socket-client` function. Each socket, either
created explicitly using this function or yielded by the `:connections` fields of an `AsyncSocketServer`, exposes two
channels, an `:in` channel for receiving messages and an `:out` channel for sending messages. The raw `java.net.Socket`
object is also available as `:socket`, should you need it.

```clojure
(ns user
  (:require [async-sockets.core :refer :all]
            [clojure.core.async :as async]))

(let [socket (socket-client "world")]
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
    (when-let [msg (async/<! (:in socket))]
      (async/>! (:out socket) {:echo msg})
      (recur))))

(let [server (socket-server "world")]
  (async/go-loop []
    (when-let [connection (async/<! (:connections server))]
      (echo-everything connection)
      (recur))))
```

When the underlying socket closes, each channel will close automatically, both for outgoing (client) and incoming
(server) sockets.

## node-ipc compatibility

`clj-ipc` is compatible with `node-ipc` at a low level, so the individual messages are separated properly.
However, when you send with `node-ipc` using `emit`:

```js
ipc.of.world.emit(
  "message", //any event or message type your server listens for
  "hello"
);
```

`node-ipc` send a message like this:

```clojure
{ :type "message"
  :data "hello" }
```

Similarly, when `on` is used:

```js
ipc.of.world.on("message", function(data) {...})
```

`node-ipc` expect a message in the same format (a map with key `:type` and `:data` where `:type = "message"`). This is not handled by this library. Wrap/unwrap your messages if you work with `node-ipc`.
