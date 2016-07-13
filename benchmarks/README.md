Zipkin Benchmarks
===================

This module includes [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmarks for Zipkin.

=== Running the benchmark
From the parent directory, run `./mvnw install` to build the benchmarks, and the following to run them:

```bash
# Run with a single worker thread
$ java -jar benchmarks/target/benchmarks.jar
# Add contention by running with 4 threads
$ java -jar benchmarks/target/benchmarks.jar -t4
```

=== Updating thrifts
Thrift changes are not frequent, but when they occur, please update the generated source.

```bash
$ git clone https://github.com/openzipkin/zipkin-api
$ rm -rf src/main/java/com
$ thrift -r --gen java -out src/main/java zipkin-api/thrift/zipkinCore.thrift
$ mvn  com.mycila:license-maven-plugin:format
$ rm -rf zipkin-api
```
