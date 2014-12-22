package com.twitter.zipkin.aggregate.cassandra

import java.io.{DataInput, DataOutput}

import org.apache.hadoop.mapred.InputSplit


final class StorageInputSplit(var startToken: String, var endToken: String, var endpoints: Seq[String]) extends InputSplit {
  def this() {
    this("","",Seq())
  }

  override def getLength: Long = Long.MaxValue
  override def getLocations: Array[String] = endpoints.toArray
  override def write(out: DataOutput) {
    out.writeUTF(startToken)
    out.writeUTF(endToken)
    out.writeInt(endpoints.length)
    endpoints.foreach(out.writeUTF)
  }
  override def readFields(in: DataInput) {
    startToken = in.readUTF()
    endToken = in.readUTF()
    val numEndpoints = in.readInt()
    endpoints = 1 to numEndpoints map (_ => in.readUTF())
  }
}