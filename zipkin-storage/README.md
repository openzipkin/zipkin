# zipkin-storage

Modules here implement popular storage options available by default in
the [server build](../zipkin-server).

Please note all modules here require JRE 8+ eventhough `InMemoryStorage`
will run on JRE 6+.

These libraries are also usable outside the server, for example in
custom collectors or storage pipelines. While compatibility guarantees
are strong, choices may be dropped over time.

Storage modules ending in `-v1` are discouraged for new sites as they
use an older data model. At some point in the future, we will stop
publishing v1 storage options.
