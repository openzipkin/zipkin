package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets.UTF_8
import com.twitter.util.Await._
import com.twitter.util.Duration
import com.twitter.zipkin.storage.IndexedTraceId
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer

class TraceIndexSpec extends RedisSpecification {

  def index = new TraceIndex[String](_client, None) {
    override def encodeKey(key: String) = copiedBuffer("foo:" + key, UTF_8)
  }

  test("create/read") {
    result(index.add("key", 1, 1234))
    result(index.list("key", 1, 1)) should be(Seq(IndexedTraceId(1234, 1)))
  }

  test("list with multiple trace ids") {
    result(index.add("key", 1, 1234))
    result(index.add("key", 2, 4567))
    result(index.list("key", 100, 100)) should be(Seq(
      IndexedTraceId(4567, 2),
      IndexedTraceId(1234, 1))
    )
  }

  test("list respects limit") {
    result(index.add("key", 1, 1234))
    result(index.add("key", 2, 4567))
    result(index.list("key", 1, 1)) should be(Seq(IndexedTraceId(1234, 1)))
  }

  test("list is unique on trace id") {
    result(index.add("key", 1, 1234))
    result(index.add("key", 2, 1234))
    result(index.list("key", 100, 100)) should be(Seq(IndexedTraceId(1234, 2)))
  }

  test("list respects ttl") {
    // Using seconds granularity as ttl literally expires entries
    val indexWithTtl = new TraceIndex[String](_client, Some(Duration.fromSeconds(10))) {
      override def encodeKey(key: String) = copiedBuffer("ttl:" + key, UTF_8)
    }

    result(indexWithTtl.add("key", 10 * 1000000, 1234))
    result(indexWithTtl.add("key", 20 * 1000000, 4567))
    result(indexWithTtl.add("key", 30 * 1000000, 8910))
    result(indexWithTtl.add("key", 40 * 1000000, 1112))
    result(indexWithTtl.list("key", 35 * 1000000, 100)) should be(Seq(
      IndexedTraceId(8910, 30 * 1000000))
    )
  }
}
