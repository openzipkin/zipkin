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
import com.twitter.zipkin.hadoop.LineResult
import com.twitter.zipkin.hadoop.sources.Util
import java.io.{File, StringWriter, FileOutputStream, PrintWriter}
import java.util.Scanner

/**
 * A basic mustache template for formatting zipkin service reports as emails
 * @param standardServiceName the name of a service
 */
class EmailContent(standardServiceName: String) {

  private var results = Map[String, (List[OneLineResults], List[TableResults])]()
  private var header = List(new Header(standardServiceName))
  private var document = List[Html]()

  def html() = {
    document.asJava
  }

  case class Html(var header: java.util.List[Header], var services: java.util.List[Service] ) {}

  case class Header(var standardServiceName: String) {}

  case class Service(var serviceName: String, var oneLineResults: java.util.List[OneLineResults], var tableResults: java.util.List[TableResults]){}

  case class OneLineResults(var result: String) {}

  case class TableResults(var tableResultHeader: String, var tableHeader: TableHeader,
                     var tableRows: java.util.List[TableRow], var tableUrlRows: java.util.List[TableUrlRow]) {}

  case class TableHeader(var tableHeaderTokens: java.util.List[TableHeaderToken]) {}

  case class TableRow(var tableRowTokens: java.util.List[TableRowToken]) {}

  case class TableHeaderToken(var tableHeaderToken: String) {}

  case class TableRowToken(var tableRowToken: String) {}

  case class TableUrlRow(var urlToken1: String, var urlToken2: String, var tableUrlRowTokens: java.util.List[TableUrlRowToken]) {}

  case class TableUrlRowToken(var tableUrlRowToken: String) {}

  def getResult(service: String) = {
    if (!results.contains(service)) {
      results += service -> (Nil, Nil)
    }
    results(service)
  }

  /**
   * Adds a single line format result
   * @param result a single line result
   */

  def addOneLineResult(service: String, result: String) {
    val (oneliners, tablers) = getResult(service)
    results += service -> ((new OneLineResults(result))::oneliners, tablers)
  }

  /**
   * Adds a result formatted as a table
   * @param tableResultHeader the header displayed above the table
   * @param tableHeader the header of the table
   * @param tableRows the rows of the table
   */
  def addTableResult(service: String, tableResultHeader: String, tableHeader: List[String], tableRows: List[LineResult]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowList = tableRows.map ( line => {
      val values = line.getValue().map(token => new TableRowToken(token))
      new TableRow(values.asJava)
    }).asJava
    val (oneliners, tablers) = getResult(service)
    results += service -> (oneliners, (new TableResults(tableResultHeader, header, rowList, null))::tablers)
  }

  /**
   * Adds a URL result formatted as a table
   * @param tableResultHeader the header displayed above the table
   * @param tableHeader the header of the table
   * @param tableUrlRows the rows of the table, where the first element is a URL
   */
  def addUrlTableResult(service: String, tableResultHeader: String, tableHeader: List[String], tableUrlRows: List[(String, String, LineResult)]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowUrlList = tableUrlRows.map ({ row =>
      val (url, hypertext, line) = row
      if (line.getValue().length < 2) {
        throw new IllegalArgumentException("Malformed line: " + line)
      }
      new TableUrlRow(url, hypertext, line.getValue().tail.map(token => new TableUrlRowToken(token)).asJava)
    }).asJava
    val (oneliners, tablers) = getResult(service)
    val newTablers = (new TableResults(tableResultHeader, header, null, rowUrlList))::tablers
    results += service -> (oneliners, newTablers)
  }

  /**
   * Apply all changes made to the body of the HTML document
   */
  def apply() {
    var body = List[Service]()
    for (result <- results) {
      val (service, (oneliners, tablers)) = result
      body ::= new Service(service, oneliners.asJava, tablers.asJava)
    }
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
  private var templates = Map[String, EmailContent]()
  var serviceNames = Map[String, String]()
  private var emailAddresses = Map[String, List[String]]()
  private var outputDir = ""

  def populateServiceNames(dirname: String) = {
    Util.traverseFileTree(new File(dirname))({f: File =>
      val s = new Scanner(f)
      while (s.hasNextLine()) {
        val line = new Scanner(s.nextLine())
        val name = line.next()
        val standardized = line.next()
        serviceNames += name -> standardized
        emailAddresses += standardized -> Nil
        while (line.hasNext()) {
          emailAddresses += standardized -> (line.next()::emailAddresses(standardized))
        }
      }
    })
  }

  def getServiceName(service: String) = {
    val toLowerCase = service.toLowerCase
    if (serviceNames.contains(toLowerCase)) {
      serviceNames(toLowerCase)
    } else {
      toLowerCase
    }
  }

  def getEmailAddress(stdService: String) = {
    if (emailAddresses.contains(stdService)) {
      Some(emailAddresses(stdService))
    } else {
      None
    }
  }

  /**
   * Gets a EmailContent for a service. If another such template already exists, we use that one.
   *
   * @param service Service name
   * @return
   */
  def getTemplate(service: String) = {
    if (templates.contains(getServiceName(service))) {
      templates(getServiceName(service))
    } else {
      val s = new EmailContent(getServiceName(service))
      templates += getServiceName(service) -> s
      s
    }
  }

  /**
   * Get all the services for which we've templated
   * @return an Iterator over all the services we have templates for
   */
  def services() = templates.keys

  def setOutputDir(output: String) = {
    outputDir = output
  }

  /**
   * Writes all the email contents to files
   */
  def writeAll() = {
    for (service <- services()) {
      if (templates.contains(service)) {
        val pw = new PrintWriter(new FileOutputStream(outputDir + "/" + Util.toSafeHtmlName(service), true))
        templates(service).write(pw)
      }
    }
  }

  /**
   * Writes all the email contents to Strings
   * @return a Map from service name to the email contents as a String
   */
  def writeAllAsStrings() = {
    var serviceToEmail = Map[String, String]()
    for (service <- services()) {
      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      if (templates.contains(service))  {
        templates(service).write(pw)
        serviceToEmail += service -> sw.toString()
      }
    }
    serviceToEmail
  }
}
