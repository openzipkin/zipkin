package com.twitter.zipkin.hadoop

import org.specs.Specification

class StandardizedServiceNameListSpec extends Specification {

  "StandardizedServiceNameList" should {
    "Add correctly if there are no similar names" in {
      var ssnm = new StandardizedServiceNameList()
      ssnm.add("hello")
      ssnm.add("aloha")
      ssnm.getName("hello") mustEqual "hello"
      ssnm.getName("aloha") mustEqual "aloha"
    }
    "Add correctly if there are exactly duplicated names" in {
      var ssnm = new StandardizedServiceNameList()
      ssnm.add("hello")
      ssnm.add("hello")
      ssnm.add("aloha")
      ssnm.getName("hello") mustEqual "hello"
      ssnm.getName("aloha") mustEqual "aloha"
    }
    "Add correctly if there are near duplicate names" in {
      var ssnm = new StandardizedServiceNameList()
      ssnm.add("hello")
      ssnm.add("alpha")
      ssnm.add("aloha")
      ssnm.add("aloha1")
      ssnm.add("hello123")
      ssnm.getName("hello") mustEqual "hello"
      ssnm.getName("hello123") mustEqual "hello123"
      ssnm.getName("aloha") mustEqual "aloha"
      ssnm.getName("alpha") mustEqual "aloha"
      ssnm.getName("aloha1") mustEqual "aloha"
    }
  }
}
