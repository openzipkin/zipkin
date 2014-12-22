package com.twitter.zipkin.aggregate.cassandra

import com.twitter.cassie.connection.ClientProvider
import com.twitter.util.{Await, Duration, Future}
import com.twitter.zipkin.storage.cassandra.CassandraStorage
import org.apache.cassandra.finagle.thrift.TokenRange
import org.apache.hadoop.mapred._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Random

final class StorageInputFormat extends InputFormat[Key,EncodedSpan] {
  private type CfSplit[T] = (T,T)

  private def inputSplitSize = 64*1024

  private def describeSplitsForRange(clientProvider: ClientProvider, cfName: String, range: TokenRange) : Future[Seq[String]] = {
    clientProvider.map { client =>
      client.describe_splits(cfName, range.start_token, range.end_token, inputSplitSize)
        .map(_.asScala.toSeq)
    }
  }

  private def describeSubsplitsForRange(clientProvider: ClientProvider, cfName: String, range: TokenRange) : Future[List[CfSplit[String]]] = {
    val splitsFuture = describeSplitsForRange(clientProvider, cfName, range)
      .map { r => toRangeList(r.toList) }
    splitsFuture
  }

  @tailrec
  private def toRangeList[T](seq: List[T], accum: List[CfSplit[T]] = Nil) : List[CfSplit[T]] = {
    seq match {
      case Nil => Nil
      case head :: Nil => accum
      case head :: tail =>
        toRangeList(tail, (head, tail.head) :: accum)
    }
  }

  private def getTokenRanges(clientProvider: ClientProvider, keyspaceName: String) : Future[List[TokenRange]]
  = clientProvider.map { client => client.describe_ring(keyspaceName).map(_.asScala.toList) }

  private def getInputSplitsForRange(clientProvider: ClientProvider, cfName: String, range: TokenRange) : Future[List[InputSplit]] = {
    describeSubsplitsForRange(clientProvider, cfName, range).map { subsplits: List[CfSplit[String]] =>
      // TODO: Fix wraparound,
      // TODO: check Cassandra source code (cassandra-hadoop), AbstractColumnFamilyInputFormat.SplitCallable.call
      val wraparoundRanges = subsplits
      wraparoundRanges map {
        case (left,right) => new StorageInputSplit(left,right,range.endpoints.asScala)
      }
    }
  }

  override def getSplits(conf: JobConf, numSplits: Int): Array[InputSplit] = {
    val storage = HadoopStorage.cassandraStoreBuilder(conf).storageBuilder.apply().asInstanceOf[CassandraStorage]

    val flattened = Await.result(storage.keyspace.provider.map { client =>
      getTokenRanges(storage.keyspace.provider, storage.traces.keyspace) flatMap { ranges =>
        println("Found "+ranges.length+" TokenRanges")
        val inputSplitsFutures = ranges map { range =>
          getInputSplitsForRange(storage.keyspace.provider, storage.traces.name, range)
        }
        Future.collect(inputSplitsFutures)
      }
    }, Duration.Top).flatten

    storage.close()

    Random.shuffle(flattened).toArray
  }

  override def getRecordReader(inputSplit: InputSplit, jobConf: JobConf, reporter: Reporter)
  = new StorageRecordReader(inputSplit.asInstanceOf[StorageInputSplit], jobConf, reporter)
}