# Rational for collector-activemq

## Diverse need
ActiveMQ was formerly requested in April, 2018 through issue #1990 which had two other thumbs-up. An
early draft of this implementation was developed by @IAMTJW and resulting in another user asking for
it. In June of 2019 there were a couple more requests for this on gitter, notably about Amazon MQ.

## On ActiveMQ 5.x
All users who expressed interest were interestd in ActiveMQ 5.x (aka Classic), not Artemis.
Moreover, at the time of creation Amazon MQ only supported ActiveMQ 5.x.

Artemis has higher throughput potential, but has more conflicting dependencies and would add 8MiB to
the server. Moreover, no-one has asked for it.

## On part of the default server
ActiveMQ's client is 2MiB, which will increase the jar size, something that we've been tracking
recently. To be fair, this is not a large module. In comparison, one dependency of Kafka, `zstd-jni`
alone is almost 4MiB. There are no dependencies likely to conflict at runtime, and only one dodgy
dependency, [hawtbuf](https://github.com/fusesource/hawtbuf), on account of it being abandoned since
2014.

Apart from size, ActiveMQ is a stable integration, included in Spring Boot, and could be useful for
other integrations as an in-memory queue. Moreover, bundling makes integration with zipkin-aws far
easier in the same way as bundling elasticsearch does.

## On a potential single-transport client

This package is using the normal activemq-jms client. During a [mail thread](http://activemq.2283324.n4.nabble.com/Interest-in-using-ActiveMQ-as-a-trace-data-transport-for-Zipkin-td4749755.html), we learned the
the STOMP and AMQP 1.0 protocol are the more portable options for a portable integration as
ActiveMQ, Artemis and RabbitMQ all support these. On the other hand Kafka does not support these
protocols. Any future portability work could be limited by this. Meanwhile, using the standard JMS
client will make troubleshooting most natural to end users.
