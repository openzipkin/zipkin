Zipkin Interop
===================

The code in this repository aims to replace the legacy implementation of zipkin, written 
in scala 2.11 + finagle + twitter server.

https://github.com/openzipkin/zipkin/issues/1071

This module runs the legacy integration tests against storage to ensure compatibility
isn't compromised while in transition. This is a safety measure only: this repository's
integration tests are a superset of the legacy ones.

