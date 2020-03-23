# clj-ipc

A `node-ipc` compatible Clojure library for asynchronous inter-process communication.

## State

`clj-ipc` is work in progress. Its main limitations at the moment:
* Although, the scope of the project is to handle all kind of inter-process
communication, it currently works with Unix Domain Sockets only.
* It uses blocking Socket API calls by `junixsocket`, thus it needs to create
many threads to be able to provide an async API. It is not adviced to use this library if you have more
than a handful of connections to deal with.

## Releases

It is released to [clojars.org](https://clojars.org) for testing purposes.

Dependency information:

https://clojars.org/org.dlacko/clj-ipc

## Usage

The `example` directory contains some examples of basic usage.
