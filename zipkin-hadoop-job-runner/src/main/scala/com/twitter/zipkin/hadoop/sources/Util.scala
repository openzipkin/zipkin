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

package com.twitter.zipkin.hadoop.sources

import java.io.File

/**
 * A collection of useful methods
 */

object Util {

  val ZIPKIN_TRACE_URL = "https://zipkin.smf1.twitter.com/traces/"

  /**
   * Returns whether or not a directory will contain data. A directory contains data if its first character is not '_'
   * @param f a File
   * @return if the directory the file represents contains data
   */
  def isDataDir(f: File) = {
    f.getName.charAt(0) != '_'
  }

  /**
   * Traverses a directory and applies a function to each file in the directory
   * @param func a function which takes a File and returns a Unit
   * @param f a File representing a directory and which we want to apply func on
   */
  def traverseFileTree(f: File)(func: File => Unit): Unit = {
    if (isDataDir(f)) {
      if (f.isDirectory) {
        val children = f.listFiles()
        for (child <- children) {
          traverseFileTree(child)(func)
        }
      } else {
        func(f)
      }
    }
  }

  /**
   * Converts the string to service name in HTML format (adds the .html tail)
   * @param s a service name
   * @return the string as a service name in HTML
   */
  def toSafeHtmlName(s: String) = {
    s.trim().replace("/", "-")
  }
}
