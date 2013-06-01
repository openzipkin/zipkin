This page explains how to set up Zipkin on a single Mac (typically for local
testing) with Cassandra and Zookeeper, the most common configuration.

Scala 2.9.1 or later is required.

[Install Homebrew](http://mxcl.github.io/homebrew/) if you haven't already. It
will make your life easier. Then run the following commands to install
Zipkin's dependencies (you can skip dependencies you already have installed):

    # Cassandra is currently required, though it should be possible to replace it.
    brew install cassandra
    # Start Cassandra on login
    ln -sfv /opt/twitter/opt/cassandra/*.plist ~/Library/LaunchAgents
    launchctl load ~/Library/LaunchAgents/homebrew.mxcl.cassandra.plist
    # Scala Build Tool
    brew install sbt
    # VCS
    brew install git

These dependencies are explained in more detail in
[install.md](https://github.com/twitter/zipkin/blob/master/doc/install.md).

Now we can install Zipkin itself:

    # WORKSPACE is wherever you want your Zipkin folder
    cd WORKSPACE
    git clone https://github.com/twitter/zipkin.git
    cd zipkin
    # Install the Zipkin schema
    cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt

Now you can run Zipkin (you'll need to leave these processes running, so use
separate bash windows if you're doing it that way):

    # Collect data
    bin/collector
    # Extract data
    bin/query
    # Display data
    bin/web

Zipkin should now be running and you can access the UI at http://localhost:8080/

The next step is to collect trace data to view in Zipkin. To do this, interface
with the collector (e.g. by using Scribe) to record trace data. There are
several libraries to make this easier to do in different environments. Twitter
uses [Finagle](https://github.com/twitter/finagle/tree/master/finagle-zipkin);
external libraries (currently for Python, REST, node, and Java) are listed in the
[wiki](https://github.com/twitter/zipkin/wiki#external-projects-that-use-zipkin);
and there is also a [Ruby gem](https://rubygems.org/gems/finagle-thrift) and
[Ruby Thrift client](https://github.com/twitter/thrift_client). Additional
information is available in
[install.md](https://github.com/twitter/zipkin/blob/master/doc/install.md).
