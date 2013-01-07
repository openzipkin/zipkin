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

import com.twitter.zipkin.builder.{Builder => ZBuilder}

object Store {

  private val nullAggregatesBuilder = new ZBuilder[Aggregates] {
    def apply() = new NullAggregates
  }

  case class Builder(
    storageBuilder: ZBuilder[Storage],
    indexBuilder: ZBuilder[Index],
    aggregatesBuilder: ZBuilder[Aggregates] = nullAggregatesBuilder
  ) extends ZBuilder[Store] {
    def apply() = Store(storageBuilder.apply(), indexBuilder.apply(), aggregatesBuilder.apply())
  }
}

/**
 * Wrapper class for the necessary store components
 */
case class Store(storage: Storage, index: Index, aggregates: Aggregates)
