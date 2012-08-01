package com.twitter.zipkin.hadoop.sources

object Util {

  /**
   * Given two strings, finds the minimum edit distance between them given only substitutions, deletions, and additions with the
   * Levenshtein algorithm.
   * @param s1 a String
   * @param s2 another String
   * @return the edit distance between them after they're converted to lower case.
   */
  def getLevenshteinDistance(s1 : String, s2 : String) : Int = {
    val shorter = if (s1.length() > s2.length() ) s2.toLowerCase else s1.toLowerCase
    val longer = if (s1.length() > s2.length() ) s1.toLowerCase else s2.toLowerCase

    var lastAndCurrentRow = ((0 to shorter.length() ).toArray, new Array[Int](shorter.length() + 1))
    for (i <- 1 to longer.length()) {
      val (lastRow, currentRow) = lastAndCurrentRow
      currentRow(0) = i
      for (j <- 1 to shorter.length()) {
        if (longer.charAt(i - 1) == shorter.charAt(j - 1)) {
          // they match; edit distance stays the same
          currentRow(j) = lastRow(j - 1)
        } else {
          // they differ; edit distance is the min of substitution, deletion, and addition
          currentRow(j) = math.min(math.min( lastRow(j), currentRow(j - 1) ), lastRow(j - 1)) + 1
        }
      }
      lastAndCurrentRow = lastAndCurrentRow.swap
    }
    return lastAndCurrentRow._1(shorter.length())
  }
}
