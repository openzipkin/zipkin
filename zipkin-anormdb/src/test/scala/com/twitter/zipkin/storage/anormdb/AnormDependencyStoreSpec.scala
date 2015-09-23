package com.twitter.zipkin.storage.anormdb

import com.twitter.zipkin.storage.DependencyStoreSpec

class AnormDependencyStoreSpec extends DependencyStoreSpec {
  val db = DB(new DBConfig("sqlite-memory", install = true))
  var store = new AnormDependencyStore(db, Some(db.install()))

  override def clear = {
    store = new AnormDependencyStore(db, Some(db.install()))
  }
}
