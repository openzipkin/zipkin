# zipkin-ui

Zipkin-UI is a single-page application that reads configuration from `/config.json`.

When looking at a trace, the browser is sent to the path `/traces/{id}`. For the single-page
app to serve that route, the server needs to forward the request to `index.html`. The same
forwarding applies to `/dependencies` and any other routes the UI controls. 

Under the scenes the JavaScript code looks at `window.location` to figure out what the
UI should do. This is handled by a route api defined in the crossroads library.

The suggested logic for serving the assets of Zipkin-UI is as follows:

 1. If the browser is requesting a file with an extension (that is, the last path segment has a `.` in it), then
    serve that file. If it doesn't exist, then return 404.
 1. Otherwise, serve `index.html`.
 
For an example implementation using Finatra, see [zipkin-query](https://github.com/openzipkin/zipkin/blob/5dec252e4c562b21bac5ac2f9d0b437d90988f79/zipkin-query/src/main/scala/com/twitter/zipkin/query/ZipkinQueryController.scala).

Note that in cases where a non-existent resource is requested, this logic returns the contents of `index.html`. When
loaded as a web-page, the client application will handle the problem of telling the user about this. When not,
it'll take an extra step to find where the problem is - you won't see a 404 in your network tab.

## Why is it packaged as a `.jar` and not a `.zip`?

Since many Zipkin servers are Java-based, it's convenient to distribute the UI as a jar, which can be imported by the
Gradle build tool. A `.jar` file is really only a `.zip` file, and can be treated as such. It can be opened by any
program that can extract zip files.

