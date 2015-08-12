package com.twitter.zipkin.anormdb

import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.anormdb.{DB, AnormSpanStore}
import com.twitter.zipkin.storage.{Index, SpanStoreStorageWithIndexAdapter, Storage}

/** Allows [[AnormSpanStore]] to be used with legacy [[Builder]]s. */
case class StorageWithIndexBuilder(
  db: DB,
  install: Boolean = false
) extends Builder[Storage with Index] {self =>
  def apply() = {
    val conn = if (install) Some(db.install()) else None
    new SpanStoreStorageWithIndexAdapter(new AnormSpanStore(db, conn))
  }
}
