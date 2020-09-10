# zipkin-storage-cassandra rationale

## Why do we use prepared statements?

We use prepared statements (instead of simple statements) for anything executed more than once.
This reduces load on the server, as the CQL query does not have to parsed server-side again and
again.

This applies even for health checks and querying for service names, which have only constant
parameters and do not select partition keys.

When partition keys are in use, ex `SELECT * FROM span WHERE trace_id = ?`, prepared statements
offer a second advantage in that you get automatic token-aware routing.

The above was distilled from https://groups.google.com/a/lists.datastax.com/d/msg/java-driver-user/d6wLkH3xDLI/jUWOokKVAgAJ
