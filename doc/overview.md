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

## Mailing lists
There are two mailing lists you can use to get in touch with other users and developers.

Users: [https://groups.google.com/group/zipkin-user](https://groups.google.com/group/zipkin-user)

Developers: [https://groups.google.com/group/zipkin-dev](https://groups.google.com/group/zipkin-dev)

## Issues
Noticed a bug? [https://github.com/twitter/zipkin/issues](https://github.com/twitter/zipkin/issues)

## Contributions
Contributions are very welcome! Please create a pull request on github and we'll look at it as soon as possible.

Try to make the code in the pull request as focused and clean as possible, stick as close to our code style as you can.

If the pull request is becoming too big we ask that you split it into smaller ones.

Areas where we'd love to see contributions include: adding tracing to more libraries and protocols, interesting reports generated with Hadoop from the trace data, extending collector to support more transports and storage systems and other ways of visualizing the data in the web UI.

## Versioning
We intend to use the [Semantic Versioning](http://semver.org/) style versioning.

## Authors
Thanks to everyone below for making Zipkin happen!

Zipkin server
* Johan Oskarsson: [@skr](https://twitter.com/skr)
* Franklin Hu: [@thisisfranklin](https://twitter.com/thisisfranklin)
* Ian Ownbey: [@iano](https://twitter.com/iano)

Zipkin UI
* Franklin Hu: [@thisisfranklin](https://twitter.com/thisisfranklin)
* Bill Couch: [@couch](https://twitter.com/couch)
* David McLaughlin: [@dmcg](https://twitter.com/dmcg)
* Chad Rosen: [@zed](https://twitter.com/zed)

Instrumentation
* Marius Eriksen: [@marius](https://twitter.com/marius)
* Arya Asemanfar: [@a_a](https://twitter.com/a_a)

## License
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0: [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
