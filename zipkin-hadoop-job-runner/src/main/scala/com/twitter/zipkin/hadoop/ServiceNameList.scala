package com.twitter.zipkin.hadoop

import collection.mutable.{HashMap, HashSet}
import com.twitter.zipkin.hadoop.sources.Util
import java.util.{ArrayList}
import scala.math.Ordering.String

class ServiceNameList() {

  protected var serviceNames = new ArrayList[String]()
  protected var serviceNamesSet = new HashSet[String]()

  def add(name: String) {
    if (!serviceNamesSet.contains(name)) {
      serviceNames.add(name)
      serviceNamesSet += name
    }
  }

  def getName(s: String) : String = s

  def getAllNames() = new ArrayList[String](serviceNames)

}

class StandardizedServiceNameList() extends ServiceNameList {

  val DISTANCE = 2

  val originalToStandardizedIndex = new HashMap[String, Int]()

  override def add(name: String) {
    val trimmed = name.trim()
    if (originalToStandardizedIndex.contains(trimmed)) return
    var foundMatch = false
    for (index <- 0 until serviceNames.size()) {
      val key = serviceNames.get(index)
      // If these are actually just near spellings of the same service
      if (Util.getLevenshteinDistance(key.toLowerCase, trimmed.toLowerCase) <= DISTANCE) {
        foundMatch = true
        if (String.lteq(trimmed, key)) {
          serviceNames.set(index, trimmed)
        }
        originalToStandardizedIndex += trimmed -> index
      }
    }
    if (!foundMatch) {
      serviceNames.add(trimmed)
      originalToStandardizedIndex += trimmed -> (serviceNames.size() - 1)
    }
  }

  override def getName(s: String) = {
    if (!originalToStandardizedIndex.contains(s)) {
      throw new IllegalArgumentException("Input not in map: " + s)
    }
    serviceNames.get(originalToStandardizedIndex(s))
  }

}