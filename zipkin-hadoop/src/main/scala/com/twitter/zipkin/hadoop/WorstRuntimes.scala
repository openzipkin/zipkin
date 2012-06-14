package com.twitter.zipkin.hadoop

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/12/12
 * Time: 1:44 PM
 * To change this template use File | Settings | File Templates.
 */

import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{Span, Constants, Annotation}
import java.nio.ByteBuffer
import java.net.{InetAddress}


class WorstRuntimes(args: Args) extends Job(args) with DefaultDateRangeJob {

  val clientAnnotations = Seq(Constants.CLIENT_RECV, Constants.CLIENT_SEND)

  SpanSource()
    .read
    // only need id and annotations for this
    .mapTo(0 -> ('id, 'annotations)) { s: Span => (s.id, s.annotations.toList) }
    .groupBy('id) { data =>
  // merge annotations from all span objects into one list
    data.reduce('annotations) {
      (annotations: List[Annotation], annotations1: List[Annotation]) =>
        annotations ++ annotations1
    }
  }
    // let's find those server annotations and convert into service name and duration
    .flatMap('annotations -> ('service, 'duration)) { annotations: List[Annotation] =>
    var clientSend: Option[Annotation] = None
    var clientReceived: Option[Annotation] = None
    annotations.foreach { a =>
      if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
      if (Constants.CLIENT_RECV.equals(a.getValue)) clientReceived = Some(a)
    }
    // only return a value if we have both annotations
    for (cs <- clientSend; cr <- clientReceived)
    yield (cs.getHost.service_name, (cr.timestamp - cs.timestamp) / 1000)
  }
    //sort by duration, find the 100 largest
    .groupBy('service) { _.sortBy('duration).reverse.take(100)}
    .write(Tsv(args("output")))


  def getAddress(ipv4: Int): InetAddress = {
    val array = new Array[Byte](4)
    val bb = ByteBuffer.wrap(array)
    bb.putInt(ipv4)
    InetAddress.getByAddress(array)
  }
}