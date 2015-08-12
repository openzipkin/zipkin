package com.twitter.zipkin.cassandra

import com.twitter.cassie.KeyspaceBuilder
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.cassandra.CassieSpanStore
import com.twitter.zipkin.storage.{Index, SpanStoreStorageWithIndexAdapter, Storage}

/** Allows [[CassieSpanStore]] to be used with legacy [[Builder]]s. */
case class StorageWithIndexBuilder(
  keyspaceBuilder: KeyspaceBuilder
) extends Builder[Storage with Index] {self =>

  def apply() = {
    new SpanStoreStorageWithIndexAdapter(new CassieSpanStore(keyspaceBuilder.connect()))
  }
}
