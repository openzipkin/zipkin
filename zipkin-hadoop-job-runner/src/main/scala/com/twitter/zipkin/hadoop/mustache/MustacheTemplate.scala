package com.twitter.zipkin.hadoop.mustache

import com.github.mustachejava._
import scala.collection.JavaConverters._
import java.io.PrintWriter
import collection.immutable.HashMap

class MustacheTemplate(serviceName: String) {

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

  def addOneLineResult(result: String) {
    oneLineResults ::= new OneLineResults(result)
  }

  def addTableResult(tableResultHeader: String, tableHeader: List[String], tableRows: List[List[String]]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowList = tableRows.map ( row => new TableRow(row.map(s => new TableRowToken((s))).asJava) ).asJava
    tableResults ::= new TableResults(tableResultHeader, header, rowList, null)
  }

  def addUrlTableResult(tableResultHeader: String, tableHeader: List[String], tableRows: List[List[String]]) {
    val header = new TableHeader(tableHeader.map(s => new TableHeaderToken(s)).asJava)
    val rowUrlList = tableRows.map ( row => new TableUrlRow(row(0), row(0), row.tail.map(s => new TableUrlRowToken((s))).asJava) ).asJava
    tableResults ::= new TableResults(tableResultHeader, header, null, rowUrlList)
  }

  def apply() {
    body = List(new Body(oneLineResults.asJava, tableResults.asJava))
    document = List(new Html(header.asJava, body.asJava))
  }

  def write(pw: PrintWriter) {
    apply()
    val mf = new DefaultMustacheFactory()
    val mustache = mf.compile("template.mustache")
    mustache.execute(pw, this).flush()
  }

}

object MustacheTemplate {

  private var templates = HashMap[String, MustacheTemplate]()

  def getTemplate(service: String) = {
    if (templates.contains(service)) {
      templates(service)
    } else {
      val s = new MustacheTemplate(service)
      templates += service -> s
      s
    }
  }

  def services() = templates.keys
}
