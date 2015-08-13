package com.twitter.zipkin.anormdb

import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.anormdb.{DB, AnormSpanStore}
import com.twitter.zipkin.storage.SpanStore

/** Allows [[AnormSpanStore]] to be used with legacy [[Builder]]s. */
case class SpanStoreBuilder(
  db: DB,
  install: Boolean = false
) extends Builder[SpanStore] {self =>
  def apply() = {
    val conn = if (install) Some(db.install()) else None
    new AnormSpanStore(db, conn)
  }
}
