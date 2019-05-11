# Zipkin Lens

Zipkin-lens is an alternative UI for [Zipkin](https://github.com/apache/incubator-zipkin), which based on React, Netflix/vizceral and chartjs.

Here are a couple example screen shots:

<img width="1920" alt="Search Screen" src="https://user-images.githubusercontent.com/64215/49579677-4602de00-f990-11e8-81b7-dd782ce91227.png">
<img width="1920" alt="Trace Detail Screen" src="https://user-images.githubusercontent.com/64215/49579684-4d29ec00-f990-11e8-8799-5c53a503413e.png">

## Quick start

Zipkin-lens required nodejs 10.2.0 to start. You could easily try Zipkin-lens at local using any zipkin deployment using below command:

```
$ npm install --dev
$ API_BASE="http://localhost:9411" npm run start
```

## URL to view development build
```
http://localhost:9000/zipkin
```

## Production build

```
$ npm run build
```

## Authentication / Authorization

Zipkin Lens can be secured by running it behind an authenticating proxy like [Apache HTTPD](https://httpd.apache.org/docs/current/howto/auth.html), [Nginx](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html) or similar.

## Acknowledgements
Zipkin Lens design includes facets of old and new designs. Here are
some notable new aspects borrowed or adapted from others.

### Overall Design
Zipkin Lens was originally an internal Ui to LINE called IMON. One driving feature was to
allow site-specific tags like "Phase" and "Instance Id" to be choices for trace queries.
The Ui was originally matching color schemes of other tools like Grafana and fit in with
the Observability ecosystem of LINE engineering. IMON was created by Igarashi Takuma and
Huy Do who later contributed it as the initial version of Zipkin Lens.

### Trace Mini Map
The original Zipkin UI trace detail screen had a Zoom feature which is
helpful for looking at nuance in a large trace by expanding the
timeline around a group of spans. However, it did not help with
navigating to these spans. We discussed the idea of a mini-map,
similar to what video games use to quickly scroll to a place of
interest in a game. This is especially important in messaging spans
where there can be a large time gap separating clusters of spans. The
initial mini-map implementation in Lens is very similar to work in
Jaeger Ui, as a mini-map occurs over the trace and lets you zoom to a
timeframe similar to Chrome or FireFox debug tools. The implementation
was different as it is implemented with SVG, which is easier to debug
than Canvas.

### Span Detail pop-under
The original Zipkin UI trace detail screen would pop-out span details
when clicking on a span. This had two problems: One was that the
pop-out blocked your position in the trace, so after you close the
pop-out you need to find it again. Another problem was that you cannot
view two span details at the same time. Lens pops these details under
a selected span. Although the data is the same as the old Zipkin UI,
the gesture is the same as in Jaeger's UI and inspired by them.

### Vizceral Dependencies graph
The original Zipkin UI dependencies screen used a D3 library which
connected services with curved arrows. These arrows varied in
thickness depending on the traffic. When you clicked an arrow, details
would pop out. The initial version of Lens uses the same library as
Haystack Ui to present service dependencies: Netflix Vizceral.
