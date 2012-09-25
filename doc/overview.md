![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/twitter/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

Zipkin is a distributed tracing system that helps us gather timing data for all the disparate services at Twitter.
It manages both the collection and lookup of this data through a Collector and a Query service.
We closely modelled Zipkin after the [Google Dapper](http://research.google.com/pubs/pub36356.html) paper. Follow [@ZipkinProject](https://twitter.com/zipkinproject) for updates. [![Build Status](https://secure.travis-ci.org/twitter/zipkin.png)](http://travis-ci.org/twitter/zipkin)

## Why distributed tracing?
Collecting traces helps developers gain deeper knowledge about how certain requests perform in a distributed system.
Let's say we're having problems with user requests timing out. We can look up traced requests that timed out and display
it in the web UI. We'll be able to quickly find the service responsible for adding the unexpected response time.
If the service has been annotated adequately we can also find out where in that service the issue is happening.

![Screnshot of the Zipkin web UI (doc/web-screenshot.png)](https://github.com/twitter/zipkin/raw/master/doc/web-screenshot.png)
