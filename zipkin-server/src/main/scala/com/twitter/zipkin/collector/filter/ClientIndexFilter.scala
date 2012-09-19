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
package com.twitter.zipkin.collector.filter

import com.twitter.finagle.{Service, Filter}
import com.twitter.util.Future
import com.twitter.zipkin.common.Span

/**
 * Filter that determines whether to index a span.
 * Spans with the Finagle default service name "client" should not be indexed
 * since they are unhelpful. Instead, rely on indexed server-side span names.
 */
class ClientIndexFilter extends Filter[Span, Unit, Span, Unit] {
  def apply(req: Span, service: Service[Span, Unit]): Future[Unit] = {
    if (shouldIndex(req)) {
      service(req)
    } else {
      Future.Unit
    }
  }

  private[filter] def shouldIndex(span: Span): Boolean = {
    !(span.isClientSide() && span.serviceNames.contains("client"))
  }
}
