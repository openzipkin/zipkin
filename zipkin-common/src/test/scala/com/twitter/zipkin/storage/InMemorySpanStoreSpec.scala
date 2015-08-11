package com.twitter.zipkin.storage

class InMemorySpanStoreSpec extends SpanStoreSpec {
  var store = new InMemorySpanStore

  override def clear = {
    store.spans.clear
    store.ttls.clear
  }
}
