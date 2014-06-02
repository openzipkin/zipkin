package com.twitter.zipkin.config

import com.sun.net.httpserver.HttpExchange
import com.twitter.ostrich.admin.CustomHttpHandler
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}
import com.twitter.zipkin.config.sampler.AdjustableRateConfig

/**
 * Test endpoints for getting and setting configurations for sample rate and storage request rate
 */
class ConfigRequestHandlerSpec extends Specification with JMocker with ClassMocker {
  "ConfigRequestHandler" should {

    val sampleRateConfig = mock[AdjustableRateConfig]
    val exchange = mock[HttpExchange]
    val customHttpHandler = mock[CustomHttpHandler]

    val handler = new ConfigRequestHandler(sampleRateConfig) {
      override def render(body: String, exchange: HttpExchange, code: Int) {
        customHttpHandler.render(body, exchange, code)
      }
    }

    "sampleRate" in {
      "get" in {
        expect {
          one(exchange).getRequestMethod willReturn "GET"
          one(sampleRateConfig).get willReturn 0.5
          one(customHttpHandler).render("0.5", exchange, 200)
        }

        handler.handle(exchange, List("config", "sampleRate"), List.empty[(String, String)])
      }

      "set" in {
        expect {
          one(exchange).getRequestMethod willReturn "POST"
          one(sampleRateConfig).set(0.3)
          one(customHttpHandler).render("success", exchange, 200)
        }

        handler.handle(exchange, List("config", "sampleRate"), List(("value", "0.3")))
      }
    }
  }
}