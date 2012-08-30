package com.twitter.zipkin.hadoop.config

import com.twitter.util.Config
import com.twitter.zipkin.hadoop.WorstRuntimesPerTraceClient

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 8/30/12
 * Time: 4:07 PM
 * To change this template use File | Settings | File Templates.
 */

class WorstRuntimesPerTraceClientConfig extends Config[WorstRuntimesPerTraceClient] {

  val zipkinTraceUrl = "your.zipkin.url/traces"

  def apply() = {
    new WorstRuntimesPerTraceClient(zipkinTraceUrl)
  }

}
