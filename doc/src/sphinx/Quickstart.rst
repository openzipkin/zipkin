Quickstart
==========

In this section we'll walk through building and starting an instance of Zipkin
and how to enable tracing on your service.

Docker
------
If you are familiar with Docker, the quickest way to get started quickly is to
use the `Docker Zipkin`_ project, which (in addition to being able to build docker
images) provides scripts and a `docker-compose.yml`_ for launching pre-built images,
e.g.

.. parsed-literal::
    $ git clone https://github.com/openzipkin/docker-zipkin
    $ cd docker-zipkin/deploy
    $ docker-compose up


Super Quickstart
----------------

The zipkin-example project combines all parts of the service into a single
package and adds a trace generator to provide random example traces.

The following command will start an instance of ZipkinExample using SQLite
in-memory as the data store and populating it with a few example traces.

.. parsed-literal::
    ./gradlew :zipkin-example:run

After it starts up open http://localhost:8080 in your browser and poke around
the example traces. They're all randomly generated so they probably wont make
much sense, but it'll provide a starting to point to navigate from.

Digging Deeper
--------------

From the example project, let's dig in a bit more to see what's available.
Zipkin uses TwitterServer_ at its core. This gives us a lot of flexibility as to
how the service is composed and configured.

Run the following to see all the available configuration flags for the example
project:

.. parsed-literal::
    ./gradlew :zipkin-example:run -PrunArgs='-help'

The list of flags include a bunch of global flags to configure Finagle but also
many to configure the different Zipkin modules we've mixed in to zipkin-example.
Of these, the most important will be `-zipkin.web.cacheResources`,
`-zipkin.storage.anormdb.install`, and `-zipkin.storage.anormdb.db`. We can set
zipkin-example up as a staging instance with persistant storage and send some
trace data to it.

**-zipkin.web.cacheResources=true** is required when running Zipkin from a jar. It
  lets the web server know that it should search the jar for static resources
  and cache them in memory.

**-zipkin.storage.anormdb.install=true** will install the necessary tables into
  the database. It's only needed once for persistent databases.

**-zipkin.storage.anormdb.db=...** will tell zipkin-anormdb which SQL database to
  use.

Packaging for Production
------------------------

First things first: if you don't need to recompile Zipkin, you can use the
fat jars distributed to `Bintray`_ or `Maven Central`_. The only (pretty big) use-case for
compiling Zipkin yourself is customizing the services (see below). This setup is
sub-optimal, and is being tracked in Zipkin issue `#466`_.

You can generate a production-ready fat jar for each stand-alone service with

.. parsed-literal::
    ./gradlew shadowJar -x test

Customizing the service
-----------------------

Zipkin uses the cake pattern to compose a set of modules into a service. Taking
a look at the Main.scala file in zipkin-example we see a Main object with a set
of Zipkin factories mixed into it. Each factory contains a factory specific set
of flags for configuration. They also contain the methods necessary to build
their functionality so it can be composed with other modules. The reasons for
using the cake pattern and manually composing modules is beyond the scope of
this quickstart.

Given this information, we can split the monolithic process into individual
pieces and tune them independently. We can also mix in a different storage
backend or a different receiver frontend.

Here we'll create a separate main for the web and collector services. We'll also
trade out the AnormDB backend for Cassandra.

First provide the imports

.. includecode:: code/quickstart/WebAndCollector.scala#imports

Next we'll build our web main:

.. includecode:: code/quickstart/WebAndCollector.scala#web_main

We start by extending TwitterServer then mix in the web and cassie factories.
ZipkinWebFactory defines a method `newQueryClient` that it uses to create a
query client used to request trace information. By default it will create a
thrift client that will connect to a query service. However, we're going to
connect directly to Cassandra. We'll override `newQueryClient` to create a new
`ThriftQueryService` with a cassie span store. TwitterServer requires us to
define `main` and `Await` on our service.

Finally our collector:

.. includecode:: code/quickstart/WebAndCollector.scala#collector_main

Here again we extend TwitterServer and mix in our required factories. We'll use
the ScribeSpanReceiver so we can receive spans sent from finagle-zipkin.

Other than the converter, this is pretty straight forward. The converter is
there because SpanReceiver requires a function that takes `Seq[scribethrift.Span]`
but SpanStore requires `Seq[Span]`.

.. _TwitterServer: http://twitter.github.io/twitter-server/
.. _Docker Zipkin: https://github.com/openzipkin/docker-zipkin
.. _docker-compose.yml: https://github.com/openzipkin/docker-zipkin/blob/master/deploy/docker-compose.yml
.. _Bintray: https://bintray.com/openzipkin/zipkin/zipkin
.. _Maven Central: http://search.maven.org/#search|ga|1|g%3A%22io.zipkin%22%20AND%20l%3A%22all%22
.. _#466: https://github.com/openzipkin/zipkin/issues/466
