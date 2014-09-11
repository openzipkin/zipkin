package com.twitter.zipkin.aggregate

object RunHadoopJob extends App {
  com.twitter.scalding.Tool.main(Array("com.twitter.zipkin.aggregate.ZipkinAggregateJob","--hdfs") ++ args)
}
