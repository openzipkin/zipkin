package com.twitter.zipkin.storage.redis

import com.twitter.util.Await._

class SetMultimapSpec extends RedisSpecification {
  val multimap = new SetMultimap(_client, None, "span")

  test("create/read") {
    result(multimap.put("service", "span1"))
    result(multimap.get("service")) should be (Set("span1"))
  }

  test("duplicate values for the same name") {
    result(multimap.put("service", "span1"))
    result(multimap.put("service", "span2"))
    result(multimap.put("service", "span3"))
    result(multimap.get("service")) should be (Set("span1", "span2", "span3"))
  }
}
