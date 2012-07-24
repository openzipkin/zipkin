/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.hadoop.sources

import com.twitter.zipkin.gen.{BinaryAnnotation, Annotation}
import org.apache.thrift.TBase
import cascading.scheme.Scheme
import com.twitter.zipkin.gen.{Span, SpanServiceName}
import org.apache.hadoop.mapred.{JobConf, RecordReader, OutputCollector}
import com.twitter.scalding._
import cascading.tuple.Fields
import com.twitter.elephantbird.cascading2.scheme.{LzoTextDelimited, LzoThriftScheme}
import cascading.scheme.local.{TextDelimited, TextLine => CLTextLine}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileStatus, Path}

// Scala is pickier than Java about type parameters, and Cascading's Scheme
// declaration leaves some type parameters underspecified.  Fill in the type
// parameters with wildcards so the Scala compiler doesn't complain.
object HadoopSchemeInstance {
  def apply(scheme : Scheme[_,_,_,_,_]) =
    scheme.asInstanceOf[Scheme[JobConf,RecordReader[_,_],OutputCollector[_,_],_,_]]
}

abstract class HourlySuffixLzoThrift[T <: TBase[_,_] : Manifest](prefix : String, dateRange : DateRange) extends
  HourlySuffixSource(prefix, dateRange) with LzoThrift[T] {
  def column = manifest[T].erasure
}

abstract class HourlySuffixSource(prefixTemplate : String, dateRange : DateRange) extends
  TimePathedSource(prefixTemplate + TimePathedSource.YEAR_MONTH_DAY_HOUR + "/*", dateRange, DateOps.UTC)

abstract class DailySuffixSource(prefixTemplate : String, dateRange : DateRange) extends
TimePathedSource(prefixTemplate + TimePathedSource.YEAR_MONTH_DAY + "/*", dateRange, DateOps.UTC)

trait LzoThrift[T <: TBase[_, _]] extends Mappable[T] {
  def column: Class[_]

  // TODO this won't actually work locally, but we need something here for the tests
  override def localScheme = new CLTextLine()

  override def hdfsScheme = HadoopSchemeInstance(new LzoThriftScheme[T](column))
}

/**
 * Ensures that a _SUCCESS file is present in the Source path.
 */
trait SuccessFileSource extends FileSource {
  override protected def pathIsGood(p : String, conf : Configuration) = {
    val path = new Path(p)
    Option(path.getFileSystem(conf).globStatus(path)).
      map{ statuses : Array[FileStatus] =>
    // Must have a file that is called "_SUCCESS"
      statuses.exists { fs : FileStatus  =>
        fs.getPath.getName == "_SUCCESS"
      }
    }.
      getOrElse(false)
  }
}

trait LzoTsv extends DelimitedScheme {
  // TODO: This doesn't work locally
  override def localScheme = {new TextDelimited(fields, separator, types) }
  override def hdfsScheme = HadoopSchemeInstance(new LzoTextDelimited(fields, separator, types))
}

  /**
 * This is the source for trace data. Directories are like so: /logs/zipkin/yyyy/mm/dd/hh
 */
case class SpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[Span]("/logs/zipkin/", dateRange)

case class FixedSpanSource(p : String) extends FixedPathSource(p) with LzoThrift[Span] {
  def column = classOf[Span]
}

/**
 * This is the source for trace data that has been merged. Directories are like in SpanSource
 */
case class PrepNoNamesSpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[Span]("Preprocessed", dateRange)

/**
 * This is the source for trace data that has been merged and for which we've found
 * the best possible client side and service names. Directories are like in SpanSource
 */
case class PreprocessedSpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[SpanServiceName]("FindNames", dateRange)

/**
 * This is the source for data of the form (id, service name)
 */

case class PrepTsvSource()(implicit dateRange : DateRange)
  extends HourlySuffixSource("FindIDtoName", dateRange)
  with LzoTsv
  with Mappable[(Long, String)]
  with SuccessFileSource {
  override val fields = new Fields("id_1", "name_1")
  override val types : Array[Class[_]] = Array(classOf[Long], classOf[String])
  override val columnNums = (0 until types.size)
}

