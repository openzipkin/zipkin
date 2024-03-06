# Zipkin Lens

Zipkin-lens is the UI for [Zipkin](https://github.com/openzipkin/zipkin). It is a modern replacement of the [classic](https://github.com/openzipkin-attic/zipkin-classic) UI which has proved its merit since the beginning of the Zipkin project.

Here are a couple example screenshots:

<img width="1920" alt="Search Screen" src="https://user-images.githubusercontent.com/64215/49579677-4602de00-f990-11e8-81b7-dd782ce91227.png">
<img width="1920" alt="Trace Detail Screen" src="https://user-images.githubusercontent.com/64215/49579684-4d29ec00-f990-11e8-8799-5c53a503413e.png">

## Quick start

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.<br />
Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

The page will reload if you make edits.<br />
You will also see any lint errors in the console.

By default, API requests are proxied to `http://localhost:9411`. You can change this target using
the `API_BASE` environment variable, e.g., `API_BASE=http://tracing.company.com npm start`.

### `npm test`

Launches the test runner in the interactive watch mode.<br />
See the section about [running tests](https://facebook.github.io/create-react-app/docs/running-tests) for more information.

To get a coverage report as well, run `npm test -- --coverage`.

### `npm run build`

Builds the app for production to the `build` folder.<br />
It correctly bundles React in production mode and optimizes the build for the best performance.

## Build tips

### Maven build

This project is not published to NPM, rather Maven, as the primary consumer is [zipkin-server](../zipkin-server).
`../mvnw clean install` installs and builds via NPM. The resulting assets, such as index.html, are
placed into the "zipkin-lens" directory of zipkin-lens.jar. This is published on release as
[io.zipkin:zipkin-lens](https://central.sonatype.com/search?q=io.zipkin%3Azipkin-lens).

### Use the production node version

The production UI is built with Maven. To use the same version, issue this command:

```bash
nvm use $(../mvnw help:evaluate -Dexpression=node.version -q -DforceStdout)
```

Now, it is less likely a pull request will fail when `npm test` succeeds locally.

## Localization

We use [LinguiJS](https://lingui.js.org/) for localization of the UI. Translations for strings are
found in the JSON files under [here](src/translations). The Javascript files in the directory are
compiled from the JSON files. We're always excited to have help maintaining these translations - if
you see a string in the UI that is not translated or mistranslated, please feel free to send a PR to
the JSON file to fix it. If you can, please run `yarn run compile` to also compile the translation
into the output. If it's tedious to set up an environment for it, though, don't worry we'll take care
of it.

### Adding a new locale

To add a new translated locale, first edit [.linguirc](.linguirc) and add the locale to the
`locales` section. Next, run `yarn run extract` to extract a new file under `src/translations` for
the locale. Translate as many strings in the JSON file as you can. Then run `yarn run compile` to
compile the strings.

Finally, edit [App.tsx](src/components/App/App.tsx) and
[LanguageSelector.tsx](src/components/App/LanguageSelector.tsx) to import the new translation and
add an entry to the language selector respectively.

## Dev Tools

As the app is a SPA using React, it can be difficult to debug issues using native browser tools because
the HTML does not map directly to any source code. It is recommended to install these extensions if you
need to debug the page (they will only work with the local dev server, not a production build).

### React Developer Tools

Chrome: https://chromewebstore.google.com/detail/react-developer-tools/fmkadmapgofadopljbjfkapdkoienihi
Firefox: https://addons.mozilla.org/en-US/firefox/addon/react-devtools/

### Redux DevTools

Chrome: https://chromewebstore.google.com/detail/redux-devtools/lmhkpmbekcpmknklioeibfkpmmfibljd
Firefox: https://addons.mozilla.org/en-US/firefox/addon/reduxdevtools/

## Running behind a reverse proxy
Since version `2.20`, Zipkin Lens supports running under an arbitrary context root. As a result,
it can be proxied under a different path than `/zipkin` such as `/admin/zipkin`.

As an example, here is the configuration for Apache HTTP Server acting as a reverse proxy
for a Zipkin instance running on the same host:

```
LoadModule proxy_module lib/httpd/modules/mod_proxy.so
LoadModule proxy_http_module lib/httpd/modules/mod_proxy_http.so

ProxyPass "/admin/zipkin"  "http://localhost:9411/zipkin"
ProxyPassReverse "/admin/zipkin"  "http://localhost:9411/zipkin"
```

For the reverse proxy configuration to work, Zipkin needs to be started with the `zipkin.ui.basepath`
parameter pointing to the proxy path:

```bash
java -jar zipkin.jar --zipkin.ui.basepath=/admin/zipkin
```

or via docker
```bash
docker run -e ZIPKIN_UI_BASEPATH=/admin/zipkin -p 9411:9411 openzipkin/zipkin
```

## Authentication / Authorization

Zipkin Lens can be secured by running it behind an authenticating proxy like [Apache HTTPD](https://httpd.apache.org/docs/current/howto/auth.html), [Nginx](https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html) or similar.

## Acknowledgements
Zipkin Lens design includes facets of old and new designs. Here are
some notable new aspects borrowed or adapted from others.

### Overall Design
Zipkin Lens was originally an internal UI at LINE called IMON. One driving feature was to
allow site-specific tags like "Phase" and "Instance Id" to be choices for trace queries.
The UI was originally matching color schemes of other tools like Grafana and fit in with
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
Jaeger UI, as a mini-map occurs over the trace and lets you zoom to a
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
