package com.twitter.zipkin.storage.anormdb

import com.twitter.util.Await._
import com.twitter.zipkin.common.{Dependencies, Span}
import com.twitter.zipkin.storage.DependencyStoreSpec

class AnormDependencyStoreSpec extends DependencyStoreSpec {
  val db = DB(new DBConfig("sqlite-memory", install = true))
  var store = new AnormDependencyStore(db, Some(db.install()))

  override def processDependencies(spans: List[Span]) = {
    val deps = new Dependencies(spans.head.startTs.get, spans.last.endTs.get, Dependencies.toLinks(spans))
    result(store.storeDependencies(deps))
  }

  override def clear = {
    store = new AnormDependencyStore(db, Some(db.install()))
  }
}
