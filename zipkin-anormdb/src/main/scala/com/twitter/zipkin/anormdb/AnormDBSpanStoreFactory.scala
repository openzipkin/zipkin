package com.twitter.zipkin.anormdb

import com.twitter.app.App
import com.twitter.zipkin.storage.SpanStore
import com.twitter.zipkin.storage.anormdb.{AnormSpanStore, SpanStoreDB}

trait AnormDBSpanStoreFactory { self: App =>
  val anormDB = flag("zipkin.storage.anormdb.db", "sqlite::memory:", "JDBC location URL for the AnormDB")
  val anormInstall = flag("zipkin.storage.anormdb.install", false, "Create the tables")

  def newAnormSpanStore(): SpanStore = {
    val db = SpanStoreDB(anormDB())
    val conn = if (anormInstall()) Some(db.install()) else None
    new AnormSpanStore(db, conn)
  }
}
