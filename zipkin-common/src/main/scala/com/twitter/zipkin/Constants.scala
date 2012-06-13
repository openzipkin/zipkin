package com.twitter.zipkin

object Constants {
  val ClientSend: String = "cs"
  val ClientRecv: String = "cr"
  val ServerSend: String = "ss"
  val ServerRecv: String = "sr"

  val CoreClient: Seq[String] = Seq(ClientSend, ClientRecv)
  val CoreServer: Seq[String] = Seq(ServerRecv, ServerSend)

  val CoreAnnotations: Seq[String] = CoreClient ++ CoreServer
}