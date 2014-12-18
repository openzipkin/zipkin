Span Recievers
==============

A `SpanReciever` is responsible for collecting spans from services, converting
them to a Zipkin common Span, then passing them to the storage layer. This
approach provides modularity allowing for receivers that accept any sort of data
from any sort of producer. Zipkin comes with a receiver for Scribe and one for
Kafka.

Scribe Reciever
---------------

Scribe_ is the logging framework we use at Twitter to transport trace data. Thus
the scribe span receiver is the most well supported.

For small architectures tracers can be setup to send directly to the Zipkin
collectors. The ScribeSpanReceiver expects a scribe log entry with a Base64
encoded, binary serialized thrift Span using the "zipkin" category (this
category is configurable via command line flag). Finagle-zipkin does this
automatically.

As the architecture scales out it may become infeasible to connect all services
directly to the collectors. At Twitter we use a `modified scribe daemon`_ that
run on every host. The daemons connect to ZooKeeper and watch for a list of
hosts to forward messages to.

To use Scribe with Zipkin, you need to set up a network store that points to
the Zipkin collector daemon. A Scribe store for Zipkin might look something
like this:

::

    <store>
      category=zipkin
      type=network
      remote_host=123.123.123.123
      remote_port=9410
      use_conn_pool=yes
      default_max_msg_before_reconnect=50000
      allowable_delta_before_reconnect=12500
      must_succeed=no
    </store>

The ScribeSpanReceiver will register itself to a configurable ZooKeeper host and
advertise on a configurable path (see the flags for details)

.. _Scribe: https://github.com/facebook/scribe
.. _modified scribe daemon: https://github.com/traviscrawford/scribe
