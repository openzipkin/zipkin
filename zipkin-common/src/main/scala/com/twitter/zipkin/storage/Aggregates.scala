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
package com.twitter.zipkin.storage

import com.twitter.util.Future

/**
 * Storage and retrieval interface for aggregates that may be computed offline and reloaded into
 * online storage
 */
trait Aggregates {
  def getTopAnnotations(serviceName: String): Future[Seq[String]]
  def getTopKeyValueAnnotations(serviceName: String): Future[Seq[String]]
  def getDependencies(serviceName: String): Future[Seq[String]]

  def storeDependencies(serviceName: String, endpoints: Seq[String]): Future[Unit]
  def storeTopAnnotations(serviceName: String, a: Seq[String]): Future[Unit]
  def storeTopKeyValueAnnotations(serviceName: String, a: Seq[String]): Future[Unit]
}

class NullAggregates extends Aggregates {
  def getDependencies(serviceName: String)         = Future(Seq.empty[String])
  def getTopAnnotations(serviceName: String)         = Future(Seq.empty[String])
  def getTopKeyValueAnnotations(serviceName: String) = Future(Seq.empty[String])

  def storeDependencies(serviceName: String, endpoints: Seq[String]): Future[Unit]         = Future.Unit
  def storeTopAnnotations(serviceName: String, a: Seq[String]): Future[Unit]         = Future.Unit
  def storeTopKeyValueAnnotations(serviceName: String, a: Seq[String]): Future[Unit] = Future.Unit
}
