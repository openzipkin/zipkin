/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/12/12
 * Time: 8:30 PM
 * To change this template use File | Settings | File Templates.
 */

package com.twitter.zipkin.hadoop


import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{Span, Constants, Annotation}
import scala.collection.JavaConverters._
import java.nio.ByteBuffer
import java.net.{Inet4Address, Inet6Address, InetAddress}

class GetAllServiceNames(args : Args) extends Job(args)  {

}
