package com.twitter.zipkin.aggregate

import java.nio.ByteBuffer

import com.twitter.zipkin.common.Dependencies
import org.apache.hadoop.io.LongWritable
import org.apache.hadoop.mapred._


package object cassandra {
  type Config = JobConf // Properties // Should probably be JobConf later on
  type Key = LongWritable // Long
  type Input = RecordReader[Key,EncodedSpan]
  type Output = OutputCollector[Key,Dependencies]
  type EncodedSpan = ByteBuffer // ByteBuffer
  type Data = Pair[Key, EncodedSpan]
}
