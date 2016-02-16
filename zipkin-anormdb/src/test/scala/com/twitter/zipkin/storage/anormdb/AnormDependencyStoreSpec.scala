package com.twitter.zipkin.storage.anormdb

import com.twitter.util.Await.result
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.storage.DependencyStoreSpec

class AnormDependencyStoreSpec extends DependencyStoreSpec {
  var db = DB(new DBConfig("sqlite-memory", install = true))
  var conn = db.install()

  var store = new AnormDependencyStore(db, Some(conn))
  var spanStore = new AnormSpanStore(db, Some(conn))

  override def processDependencies(spans: List[Span]) = result(spanStore.apply(spans))

  override def clear = {
    db = DB(new DBConfig("sqlite-memory", install = true))
    conn = db.install()

    store = new AnormDependencyStore(db, Some(conn))
    spanStore = new AnormSpanStore(db, Some(conn))
  }
}
