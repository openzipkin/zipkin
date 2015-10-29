package com.twitter.zipkin.sampler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.{Future, Return, Throw, Try, Witness}
import java.util.concurrent.atomic.AtomicReference

class ZkPathUpdater[T](
  zkClient: ZKClient,
  path: String,
  convert: String => T,
  validate: T => Boolean
) extends Service[Request, Response] {
  private[this] val curRate = new AtomicReference[String]("unknown")
  zkClient.watchData(path).data.map(new String(_)).changes.register(Witness(curRate))

  def apply(req: Request): Future[Response] = {
    val rep = req.response
    rep.statusCode = 200

    req.params.get("newRate") match {
      case Some(newRate) =>
        Try(convert(newRate)) filter (validate) match {
          case Return(newRate) =>
            zkClient.setData(path, newRate.toString.getBytes) map { _ =>
              rep.contentString = "Rate changed to: %s".format(newRate.toString)
              rep
            }
          case Throw(_: Try.PredicateDoesNotObtain) =>
            rep.statusCode = 500
            rep.contentString = "New rate (%s) is not valid.".format(newRate)
            Future.value(rep)

          case Throw(err) =>
            rep.statusCode = 500
            rep.contentString = "Error: %s".format(err.toString)
            Future.value(rep)
        }
      case None =>
        rep.contentString = "Current rate: %s\nChange it by setting the newRate parameter.".format(curRate.get)
        Future.value(rep)
    }
  }
}
