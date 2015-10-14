.. image:: _static/logo.jpg
   :class: logo

Zipkin is a distributed tracing system. It helps gather timing data needed to troubleshoot latency problems in microservice architectures. It manages both the collection and lookup of this data through a Collector and a Query service. Zipkin's design is based on the `Google Dapper`_ paper.

Collecting traces helps developers gain deeper knowledge about how certain requests perform in a distributed system. Let's say we're having problems with user requests timing out. We can look up traced requests that timed out and display it in the web UI. We'll be able to quickly find the service responsible for adding the unexpected response time. If the service has been annotated adequately we can also find out where in that service the issue is happening.

Follow `@ZipkinProject`_ for updates.

.. _Google Dapper: http://research.google.com/pubs/pub36356.html
.. _@ZipkinProject: http://twitter.com/ZipkinProject

User's guide
------------

.. toctree::
   :maxdepth: 1

   Quickstart
   Instrumenting
   SpanReceivers
   Architecture
