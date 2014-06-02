/*
 * Copyright 2014 Twitter Inc.
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
package com.twitter.zipkin.storage.anormdb

import com.twitter.app.App
import com.twitter.zipkin.anormdb.AnormDBSpanStoreFactory
import com.twitter.zipkin.storage.util.SpanStoreValidator
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AnormSpanStoreTest extends FunSuite {
  object AnormStore extends App with AnormDBSpanStoreFactory
  AnormStore.main(Array(
    "-zipkin.storage.anormdb.db", "sqlite::memory:",
    "-zipkin.storage.anormdb.install", "true"))

  def newSpanStore = {
    AnormStore.newAnormSpanStore()
  }

  test("validate") {
    new SpanStoreValidator(newSpanStore).validate
  }
}

