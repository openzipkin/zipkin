[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin/_latestVersion)

![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/openzipkin/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

[Zipkin](http://twitter.github.com/zipkin) is a distributed tracing system. It is used by Twitter to help gather timing data for all their disparate services. The front end is a "waterfall" style graph of service calls showing call durations as horizontal bars:

![Screenshot](https://github.com/openzipkin/zipkin/raw/master/doc/web-screenshot.png)

## Running Zipkin

Zipkin is a collection of processes (a backend for the data, a
"collector", and query engine, and a web UI) and all of them need to
be running to make any progress:

![Architecture](https://github.com/openzipkin/zipkin/raw/master/doc/architecture-0.png)

If you are familiar with Docker, the
quickest way to get started quickly is to use the
[Docker Zipkin](https://github.com/openzipkin/docker-zipkin) project,
which (in addition to being able to build docker images) provides
scripts and a
[`docker-compose.yml`](https://github.com/openzipkin/docker-zipkin/blob/master/docker-compose.yml)
for launching pre-built images, e.g.

```
$ git clone https://github.com/openzipkin/docker-zipkin
$ cd docker-zipkin
$ docker-compose up
```

If you are happy building from source you can use the scripts in the
[`bin`](bin) directory of this repository.

Here's how to start zipkin using the default file-based backend and view traces.
```bash
# get the zipkin source and change to its directory
$ git clone https://github.com/openzipkin/zipkin; cd zipkin
# start the collector server in a new terminal session or tab
$ ./bin/collector
# start the query server in a new terminal session or tab
$ ./bin/query
# start the web server in a new terminal session or tab
$ ./bin/web
# create dummy traces
$ ./bin/tracegen
# open the ui and look at them!
$ open http://localhost:8080/
```

## Full documentation

See [http://twitter.github.com/zipkin](http://twitter.github.com/zipkin)

## Get involved

Join the [openzipkin/zipkin gitter chat](https://gitter.im/openzipkin/zipkin)
for questions and to talk with the developers. Otherwise, there are two mailing
lists you can use to get in touch with other users and developers.

Users: [https://groups.google.com/group/zipkin-user](https://groups.google.com/group/zipkin-user)

Developers: [https://groups.google.com/group/zipkin-dev](https://groups.google.com/group/zipkin-dev)

## Issues

Noticed a bug? [Please file an issue](https://github.com/openzipkin/zipkin/issues)

## Contributing

See [CONTRIBUTING.md](https://github.com/openzipkin/zipkin/blob/master/CONTRIBUTING.md) for guidelines.

Areas where we'd love to see contributions:

* adding tracing to more libraries and protocols
* interesting reports generated with Hadoop from the trace data
* extending collector to support more transports and storage systems
* trace data visualizations in the web UI
