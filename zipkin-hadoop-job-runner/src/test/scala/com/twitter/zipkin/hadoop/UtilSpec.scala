package com.twitter.zipkin.hadoop

import com.twitter.zipkin.hadoop.sources.Util
import org.specs.Specification

class UtilSpec extends Specification {

  "Util.getLevenshteinDistance" should {
    "get correct edit distance when a string is empty" in {
      Util.getLevenshteinDistance("whee", "") must be_==(4)
      Util.getLevenshteinDistance("", "pie") must be_==(3)
    }
    "get correct edit distance when deletions are necessary" in {
      Util.getLevenshteinDistance("hi", "h") must be_==(1)
      Util.getLevenshteinDistance("hihi", "hh") must be_==(2)
    }
    "get correct edit distance when substitutions are necessary" in {
      Util.getLevenshteinDistance("hi", "ho") must be_==(1)
      Util.getLevenshteinDistance("hihi", "heha") must be_==(2)
    }
    "get correct edit distance when additions are necessary" in {
      Util.getLevenshteinDistance("h", "ho") must be_==(1)
      Util.getLevenshteinDistance("hh", "heha") must be_==(2)
    }
    "get correct edit distance in general case" in {
      Util.getLevenshteinDistance("aloha", "BoCa") must be_==(3)
      Util.getLevenshteinDistance("all", "lK") must be_==(2)
    }
  }
}
