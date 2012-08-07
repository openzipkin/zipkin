package com.twitter.zipkin.hadoop.sources

import java.io.File

object Util {
  def isDataDir(f: File) = {
    f.getName.charAt(0) != '_'
  }

  def traverseFileTree(func: File => Unit, f: File): Unit = {
    if (isDataDir(f)) {
      if (f.isDirectory) {
        val children = f.listFiles()
        for (child <- children) {
          traverseFileTree(func, child)
        }
      } else {
        func(f)
      }
    }
  }

  def toServiceName(s: String) = {
    s.trim().replace("/", "-")
  }
}
