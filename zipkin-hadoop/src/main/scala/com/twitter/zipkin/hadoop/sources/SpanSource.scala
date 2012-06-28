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


trait LzoThrift[T <: TBase[_, _]] extends Mappable[T] {
  def column: Class[_]

  // TODO this won't actually work locally, but we need something here for the tests
  override def localScheme = new CLTextLine()

  override def hdfsScheme = HadoopSchemeInstance(new LzoThriftScheme[T](column))
}

/**
 * This is the source for trace data. Directories are like so: /logs/zipkin/yyyy/mm/dd/hh
 */
case class SpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[Span]("/logs/zipkin/", dateRange)

/**
 * This is the source for trace data that has been merged. Directories are like in SpanSource
 */
case class PrepNoMergeSpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[Span]("test", dateRange)

/**
 * This is the source for trace data that has been merged and for which we've found
 * the best possible client side and service names. Directories are like in SpanSource
 */
case class PreprocessedSpanSource(implicit dateRange: DateRange) extends HourlySuffixLzoThrift[SpanServiceName]("testagain", dateRange)

