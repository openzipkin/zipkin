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

trait StaticResourceConfig {
  val pathPrefix: String = "/public"

  val resourceType: String

  val remoteResources: Seq[String]

  val localResources: Seq[String]

  val localAggregatesResources: Seq[String]

  lazy val resources = remoteResources ++
    localResources.map { r =>
      "%s/%s/%s".format(pathPrefix, resourceType, r)
    }

  lazy val aggregatesResources = remoteResources ++
    localAggregatesResources.map { r =>
      "%s/%s/%s".format(pathPrefix, resourceType, r)
    }
}
