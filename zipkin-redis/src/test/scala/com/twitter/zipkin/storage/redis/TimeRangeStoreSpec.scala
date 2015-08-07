package com.twitter.zipkin.storage.redis

import com.twitter.util.Await.{ready, result}

class TimeRangeStoreSpec extends RedisSpecification {
  val store = new TimeRangeStore(_client, "test")

  test("create/read") {
    ready(store.put(1, TimeRange(1, 2)))
    result(store.get(1)) should be(Some(TimeRange(1, 2)))
  }

  test("reads last update") {
    ready(store.put(1, TimeRange(1, 2)))
    ready(store.put(1, TimeRange(2, 3)))
    result(store.get(1)) should be(Some(TimeRange(2, 3)))
  }

  test("reads correct key") {
    ready(store.put(1, TimeRange(1, 2)))
    ready(store.put(2, TimeRange(2, 3)))
    result(store.get(1)) should be(Some(TimeRange(1, 2)))
  }
}
