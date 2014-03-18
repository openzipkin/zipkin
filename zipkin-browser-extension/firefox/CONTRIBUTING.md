To contribute a change, please create a pull request against `master`.

By contributing your code, you agree to license your contribution under the
terms of the Apache Public License v2 (see LICENSE.txt).

## Code Structure

If you've never written a Firefox extension before, here is how the code is
laid out, and where to find things. This extension was written without the
Add-On SDK or Add-On Builder.

- ant.properties and build.xml are for the ant build tool. They just help
  generate the XPI extension file. ant.properties holds the extension version.
- bootstrap.js registers the extension on load and installation. This is what
  allows the extension to be installed without restarting.
- chrome.manifest tells Firefox where to look for some of the extension's
  resources.
- install.rdf includes metadata about the extension, like the title and
  description.
- chrome/skin/classic contains the CSS for the panel that displays the Zipkin
  trace information. It also holds the extension's icons.
- chrome/locale/en-US/zipkin.properties contains translation information.
  Translations are accessed with the Locale.$STR() function, aliased in
  zipkinPanel.js to t().
- chrome/content contains the code that actually does work:
   - options.xul is the template for the extension's settings. (Some of those
     settings are inline preferences and some are exposed via the Firebug panel
     options.) The location of options.xul is specified in install.rdf, the
     defaults are in defaults/preferences/prefs.js, and the code to remember
     the options is in options.js. The preferences are:
       - `DBG_ZIPKIN`: Toggles whether to log debugging messages to the
         FBTrace console.
       - `profile_site`: A regular expression indicating which site(s) to
         trace.
       - `zipkin_site`: The base URL of the Zipkin web UI.
       - `enable_tracing`: Whether tracing is enabled.
       - `trace_zipkin_ui`: Whether to trace the Zipkin UI or not, if it
         matches the profile_site regex.
   - mainOverlay.js and mainOverlay.xul are superfluous, only present for
     backwards-compatibility to make sure main.js gets loaded.
   - log.js, translate.js, getTraceId.js, and http.js provide utility methods
     that make other code easier to read. They are structured as
     [AMD](https://github.com/amdjs/amdjs-api/wiki/AMD) modules that get
     imported into other code with the define() function. log.js allows logging
     to the FBTrace console for debugging; translate.js makes it easier to use
     localization features; getTraceId.js generates Zipkin trace IDs; and
     http.js provides a utility for making HTTP requests.
   - generateOutput.js is also an AMD module, but a more important one: it
     builds the HTML representing Zipkin trace information to display in the
     Firebug panel.
   - traces.js is an AMD module that stores information about the requests
     that this extension asks Zipkin to trace.
   - When the extension is loaded, the files run in this order: bootstrap.js,
     main.js, zipkinModule.js, zipkinPanel.js. zipkinModule.js is just a
     container for the panel. main.js includes the code to force Zipkin to
     trace requests. Requests are force-traced by intercepting them and adding
     HTTP headers for which Zipkin listens. zipkinPanel.js gets trace
     information from main.js and the Zipkin REST API and displays information
     about the traces in a Firebug panel.

As you can see, main.js and generateOutput.js are where most of the interesting
action happens since they are where the traces are initiated and the
information rendered. To actually initiate a trace, main.js registers an
observer (basically an event listener) which listens for HTTP requests to be
issued, then adds Zipkin-specific headers specifying the trace ID to use for
the request. We generate a random trace ID to use (a 64-bit int in hex) which
we can do because it's very unlikely that we'll have trace ID collisions (and
it's not a big deal if we do -- some other trace will get overwritten).
