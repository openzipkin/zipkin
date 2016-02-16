# zipkin-web-service

#### Configuration

`zipkin-web` applies configuration parameters through environment variables.

Below are environment variables definitions.

    * `SCRIBE_HOST`: Listen host for scribe, where traces will be sent
    * `SCRIBE_PORT`: Listen port for scribe, where traces will be sent
    * `WEB_LOG_LEVEL`: Log level written to the console; Defaults to INFO

#### Build Notes

During the build, environment should not contain `NODE_ENV=production`,
as it may cause errors similar to https://github.com/openzipkin/zipkin/issues/970.

