package com.twitter.zipkin.storage.mongodb.utils

import com.mongodb.casbah.Imports._

trait Index {
  def apply(collection: MongoCollection): Unit
}

object Index {

  abstract class WithOptions(key: String, options: MongoDBObject) extends Index {
    override def apply(collection: MongoCollection): Unit = collection.ensureIndex(
      MongoDBObject(key -> 1),
      options
    )
  }

  case class ExpiresAt(key: String) extends WithOptions(key, MongoDBObject("expireAfterSeconds" -> 0))

  case class Unique(key: String) extends WithOptions(key, MongoDBObject("unique" -> true))

  case class Generic(key: String) extends WithOptions(key, MongoDBObject())

}

object EnsureIndexes {
  def apply(collection: MongoCollection)(indexes: Index*): Unit = indexes.foreach(_.apply(collection))
}
