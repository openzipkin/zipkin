/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.twitter.zipkin.config

class JsConfig extends StaticResourceConfig {
  val resourceType = "js"

  lazy val remoteResources = Seq(
    "https://ajax.googleapis.com/ajax/libs/jquery/1.7.2/jquery.min.js",
    "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8.18/jquery-ui.min.js"
  )

  lazy val localResources = Seq(
    "underscore-1.3.3.js",
    "backbone-0.9.2.js",
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
    "application-models.js",
    "application-views.js",
    "application-index.js",
    "application-show.js",
    "application-static.js"
  )

  lazy val localAggregatesResources = localResources ++ Seq(
    "sankey.js",
    "zipkin-global-dependency.js",
    "application-aggregates.js"
  )
}
