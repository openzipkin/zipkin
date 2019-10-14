# zipkin-server rationale

## Custom Servers not supported
TODO: list all the reasons why this has caused us pain. Also considerations that this helps with,
such as our ability to change spring boot or armeria whenever we want.

## Modules

### Impact of auto-configuration being optional
Most typically, we'd register `META-INF/spring.factories` for each module and property defaults in
`zipkin-server-${moduleName}.yml`. However, this would rely on auto-configuration, which has a
measurable performance impact and may be disabled.

Instead, we perform discovery via a dict at the yaml path `zipkin.internal.module`. The entry would
look like this:
```yaml
zipkin:
  internal:
    module:
      sqs: zipkin.module.collector.sqs.ZipkinSQSCollectorModule
```

The above gives a very similar feeling towards auto-configuration, and with the ability to combine
values together. However, it implies the named module loads all of its own module dependencies
explicitly. This is currently a non-issue as all of our extensions are self-contained, only
depending on configuration supplied by themselves or the Zipkin server.

### yaml syntax
Starting with Spring Boot 2.0, merging YAML lists from different profiles is no longer
supported. Here's what breaks.

Given zipkin-server-sqs.yml:
```yaml
zipkin:
  internal:
    module:
      - zipkin.module.collector.sqs.ZipkinSQSCollectorModule
```

.. and zipkin-server-kinesis.yml
```yaml
zipkin:
  internal:
    module:
      - zipkin.module.collector.kinesis.ZipkinKinesisCollectorModule
```

When both profiles are present, a `List<String>` property of `zipkin.internal.module` results in one
of the above, not both. Those not checking the `/health` endpoint may not notice the problem.


Instead, we use what is supported: map based property merging.

Given zipkin-server-sqs.yml:
```yaml
zipkin:
  internal:
    module:
      sqs: zipkin.module.collector.sqs.ZipkinSQSCollectorModule
```

.. and zipkin-server-kinesis.yml
```yaml
zipkin:
  internal:
    module:
      kinesis: zipkin.module.collector.kinesis.ZipkinKinesisCollectorModule
```

When both profiles are present, a `Map<String, String>` property of `zipkin.internal.module` results
in both entries.

## Performance related optimizations and impacts

Many users expect Zipkin to start as fast as other metrics tools, such as Prometheus. In other words
instantly. While some have very fast laptops, performance can be quite different in non-laptop
scenarios, particularly when using docker. Even some laptop users had reported actual startup times
between 10 and 30 seconds. Slow start has caused at least one site to change products, and some
ecosystem players to change products also. Hence, we take this very seriously.

Our goal is to be at the second range for laptops and a few seconds or less in Docker inclusive of
JVM start time.

It is equally important the amount of time until first healthy request. In other words, cheating by
deferring evaluation to make startup seem faster will not solve this. We have sites that block until
healthy, so moving the time around will not make things easier for them.

Luckily, we do not support custom servers. This allows us many options that a typical Spring Boot
application will not be able to do. For example, we can shut off AutoConfiguration and strip out
components we don't use ourselves. Below discusses some of these tradeoffs

### Disabling auto configuration

In the zipkin server yaml, we disable a significant amount of auto-configuration, and we also are
very strict about dependencies. This means there is less auto-configuration work going on in Zipkin
server than a normal Spring Boot application. Starting Zipkin with no options (just in-memory) may
not take notably longer than if auto configuration was disabled.

When multiple configuration options are set, Zipkin can start measurably faster with auto-
configuration disabled. For example, using storage throttling and elasticsearch storage, a
measurement of best in 5 runs enabled vs disabled resulted in 4-7% improvement, depending on whether
the terms are in JVM total time, or time in Spring Boot.

Disabling auto-configuration interferes with some functionality that uses implicit configuration,
such as Actuator. That said, the way we disabled auto-configuration, was property based, which means
any integration can re-enable it in worst case.

### Making Actuator optional

Actuator is both a source of size and slowness. We have a profile called `skipActuator`, which
allows us to track performance as other factors change such as the version of Spring Boot.

While our build includes the ability to opt-out of actuator, the default should load what we haven't
disabled in yaml. `ActuatorImporter` reads the path "zipkin.internal.actuator.include" to find types
auto-configuration would have otherwise discovered from `META-INF/spring.factories`. By reading just
the type names, we avoid a compile dependency which would break the `skipActuator` build feature.

Future versions (>v2.1) of Spring Boot may reduce the size or slowness of the actuator subsystem and
could imply a revisit. If such happens take close attention to size, not just startup time, as we
skip actuator for both reasons.
