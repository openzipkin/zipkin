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
    # Python is required for pip, which is required to get CQL
    brew install python
    pip install cql
    # Zookeeper helps Zipkin sample data
    brew install zookeeper
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
    # TODO are these next 3 lines necessary?
    cp zipkin-collector-service/config/collector-dev.scala zipkin-collector-service/config/collector-prod.scala
    cp zipkin-query-service/config/query-dev.scala zipkin-query-service/config/query-prod.scala
    bin/sbt update package-dist
    # Install the Zipkin schema
    cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt
    # TODO not sure this is necessary either
    sudo mkdir -p /var/log/zipkin

Zipkin is installed now. To run it (you'll need to leave these processes
running, so use separate bash windows if you're doing it that way):

    # Collect data
    bin/collector
    # Extract data
    bin/query
    # Display data
    bin/web

Zipkin should now be running and you can access the UI at http://localhost:8080/

The next step is to have your existing services record data to the collector.
If you are testing or developing Zipkin locally and you already have data being
recorded in production, you can use SSH port forwarding to run a remote query
daemon behind a local Zipkin instance. In production, Twitter records data by
connecting Scribe to the Collector daemon and writing to Scribe from its other
services as described in
[install.md](https://github.com/twitter/zipkin/blob/master/doc/install.md).