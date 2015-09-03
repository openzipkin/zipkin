/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.collector

import com.twitter.finagle.Service
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.thriftscala

class OstrichService(serviceStatsPrefix: String) extends Service[Span, Unit] {
  def apply(span: Span): Future[Unit] = {
    for {
      start <- span.getAnnotation(thriftscala.Constants.SERVER_RECV)
      end <- span.getAnnotation(thriftscala.Constants.SERVER_SEND)
    } {
      span.serviceNames.foreach(serviceName => {
        Stats.addMetric(serviceStatsPrefix + serviceName, (end - start).toInt)
        Stats.addMetric(serviceStatsPrefix + serviceName + "." + span.name, (end - start).toInt)
      })
    }

    Future.Unit
  }
}
