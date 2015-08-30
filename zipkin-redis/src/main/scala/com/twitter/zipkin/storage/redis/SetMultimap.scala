package com.twitter.zipkin.storage.redis

import com.google.common.base.Charsets._
import com.twitter.finagle.redis.Client
import com.twitter.util.{Duration, Future}
import org.jboss.netty.buffer.ChannelBuffers._

/**
 * Allows you to associate one or more string values with a string key.
 *
 * @param client the redis client to use
 * @param ttl expires keys older than this many seconds.
 * @param prefix prefix of the namespace/redis hash key
 */
class SetMultimap(
  val client: Client,
  val ttl: Option[Duration],
  prefix: String
) extends ExpirationSupport {

  private[this] def encodeKey(key: String) = copiedBuffer("%s:%s".format(prefix, key), UTF_8)

  /** Adds a value to the given key. */
  def put(key: String, value: String): Future[Unit] = {
    val redisKey = encodeKey(key)
    client.sAdd(redisKey, List(copiedBuffer(value, UTF_8)))
      .flatMap(_ => expireOnTtl(redisKey))
  }

  /** Returns all values for the given key. */
  def get(key: String): Future[Set[String]] =
    client.sMembers(encodeKey(key))
      .map(members => members.map(_.toString(UTF_8)))
}
