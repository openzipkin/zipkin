.. image:: _static/logo.jpg
   :class: logo

Zipkin is a distributed tracing system that helps us gather timing data for all the disparate services at Twitter. It manages both the collection and lookup of this data through a Collector and a Query service. We closely modelled Zipkin after the `Google Dapper`_ paper. Follow `@ZipkinProject`_ for updates.

Collecting traces helps developers gain deeper knowledge about how certain requests perform in a distributed system. Let's say we're having problems with user requests timing out. We can look up traced requests that timed out and display it in the web UI. We'll be able to quickly find the service responsible for adding the unexpected response time. If the service has been annotated adequately we can also find out where in that service the issue is happening.

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
