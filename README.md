![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/twitter/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

[Zipkin](http://twitter.github.com/zipkin) is a distributed tracing system that helps us gather timing data for all the disparate services at Twitter.

## Quick start
You'll need Scala 2.9.1

Clone the repo, `git clone git://github.com/twitter/zipkin`, or [download a release](https://github.com/twitter/zipkin/downloads)

To run a collector daemon: `bin/sbt 'project zipkin-scribe' 'run -f zipkin-scribe/config/collector-dev.scala'`
To run a query daemon: `bin/sbt 'project zipkin-server' 'run -f zipkin-server/config/query-dev.scala'`
To run a UI daemon: `bin/sbt 'project zipkin-finatra' 'run -f zipkin-finatra/config/web-dev.scala'`

