package com.twitter.zipkin.storage

import com.twitter.util.Config

object Store {
  case class Builder(
    storageBuilder: Config[Storage],
    indexBuilder: Config[Index],
    aggregatesBuilder: Config[Aggregates]
  ) extends Config[Store] {
    def apply() = Store(storageBuilder.apply(), indexBuilder.apply(), aggregatesBuilder.apply())
  }
}

/**
 * Wrapper class for the necessary store components
 */
case class Store(storage: Storage, index: Index, aggregates: Aggregates)
