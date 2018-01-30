# zipkin-ui

Zipkin-UI is a single-page application mounted at /zipkin. For simplicity,
assume paths mentioned below are relative to that. For example, the UI
reads config.json, from the absolute path /zipkin/config.json

When looking at a trace, the browser is sent to the path "/traces/{id}".
For the single-page app to serve that route, the server needs to forward
the request to "/index.html". The same forwarding applies to "/dependencies"
and any other routes the UI controls.

Under the scenes the JavaScript code looks at window.location to figure
out what the UI should do. This is handled by a route api defined in the
crossroads library.

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

## How do I run against a proxy zipkin-backend?

By specifying the `proxy` environment variable, you can point the zipkin-ui to a different backend, allowing you to access real data while developing locally.
An example to run with npm would be `proxy=http://myzipkininstance.com:9411/zipkin/ npm run dev`. (note that prefixing with http:// and suffixing the port is mandatory)

## What's the easiest way to develop against this locally?

The maven install process already downloads everything needed to do development,
so you don't need to install node/npm or whatever. Instead, you can use the
`./npm.sh` shell script to perform npm operations. Here's how you launch zipkin
server and webapp to work together:

* In one terminal, go to the root of the zipkin repo and run this to build zipkin:

```bash
# In one terminal, build the server and also make its dependencies (run from the root of the zipkin repo)
$ ./mvnw -DskipTests --also-make -pl zipkin-server clean install
# Run it!
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```

* In another terminal, launch the zipkin UI server:

```bash
# Do this in another terminal!
$ cd zipkin-ui
$ proxy=http://localhost:9411/zipkin/ ./npm.sh run dev
```

This runs an NPM development server, which will automatically rebuild the webapp
when you change the source code. You should now be able to access your local
copy of zipkin-ui at http://localhost:9090.

#### What's an easy way to create new spans for testing?

Using this setup, if you open the web UI and find a trace from zipkin-server,
you can download a JSON blob by right clicking on the JSON button on the trace
page (top right) and doing a "Save As". Modify the span to your heart's content,
and then you can submit the span(s) via curl:

```bash
$ curl -H "Content-Type: application/json" --data @span.json http://localhost:9411/api/v1/spans
```

#### How do I find logs associated with a particular trace

Since zipkin provides a correlation id (the trace id), it's a good pattern to add it in your logs.
If you use zipkin, it's very likely that you have distributed logging sytem also and a way to query your logs (e.g. ELK stack).

One convenient way to switch from a trace to its logs is to get a button which directly links a trace to its logs.

This feature can be activated by setting the property `zipkin.ui.logs-url` or its corresponding environment variable:

* Kibana 3: `ZIPKIN_UI_LOGS_URL=http://kibana.company.com/query={traceId}`
* Kibana 4 or newer: `ZIPKIN_UI_LOGS_URL=http://kibana.company.com/app/kibana#/discover?_a=(index:'filebeat-*',query:(language:lucene,query:'{traceId}'))`
  This assumes that the log data is stored under the `filebeat-*` index pattern (replace this with the correct pattern if needed) and it uses the default timerange of 15 minutes (change it to 24 hours by adding `&_g=(refreshInterval:(display:Off,pause:!f,value:0),time:(from:now-24h,mode:quick,to:now))` to the URL for example).

where `{traceId}` will be contextually replaced by the trace id.

If this feature is activated, you'll see on the trace detail page an additional button named `logs`.

![Logs Button](https://cloud.githubusercontent.com/assets/9842366/20482538/6e35ca66-afed-11e6-90e9-1e28f66d985e.png)

#### How do I adjust the error rates in the dependency graph

By default, the /dependency endpoint colors a link yellow when the error
rate is 50% or higher, or red when it 75% or higher. You can control
these rates via the `dependency.low-error-rate` and `dependency.high-error-rate`
properties:

Ex. To make lines yellow when there's a 10% error rate, set:
`ZIPKIN_UI_DEPENDENCY_LOW_ERROR_RATE=0.1`

To disable coloring of lines, set both rates to a number higher than 1.

## Running behind a reverse proxy
Starting with Zipkin `1.31.2`, Zipkin UI supports running under an arbitrary _context root_. As a result, it can be proxied
under a different path than `/zipkin/` such as `/proxy/foo/bar/zipkin/`. 

> Note that Zipkin requires the last path segment to be `zipkin`.

> Also note that due to `html-webpack-plugin` limitations, Zipkin UI relies on a 
[`base` tag](https://www.w3schools.com/TAgs/tag_base.asp) and its `href` attribute to be set in the `index.html` file. 
By default its value is `/zipkin/` and as such the reverse proxy must rewrite the value to an alternate _context root_.

### Apache HTTP as a Zipkin reverse proxy
To configure Apache HTTP as a reverse proxy, following minimal configuration is required.

```
LoadModule proxy_module libexec/apache2/mod_proxy.so
LoadModule proxy_html_module libexec/apache2/mod_proxy_html.so
LoadModule proxy_http_module libexec/apache2/mod_proxy_http.so

ProxyPass /proxy/foo/bar/ http://localhost:9411/
SetOutputFilter proxy-html
ProxyHTMLURLMap /zipkin/ /proxy/foo/bar/zipkin/
ProxyHTMLLinks  base        href
``` 

To access Zipkin UI behind the reverse proxy, execute:
```bash
$ curl http://localhost/proxy/foo/bar/zipkin/
<html><head><!--
      add 'base' tag to work around the fact that 'html-webpack-plugin' does not work
      with '__webpack_public_path__' being set as reported at https://github.com/jantimon/html-webpack-plugin/issues/119
    --><base href="/proxy/foo/bar/zipkin/"><link rel="icon" type="image/x-icon" href="favicon.ico"><meta charset="UTF-8"><title>Webpack App</title><link href="app-94a6ee84dc608c5f9e66.min.css" rel="stylesheet"></head><body>
  <script type="text/javascript" src="app-94a6ee84dc608c5f9e66.min.js"></script></body></html>
```
As you would see, the attribute `href` of the `base` tag is rewritten which is the way to get around the 
`html-webpack-plugin` limitations.

Uploading the span is easy as
```bash
$ curl -H "Content-Type: application/json" --data-binary "[$(cat ../benchmarks/src/main/resources/span-local.json)]" http://localhost/proxy/foo/bar/api/v1/spans
```

And then it's observable in the UI:
```bash
$ open http://localhost/proxy/foo/bar/zipkin/?serviceName=zipkin-server&startTs=1378193040000&endTs=1505463856013 
```
