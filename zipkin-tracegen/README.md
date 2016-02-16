# Testing
This module is for non unit tests.

So far there's just a very basic, and somewhat hacky, program that generates
fake traces and sends those off to the server and reads them back.

There is also a SQL dump of test aggregate dependency data, which can be used if you
want to test the aggregate dependency graph feature in the Web UI without running the
zipkin-dependency Hadoop job first. In order to use it, start up Zipkin
configured with a SQL database backend, and dump the data into that database.
The aggregate dependency graph should then show up in the Zipkin Web UI.
