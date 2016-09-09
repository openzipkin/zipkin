# storage-elasticsearch-jest

Implements a client provider for Elasticsearch that uses Elasticsearch's HTTP APIs by way of the [Jest client](https://github.com/searchbox-io/Jest) for flexibility.

See the main elasticsearch module for more details.

## Testing this component
This module conditionally runs integration tests against a local Elasticsearch instance.

Tests are configured to automatically access Elasticsearch started with its defaults.
To ensure tests execute, download an Elasticsearch 2.x distribution, extract it, and run `bin/elasticsearch`. 

If you run tests via Maven or otherwise when Elasticsearch is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 50, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.
