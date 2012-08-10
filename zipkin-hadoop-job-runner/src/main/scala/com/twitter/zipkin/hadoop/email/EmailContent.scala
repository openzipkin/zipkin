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

package com.twitter.zipkin.hadoop.email

import com.github.mustachejava._
import scala.collection.JavaConverters._
import collection.immutable.HashMap
import java.io.{FileOutputStream, PrintWriter}
import com.twitter.zipkin.hadoop.LineResult

/**
 * A basic mustache template for formatting zipkin service reports as emails
 * @param serviceName the name of a service
 */
class EmailContent(serviceName: String) {

  private var tableResults = List[TableResults]()
  private var oneLineResults = List[OneLineResults]()
  private var body = List[Body]()
  private var header = List(new Header(serviceName))
  private var document = List[Html]()

  def html() = {
    document.asJava
  }

  class Html(var header: java.util.List[Header], var body: java.util.List[Body] ) {}

  class Header(var serviceName: String) {}

  class Body(var oneLineResults: java.util.List[OneLineResults], var tableResults: java.util.List[TableResults]){}

  class OneLineResults(var result: String) {}

  class TableResults(var tableResultHeader: String, var tableHeader: TableHeader,
                     var tableRows: java.util.List[TableRow], var tableUrlRows: java.util.List[TableUrlRow]) {}

  class TableHeader(var tableHeaderTokens: java.util.List[TableHeaderToken]) {}

  class TableRow(var tableRowTokens: java.util.List[TableRowToken]) {}

  class TableHeaderToken(var tableHeaderToken: String) {}

  class TableRowToken(var tableRowToken: String) {}

  class TableUrlRow(var urlToken1: String, var urlToken2: String, var tableUrlRowTokens: java.util.List[TableUrlRowToken]) {}

  class TableUrlRowToken(var tableUrlRowToken: String) {}

  /**
   * Adds a single line format result
   * @param result a single line result
   */

  def addOneLineResult(result: String) {
    oneLineResults ::= new OneLineResults(result)
  }

  /**
   * Adds a result formatted as a table
   * @param tableResultHeader the header displayed above the table
   * @param tableHeader the header of the table
   * @param tableRows the rows of the table
   */
  def addTableResult(tableResultHeader: String, tableHeader: List[String], tableRows: List[LineResult]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowList = tableRows.map ( line => {
      val values = line.getValue().map(token => new TableRowToken(token))
      new TableRow(values.asJava)
    }).asJava
    tableResults ::= new TableResults(tableResultHeader, header, rowList, null)
  }

  /**
   * Adds a URL result formatted as a table
   * @param tableResultHeader the header displayed above the table
   * @param tableHeader the header of the table
   * @param tableUrlRows the rows of the table, where the first element is a URL
   */
  def addUrlTableResult(tableResultHeader: String, tableHeader: List[String], tableUrlRows: List[(String, String, LineResult)]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowUrlList = tableUrlRows.map ({ row =>
      val (url, hypertext, line) = row
      if (line.getValue().length < 2) {
        throw new IllegalArgumentException("Malformed line: " + line)
      }
      new TableUrlRow(url, hypertext, line.getValue().tail.map(token => new TableUrlRowToken(token)).asJava)
    }).asJava
    tableResults ::= new TableResults(tableResultHeader, header, null, rowUrlList)
  }

  /**
   * Apply all changes made to the body of the HTML document
   */
  def apply() {
    body = List(new Body(oneLineResults.asJava, tableResults.asJava))
    document = List(new Html(header.asJava, body.asJava))
  }

  /**
   * Write the formatted file to the specified PrintWriter
   * @param pw a PrintWriter
   */
  def write(pw: PrintWriter) {
    apply()
    val mf = new DefaultMustacheFactory()
    val mustache = mf.compile("email.mustache")
    mustache.execute(pw, this).flush()
  }

}

object EmailContent {

  // Ensure that we never make a different EmailContent for the same service
  private var templates = HashMap[String, EmailContent]()
  private var serviceToHtml = HashMap[String, String]()

  /**
   * Gets a EmailContent for a service. If another such template already exists, we use that one.
   * The user also specifes the name of the html file he/she wants to write to. If the template already exists,
   * we don't modify the html file.
   *
   * @param service Service name
   * @param html the html file you want to write to
   * @return
   */
  def getTemplate(service: String, html: String) = {
    if (templates.contains(service)) {
      templates(service)
    } else {
      serviceToHtml += service -> html
      val s = new EmailContent(service)
      templates += service -> s
      s
    }
  }

  /**
   * Get all the services for which we've templated
   * @return an Iterator over all the services we have templates for
   */
  def services() = templates.keys

  def writeAll() = {
    for (service <- services()) {
      val pw = new PrintWriter(new FileOutputStream(serviceToHtml(service), true))
      templates(service).write(pw)
    }
  }
}
