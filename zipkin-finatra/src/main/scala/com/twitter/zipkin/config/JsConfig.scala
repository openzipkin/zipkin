package com.twitter.zipkin.config

class JsConfig extends StaticResourceConfig {
  val resourceType = "js"

  lazy val remoteResources = Seq(
    "https://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js",
    "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8.18/jquery-ui.min.js"
  )

  lazy val localResources = Seq(
    "bootstrap.js",
    "datepicker.js",
    "d3-2.9.1.js",
    "hogan-2.0.0.js",

    "zipkin.js",
    "zipkin-node.js",
    "zipkin-span.js",
    "zipkin-tree.js",
    "zipkin-annotation.js",
    "zipkin-config.js",
    "zipkin-filter-span.js",
    "zipkin-kv-annotation.js",
    "zipkin-lazy-tree.js",
    "zipkin-onebox.js",
    "zipkin-trace-dependency.js",
    "zipkin-trace-summary.js",

    "application.js",
    "application-index.js",
    "application-show.js",
    "application-static.js"
  )
}
