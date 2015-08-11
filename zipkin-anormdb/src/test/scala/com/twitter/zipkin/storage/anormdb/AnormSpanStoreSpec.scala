package com.twitter.zipkin.storage.anormdb

import com.twitter.zipkin.storage.SpanStoreSpec

class AnormSpanStoreSpec extends SpanStoreSpec {
  val db = SpanStoreDB("sqlite::memory:")
  var store = new AnormSpanStore(db, Some(db.install()))

  override def clear = {
    store = new AnormSpanStore(db, Some(db.install(true)))
  }
}
