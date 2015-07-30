# zipkin-example
The zipkin-example project combines all parts of the service into a single
package and adds a trace generator to provide random example traces.

## Starting the example server
The following command will start an instance of ZipkinExample using SQLite
in-memory as the data store and populating it with a few example traces.

```bash
./gradlew :zipkin-example:run
```

You can customize execution by specifying your own `runArgs`. Use `-help` to discover what's available.

```bash
./gradlew :zipkin-example:run -PrunArgs='-help'
```
## Using zipkin
Once started, you can browse traces in a web browser http://localhost:8080/
