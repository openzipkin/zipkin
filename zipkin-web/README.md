# zipkin-web-service

## Configuration

`zipkin-web` applies configuration parameters through environment variables.

Below are environment variables definitions.

    * `SCRIBE_HOST`: Listen host for scribe, where traces will be sent
    * `SCRIBE_PORT`: Listen port for scribe, where traces will be sent
    * `WEB_LOG_LEVEL`: Log level written to the console; Defaults to INFO

## Build Notes

During the build, environment should not contain `NODE_ENV=production`,
as it may cause errors similar to https://github.com/openzipkin/zipkin/issues/970.

## Developing

You must have Node.js and npm installed on your machine. First, start the zipkin-web application:

```bash
# from the root directory
$ ./gradlew zipkin-web:run
```

Then start the webpack development server:

```bash
$ cd zipkin-web
$ npm install
$ npm run dev
```

That will start zipkin-web on port 8080 and the webpack dev server on port 9090. Point your browser to http://localhost:9090 and start developing!

