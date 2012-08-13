package com.twitter.zipkin.config

class CssConfig extends StaticResourceConfig {
  val resourceType = "css"

  val remoteResources = Seq(
    "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8/themes/ui-lightness/jquery-ui.css"
  )

  val localResources = Seq(
    "bootstrap.css",
    "bootstrap-responsive.css",
    "datepicker.css",
    "application.css"
  )
}
