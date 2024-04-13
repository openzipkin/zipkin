# zipkin-benchmarks

This module includes [JMH](http://openjdk.java.net/projects/code-tools/jmh/)
benchmarks for zipkin. You can use these to measure overhead.

### Running the benchmark
From the project directory, run this to build the benchmarks:

```bash
$ ./mvnw install -pl benchmarks -am -Dmaven.test.skip.exec=true
```

and the following to run them:

```bash
$ java -jar benchmarks/target/benchmarks.jar
```
