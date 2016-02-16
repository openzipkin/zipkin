[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin/_latestVersion)

![Zipkin (doc/zipkin-logo-200x119.jpg)](https://github.com/openzipkin/zipkin/raw/master/doc/zipkin-logo-200x119.jpg)

[Zipkin](http://twitter.github.com/zipkin) is a distributed tracing system. It helps gather timing data needed to troubleshoot latency problems in microservice architectures. The front end is a "waterfall" style graph of service calls showing call durations as horizontal bars:

![Screenshot](https://github.com/openzipkin/zipkin/raw/master/doc/web-screenshot.png)

## Running Zipkin

Zipkin minimally needs a datastore, query and UI server. Some architectures also include
a collection tier (ex polling Kafka).

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
# start the query server in a new terminal session or tab
$ ./bin/query
# start the collector server in a new terminal session or tab
$ ./bin/collector
# start the web server in a new terminal session or tab
$ ./bin/web
# create dummy traces
$ ./bin/tracegen
# open the ui and look at them!
$ open http://localhost:8080/
```

## Different Tracers available

| Language | Library | Framework | Transports Supported | Sampling Supported? | Other notes |
|:---------|:--------|:----------|:---------------------|:--------------------|:------------|
| Python | [pyramid_zipkin](https://github.com/Yelp/pyramid_zipkin) | [Pyramid](http://docs.pylonsproject.org/projects/pyramid/en/latest/) |[Kafka \| Scribe](http://pyramid-zipkin.readthedocs.org/en/latest/configuring_zipkin.html#zipkin-transport-handler) | [Yes](http://pyramid-zipkin.readthedocs.org/en/latest/configuring_zipkin.html#zipkin-tracing-percent) | py2, py3 support. |
| Java | [brave](https://github.com/openzipkin/brave) | Jersey, RestEASY, JAXRS2, Apache HttpClient, Mysql | Http, Kafka, Scribe | Yes | Java 7 or higher|
| Ruby | [zipkin-tracer](https://github.com/openzipkin/zipkin-tracer) | [Rack](http://rack.github.io/) | Http, Kafka, Scribe | Yes | lc support. Ruby 2.0 or higher|
| C# | [ZipkinTracerModule](https://github.com/mdsol/Medidata.ZipkinTracerModule) | OWIN, HttpHandler | Http | Yes | lc support. 4.5.2 or higher |

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

## Build
If you can't use a [release](https://jcenter.bintray.com/io/zipkin) or [snapshot build](http://oss.jfrog.org/artifactory/oss-snapshot-local/io/zipkin/), you can build zipkin's service jars directly from your fork using gradle.

```bash
$ git clone https://github.com/YOUR_USER/zipkin.git
$ cd zipkin/
$ ./gradlew shadowJar
$ ls */build/libs/*all.jar
```

See zipkin-web/README.md if you experience issues from npm.

## IntelliJ IDEA

The most reliable way to import zipkin is to use the Gradle command-line and open the resulting project into Intellij.

*Note* Do not import as a Gradle project. If you do, you'll likely see classpath or other build related problems. That's why we use the command-line.

To build the project file, close any open window for zipkin and (re)generate the content like so:

```bash
$ ./gradlew cleanIdea idea
```

Import the the result via File, Open, (path you invoked gradlew). Dismiss any pop-ups about unlinked Gradle projects, as clicking those will likely break your project.

## Contributing

See [CONTRIBUTING.md](https://github.com/openzipkin/zipkin/blob/master/CONTRIBUTING.md) for guidelines.

Areas where we'd love to see contributions:

* adding tracing to more libraries and protocols
* interesting reports generated with Hadoop from the trace data
* extending collection to more transports and storage systems
* trace data visualizations in the web UI
