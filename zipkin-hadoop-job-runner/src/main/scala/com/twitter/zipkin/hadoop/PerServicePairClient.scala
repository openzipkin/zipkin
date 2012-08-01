package com.twitter.zipkin.hadoop

import java.util.Scanner
import com.twitter.zipkin.gen

abstract class PerServicePairClient(combineSimilarNames: Boolean, serverName: String, portNumber: Int) extends
  WriteToServerClient(combineSimilarNames, portNumber) {

  def populateSsnm(s: Scanner) {
    if (!combineSimilarNames) return
    while (s.hasNextLine()) {
      val line = new Scanner(s.nextLine())
      ssnm.add(line.next() + DELIMITER + line.next())
    }
  }

  def getKeyValue(lineScanner: Scanner) = {
    lineScanner.next() + DELIMITER + lineScanner.next()
  }
}