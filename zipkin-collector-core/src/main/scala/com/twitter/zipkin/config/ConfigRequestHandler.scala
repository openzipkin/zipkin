package com.twitter.zipkin.config

import com.sun.net.httpserver.HttpExchange
import com.twitter.ostrich.admin.CgiRequestHandler
import com.twitter.zipkin.config.sampler.AdjustableRateConfig

class ConfigRequestHandler(
  adjustable: AdjustableRateConfig
) extends CgiRequestHandler {
  def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)]) {
    if (path.length != 2) {
      render("invalid command", exchange, 404)
    }

    val paramMap = Map(parameters:_*)

    exchange.getRequestMethod match {
      case "GET" =>
        render(adjustable.get.toString, exchange, 200)
      case "POST" =>
        paramMap.get("value") match {
          case Some(value) =>
            try {
              adjustable.set(value.toDouble)
              render("success", exchange, 200)
            } catch {
              case e =>
                render("invalid input", exchange, 500)
            }
          case None =>
            render("invalid command", exchange, 404)
        }
    }
  }
}
