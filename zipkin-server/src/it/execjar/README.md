# execjar
This includes tests that use the exec jar produced by the zipkin-server build.

It intentionally doesn't depend on any zipkin pom, to ensure the only
thing in the classpath is the exec jar.
