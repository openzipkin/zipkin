/*
 * Copyright 2013 Twitter Inc.
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

package com.twitter.zipkin.anormdb

import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.Aggregates
import com.twitter.zipkin.storage.anormdb.{AnormAggregates, DB}

object AggregatesBuilder {
  def apply(db:DB) = {
    new AggregatesBuilder(db)
  }
}
class AggregatesBuilder(db: DB) extends Builder[Aggregates] {
  def apply() = {
    AnormAggregates(db)
  }
}
