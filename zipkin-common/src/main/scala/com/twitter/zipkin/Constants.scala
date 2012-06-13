package com.twitter.zipkin

object Constants {
  val CLIENT_SEND: String = "cs"
  val CLIENT_RECV: String = "cr"
  val SERVER_SEND: String = "ss"
  val SERVER_RECV: String = "sr"

  val CoreClient: Seq[String] = Seq(CLIENT_SEND, CLIENT_RECV)
  val CoreServer: Seq[String] = Seq(SERVER_RECV, SERVER_SEND)

  val CoreAnnotations: Seq[String] = CoreClient ++ CoreServer
}