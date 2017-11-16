# zipkin-storage-influxdb

TODO
## Logging

TODO

## Testing
This module conditionally runs integration tests against a local InfluxDB instance.

This starts a docker container or attempts to re-use an existing influxdb on localhost.

If you run tests via Maven or otherwise when InfluxDB is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 62, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.

### Running a single test

To run a single integration test, use the following syntax:

```bash
$ ./mvnw -Dit.test='ITInfluxDBStorage$SpanStoreTest#getTraces_duration' -pl zipkin-storage/influxdb clean verify
```
