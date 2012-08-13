package com.twitter.zipkin.config

trait StaticResourceConfig {
  val pathPrefix: String = "/public"

  val resourceType: String

  val remoteResources: Seq[String]

  val localResources: Seq[String]

  lazy val resources = remoteResources ++
    localResources.map { r =>
      "%s/%s/%s".format(pathPrefix, resourceType, r)
    }
}
