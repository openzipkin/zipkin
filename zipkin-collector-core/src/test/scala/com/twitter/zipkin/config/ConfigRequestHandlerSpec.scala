package com.twitter.zipkin.config

import com.sun.net.httpserver.HttpExchange
import com.twitter.ostrich.admin.CustomHttpHandler
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

/**
 * Test endpoints for getting and setting configurations for sample rate and storage request rate
 */
class ConfigRequestHandlerSpec extends FunSuite with Matchers with MockitoSugar {

  val sampleRateConfig = mock[AdjustableRateConfig]
  val exchange = mock[HttpExchange]
  val customHttpHandler = mock[CustomHttpHandler]

  val handler = new ConfigRequestHandler(sampleRateConfig) {
    override def render(body: String, exchange: HttpExchange, code: Int) {
      customHttpHandler.render(body, exchange, code)
    }
  }

  test("sampleRate get") {
    when(exchange.getRequestMethod) thenReturn "GET"
    when(sampleRateConfig.get) thenReturn 0.5

    handler.handle(exchange, List("config", "sampleRate"), List.empty[(String, String)])

    verify(customHttpHandler).render("0.5", exchange, 200)
  }

  test("sampleRate set") {
    when(exchange.getRequestMethod) thenReturn "POST"

    handler.handle(exchange, List("config", "sampleRate"), List(("value", "0.3")))

    verify(sampleRateConfig).set(0.3)
    verify(customHttpHandler).render("success", exchange, 200)
  }
}
