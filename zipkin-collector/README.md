# zipkin-collector

Modules here implement popular transport options available by default in
the [server build](../zipkin-server).

Please note all modules here require JRE 8+

These libraries are also usable outside the server, for example in
custom collectors or storage pipelines. While compatibility guarantees
are strong, choices may be dropped over time.

Collector modules ending in `-v1` are discouraged for new sites as they
use an older data model. At some point in the future, we will stop
publishing v1 collector options.
