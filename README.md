![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/twitter/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

[Zipkin](http://twitter.github.com/zipkin) is a distributed tracing system that helps us gather timing data for all the disparate services at Twitter.

## Full documentation
See [http://twitter.github.com/zipkin](http://twitter.github.com/zipkin)

## Quick start
You'll need Scala 2.9.1

Clone the repo, `git clone git://github.com/twitter/zipkin`, or [download a release](https://github.com/twitter/zipkin/downloads)

To run a collector daemon: `bin/collector`

To run a query daemon: `bin/query`

To run a UI daemon: `bin/web`

For a more in-depth installation guide, see: [http://twitter.github.com/zipkin/install.html](http://twitter.github.com/zipkin/install.html)

## Get involved

Check out the #zipkin IRC channel on chat.freenode.com to see if any
developers are there for questions or live debugging tips. Otherwise,
there are two mailing lists you can use to get in touch with other
users and developers.

Users: [https://groups.google.com/group/zipkin-user](https://groups.google.com/group/zipkin-user)

Developers: [https://groups.google.com/group/zipkin-dev](https://groups.google.com/group/zipkin-dev)

## Issues
Noticed a bug? [https://github.com/twitter/zipkin/issues](https://github.com/twitter/zipkin/issues)

## Contributing
See [CONTRIBUTING.md](https://github.com/twitter/zipkin/blob/master/CONTRIBUTING.md) for guidelines.

Areas where we'd love to see contributions: 

* adding tracing to more libraries and protocols
* interesting reports generated with Hadoop from the trace data
* extending collector to support more transports and storage systems
* trace data visualizations in the web UI

## Versioning
We use [SemVer](http://semver.org/) style versioning.

## Authors
Thanks to everyone below for making Zipkin happen!

Zipkin server

* Johan Oskarsson: [@skr](https://twitter.com/skr)
* Franklin Hu: [@thisisfranklin](https://twitter.com/thisisfranklin)
* Ian Ownbey: [@iano](https://twitter.com/iano)
* Brian Degenhardt [@bmdhacks](https://twitter.com/bmdhacks)

Zipkin UI

* Franklin Hu: [@thisisfranklin](https://twitter.com/thisisfranklin)
* Bill Couch: [@couch](https://twitter.com/couch)
* David McLaughlin: [@dmcg](https://twitter.com/dmcg)
* Chad Rosen: [@zed](https://twitter.com/zed)
* Brian Degenhardt [@bmdhacks](https://twitter.com/bmdhacks)

Instrumentation

* Marius Eriksen: [@marius](https://twitter.com/marius)
* Arya Asemanfar: [@a_a](https://twitter.com/a_a)
* Brian Degenhardt [@bmdhacks](https://twitter.com/bmdhacks)

## License
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0: [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

