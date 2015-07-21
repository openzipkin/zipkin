package io.zipkin.sbt

import java.io.{BufferedReader, FileReader, FileWriter, File}
import sbt._

object FileFilter {
  /**
   * Perform configure-style `@key@` substitution on a file as it's being copied.
   */
  def filter(source: File, destination: File, filters: Map[String, String]) {
    val in = new BufferedReader(new FileReader(source))
    val out = new FileWriter(destination)
    var line = in.readLine()
    while (line ne null) {
      filters.keys.foreach { token =>
        line = line.replace("@" + token + "@", filters(token))
      }
      out.write(line)
      out.write("\n")
      line = in.readLine()
    }
    in.close()
    out.close()
  }
}
