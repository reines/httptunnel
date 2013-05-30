httptunnel
==========

A HTTP Tunnel for Netty - allows tunnelling of TCP traffic over the HTTP protocol.

![Build Status](https://api.travis-ci.org/reines/httptunnel.png)

Works by opening 2 HTTP from the client to server, one for pushing data as it becomes available, and another for long-polling. Can be dropped in to Netty 3.4.x and using transparently as a transport.

![Sequence diagram](https://raw.github.com/reines/httptunnel/master/docs/diagrams/tunnel_sequence.png)
