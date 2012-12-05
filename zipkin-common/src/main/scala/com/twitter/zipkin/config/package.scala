package com.twitter.zipkin

import com.twitter.util.Config
import com.twitter.zipkin.storage.{Index, Aggregates, Storage}

package object config {
  type StorageConfig = Config[Storage]
  type IndexConfig = Config[Index]
  type AggregatesConfig = Config[Aggregates]
}
