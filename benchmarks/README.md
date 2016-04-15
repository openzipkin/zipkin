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
