# clj-ipc

A [node-ipc](https://www.npmjs.com/package/node-ipc) compatible Clojure library for asynchronous inter-process communication.

## State

`clj-ipc` is work in progress. Its main limitations at the moment:

- Although, the scope of the project is to handle all kind of inter-process
  communication, it currently works with Unix Domain Sockets only.
- It uses blocking Socket API calls by `junixsocket`, thus it needs to create
  many threads to be able to provide an async API. Use the library accordingly.

## Releases

It is released to [clojars.org](https://clojars.org).

Dependency information:

[![Clojars Project](https://img.shields.io/clojars/v/org.dlacko/clj-ipc.svg)](https://clojars.org/org.dlacko/clj-ipc)

## Usage

The `example` directory contains some examples of basic usage.
