This page explains how to set up Zipkin on a single Mac (typically for local
testing) with Cassandra, the most common choice of database for use with Zipkin.
To run Zipkin out of the box without using Cassandra, see
[install.md](https://github.com/openzipkin/zipkin/blob/master/doc/install.md).

Scala 2.11.7 or later is required.

## Installing Cassandra

[Install Homebrew](http://mxcl.github.io/homebrew/) if you haven't already. It
will make your life easier. Then run the following commands to install
and set up Cassandra:

```bash
# Cassandra is the most common choice, though Zipkin supports other databases
brew install cassandra
# Start Cassandra on login
ln -sfv /opt/twitter/opt/cassandra/*.plist ~/Library/LaunchAgents
launchctl load ~/Library/LaunchAgents/homebrew.mxcl.cassandra.plist
```

## Install Zipkin

Now we can install Zipkin itself:

```bash
# WORKSPACE is wherever you want your Zipkin folder
cd WORKSPACE
# If you don't have git, `brew install git` or just download Zipkin directly
git clone https://github.com/openzipkin/zipkin.git
cd zipkin
# Install the Zipkin schema
cassandra-cli -host localhost -port 9160 -f zipkin-cassandra/src/schema/cassandra-schema.txt
```

## Run Zipkin

Now you can run Zipkin (you'll need to leave these processes running, so use
separate bash windows if you're doing it that way):

```bash
# Collect data
bin/collector cassandra
# Extract data
bin/query cassandra
# Display data
bin/web
```

Zipkin should now be running and you can access the UI at http://localhost:8080/

## Next Steps

The next step is to collect trace data to view in Zipkin. To do this, interface
with the collector (e.g. by using Scribe) to record trace data. There are
several libraries to make this easier to do in different environments. Twitter
uses [Finagle](https://github.com/twitter/finagle/tree/master/finagle-zipkin);
external libraries (currently for Python, REST, node, and Java) are listed in the
[wiki](https://github.com/openzipkin/zipkin/wiki#external-projects-that-use-zipkin);
and there is also a [Ruby gem](https://rubygems.org/gems/finagle-thrift) and
[Ruby Thrift client](https://github.com/twitter/thrift_client). Additional
information is available in
[install.md](https://github.com/openzipkin/zipkin/blob/master/doc/install.md).
