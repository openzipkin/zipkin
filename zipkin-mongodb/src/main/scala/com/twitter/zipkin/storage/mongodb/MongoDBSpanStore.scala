package com.twitter.zipkin.storage.mongodb

import java.nio.ByteBuffer
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.conversions.scala._
import com.twitter.finagle.jsr166y.ForkJoinPool
import com.twitter.util._
import com.twitter.zipkin.common._
import com.twitter.zipkin.{Constants, gen}
import com.twitter.zipkin.storage.mongodb.utils.{EnsureIndexes, Index}
import com.twitter.zipkin.storage.{IndexedTraceId, SpanStore, TraceIdDuration}

private object RegisterMongoDBSerializers {

  private[this] lazy val registerSerializers = new Serializers {}.register()

  def apply(): Unit = registerSerializers
}

private class Asyncifier extends Closable{

  private[this] val pool = new ForkJoinPool()
  private[this] val closed = new AtomicBoolean(false)
  type JavaFuture[T] = java.util.concurrent.Future[T]

  def apply[U](func: => U): Future[U] = {
    if (!closed.get()) {
      val promise = Promise[U]()
      pool.execute(
        new Runnable {
          override def run(): Unit = promise.update(Try(func))
        })
      promise
    } else {
      Future.exception(new IllegalStateException("This Asyncifier is already closed"))
    }
  }

  def close(deadline: Time): Future[Unit] = {
    val promise = Promise[Unit]()
    closed.set(true)
    new Thread {
      override def run(): Unit = promise.update(
        Try(
          pool.awaitTermination(deadline.sinceNow.inNanoseconds, TimeUnit.NANOSECONDS)
        ))
    }.start()
    promise
  }
}

private[mongodb] trait MongoDBSpanStoreUtils {

  private[mongodb] def timestampsFromMongoObject(obj: MongoDBObject): Seq[Long] =
    obj.as[MongoDBList]("annotations").map((x) => new MongoDBObject(x.asInstanceOf[DBObject])).map(
      _.as[Long]("timestamp")
    )

  private[mongodb] def startTimeStampFromMongoObject(obj: MongoDBObject): Long = timestampsFromMongoObject(obj).min

  private[mongodb] def endTimeStampFromMongoObject(obj: MongoDBObject): Long = timestampsFromMongoObject(obj).max

  private[mongodb] def dbObjectToIndexedTraceId(dbobj: DBObject): IndexedTraceId = {
    val obj = new MongoDBObject(dbobj)
    IndexedTraceId(
      obj.as[Long]("traceId"),
      startTimeStampFromMongoObject(obj)
    )
  }

  private[mongodb] def toByteArray(buffer: ByteBuffer): Array[Byte] = {
    val arr = new Array[Byte](buffer.remaining())
    val copy = buffer
      .duplicate() //get() modifies the ByteBuffer position. Slice makes a shallow copy so this isn't a problem
    copy.get(arr)
    arr
  }
}

class MongoDBSpanStore(url: String, database: String, spanTTL: Duration) extends SpanStore with MongoDBSpanStoreUtils {

  RegisterMongoDBSerializers()
  private[this] val makeAsync = new Asyncifier

  private[this] val client = MongoClient(MongoClientURI(url))
  private[this] val db = client(database)
  private[this] val traces = db("traces")
  private[this] val servicesIndex = db("servicesIndex")

  EnsureIndexes(traces)(
    Index.ExpiresAt("expiresAt"),
    Index.Unique("traceId"),
    Index.Generic("spans.name"),
    Index.Generic("annotations.timestamp"),
    Index.Generic("annotations.host.service"),
    Index.Generic("binaryAnnotations.host.service"),
    Index.Generic("binaryAnnotations.key"),
    Index.Generic("binaryAnnotations.value")
  )
  EnsureIndexes(servicesIndex)(Index.ExpiresAt("expiresAt"), Index.Unique("serviceName"))

  override def getTimeToLive(traceId: Long): Future[Duration] = makeAsync {
    Time(traces.findOne(MongoDBObject("traceId" -> traceId)).get.apply("expiresAt").asInstanceOf[Date]).sinceNow
  }

  // Used for pinning
  override def setTimeToLive(traceId: Long, ttl: Duration): Future[Unit] = makeAsync {
    traces.update(
      MongoDBObject("traceId" -> traceId), MongoDBObject(
        "$set" -> MongoDBObject(
          "expiresAt" -> ttl.fromNow.toDate
        )
      ))
  }

  /**
   * Get the trace ids for this particular service and if provided, span name.
   * Only return maximum of limit trace ids from before the endTs.
   */
  override def getTraceIdsByName(
    serviceName: String,
    spanName: Option[String],
    endTs: Long,
    limit: Int): Future[Seq[IndexedTraceId]] =
    makeAsync {
      traces.find(
        MongoDBObject(
          List(
            spanName.map(
              name => List(
                "spans.name" -> name
              )).getOrElse(List()),
            List(
              "$or" -> MongoDBList(
                MongoDBObject("annotations.host.service" -> serviceName),
                MongoDBObject("binaryAnnotations.host.service" -> serviceName)
              ),
              "annotations.timestamp" -> MongoDBObject(
                "$lte" -> endTs
              )
            )
          ).flatten)).limit(limit).map(dbObjectToIndexedTraceId(_)).toSeq
    }

  private[this] def optionalGetSpansByTraceId(traceId: Long): Future[Option[Seq[Span]]] = makeAsync {
    traces.findOne(MongoDBObject("traceId" -> traceId)).map {
      (rawTrace) =>
        val trace = new MongoDBObject(rawTrace)
        val annotations = new MongoDBList(trace("annotations").asInstanceOf[BasicDBList])
          .map((x) => new MongoDBObject(x.asInstanceOf[DBObject]))
          .toSeq
        val binaryAnnotations = new MongoDBList(trace("binaryAnnotations").asInstanceOf[BasicDBList])
          .map((x) => new MongoDBObject(x.asInstanceOf[DBObject]))
          .toSeq

        def getHostOption(obj: MongoDBObject): Option[Endpoint] =
          obj.getAs[DBObject]("host").map(new MongoDBObject(_)).map(
            (host) =>
              Endpoint(
                ipv4 = host.as[Int]("ipv4"),
                port = host.as[Int]("port").toShort,
                serviceName = host.as[String]("service")
              )
          )
        val rawSpans = new MongoDBList(trace("spans").asInstanceOf[BasicDBList])
          .map((x) => new MongoDBObject(x.asInstanceOf[DBObject]))
        val spanMap = rawSpans.groupBy(_.as[Long]("id"))
        spanMap.map {
          case (spanId, spans) =>
            Span(
              traceId = traceId,
              name = spans.map(_.getAs[String]("name")).filter(_.nonEmpty).map(_.get).filter(_.nonEmpty).head,
              id = spanId,
              parentId = spans.map(_.getAs[Long]("parentId")).filter(_.nonEmpty).map(_.get).headOption,
              annotations = annotations.filter(_.as[Long]("span") == spanId).map(
                (obj) => Annotation(
                  timestamp = obj.as[Long]("timestamp"),
                  value = obj.as[String]("value"),
                  duration = obj.getAs[Long]("durationInNanoseconds").map(Duration.fromNanoseconds(_)),
                  host = getHostOption(obj)
                )).toList,
              binaryAnnotations = binaryAnnotations.filter(_.as[Long]("span") == spanId).map(
                (obj) => BinaryAnnotation(
                  key = obj.as[String]("key"),
                  value = ByteBuffer.wrap(obj.as[Array[Byte]]("value")),
                  annotationType = AnnotationType.fromInt(obj.as[Int]("kind")),
                  host = getHostOption(obj)
                )).toSeq
            )
        }.toSeq
    }
  }

  override def getSpansByTraceId(traceId: Long): Future[Seq[Span]] = optionalGetSpansByTraceId(traceId).map(_.get)

  /**
   * Get the trace ids for this annotation between the two timestamps. If value is also passed we expect
   * both the annotation key and value to be present in index for a match to be returned.
   * Only return maximum of limit trace ids from before the endTs.
   */
  override def getTraceIdsByAnnotation(
    serviceName: String, annotation: String, value: Option[ByteBuffer],
    endTs: Long, limit: Int): Future[Seq[IndexedTraceId]] =
    if (Constants.CoreAnnotations.contains(annotation)) Future(Seq())
    else makeAsync {
      val serviceClauses = MongoDBList(
        MongoDBObject("annotations.host.service" -> serviceName.toLowerCase),
        MongoDBObject("binaryAnnotations.host.service" -> serviceName.toLowerCase)
      )
      val queryParts = List(
        value.map(
          (buffer) => List(
            "binaryAnnotations" -> MongoDBObject(
              "$elemMatch" -> MongoDBObject(
                "key" -> annotation,
                "value" -> toByteArray(buffer)
              )
            )
          )
        ).getOrElse(List()),
        List(
          value match {
            case None => "$and" -> MongoDBList(
              MongoDBObject("$or" -> serviceClauses),
              MongoDBObject(
                "$or" -> MongoDBList(
                  MongoDBObject("annotations.value" -> annotation),
                  MongoDBObject("binaryAnnotations.key" -> annotation)
                ))
            )
            case Some(_) => "$or" -> serviceClauses
          },
          "annotations.timestamp" -> MongoDBObject(
            "$lte" -> endTs
          )
        )
      )
      traces.find(MongoDBObject(queryParts.flatten)).limit(limit).map(dbObjectToIndexedTraceId(_)).toSeq
    }

  override def tracesExist(traceIds: Seq[Long]): Future[Set[Long]] =
    Future.collect(
      traceIds.map(
        (traceId) =>
          makeAsync(if (traces.count(MongoDBObject("traceId" -> traceId), limit = 1) == 0) None else Some(traceId))
      )).map {
      maybeTraceIds =>
        val foundTraceIds = maybeTraceIds.collect { case Some(id) => id }
        foundTraceIds.toSet
    }

  /**
   * Get the available trace information from the storage system.
   * Spans in trace should be sorted by the first annotation timestamp
   * in that span. First event should be first in the spans list.
   *
   * The return list will contain only spans that have been found, thus
   * the return list may not match the provided list of ids.
   */
  override def getSpansByTraceIds(traceIds: Seq[Long]): Future[Seq[Seq[Span]]] =
    Future
      .collect(traceIds.map(optionalGetSpansByTraceId(_)))
      .map(_ /*Option[Seq[Span]]*/ .filter(_.nonEmpty).map(_.get))

  /**
   * Get all the span names for a particular service, as far back as the ttl allows.
   */
  override def getSpanNames(service: String): Future[Set[String]] = makeAsync {
    servicesIndex.findOne(MongoDBObject("serviceName" -> service.toLowerCase)).map(new MongoDBObject(_))
      .map(_.as[MongoDBList]("methods").map(_.asInstanceOf[String]).toSet).getOrElse(Set())
  }

  /**
   * Fetch the duration or an estimate thereof from the traces.
   * Duration returned in micro seconds.
   */
  override def getTracesDuration(traceIds: Seq[Long]): Future[Seq[TraceIdDuration]] =
    Future.collect(
      traceIds.map(
        (traceId) =>
          makeAsync {
            val trace = new MongoDBObject(traces.findOne(MongoDBObject("traceId" -> traceId)).get)
            val timestamp = startTimeStampFromMongoObject(trace)
            TraceIdDuration(
              traceId = traceId,
              duration = endTimeStampFromMongoObject(trace) - timestamp,
              startTimestamp = timestamp
            )
          }
      ).toSeq)

  /**
   * Get all the service names for as far back as the ttl allows.
   */
  override def getAllServiceNames: Future[Set[String]] = makeAsync {
    servicesIndex.find().map(new MongoDBObject(_)).map(_.as[String]("serviceName")).toSet
  }

  // store a list of spans
  override def apply(spans: Seq[Span]): Future[Unit] = Future.join(
    (
      spans map {
        (span) =>
          makeAsync {
            val hostToMongoObject: PartialFunction[Endpoint, MongoDBObject] = {
              case Endpoint(ipv4, port, service) => MongoDBObject(
                "ipv4" -> ipv4,
                "port" -> port,
                "service" -> service.toLowerCase
              )
            }
            traces.update(
              MongoDBObject("traceId" -> span.traceId),
              MongoDBObject(
                "$set" -> MongoDBObject(
                  "expiresAt" -> spanTTL.fromNow.toDate //always update TTL on update
                ),
                "$addToSet" -> MongoDBObject(
                  "spans" -> MongoDBObject(
                    "id" -> span.id,
                    "parentId" -> span.parentId,
                    "name" -> span.name
                  )
                ),
                "$pushAll" -> MongoDBObject(
                  "annotations" -> span.annotations.map {
                    case Annotation(timestamp, value, host, duration) => MongoDBObject(
                      "span" -> span.id,
                      "timestamp" -> timestamp,
                      "value" -> value,
                      "durationInNanoseconds" -> duration.map(_.inNanoseconds),
                      "host" -> host.map(hostToMongoObject)
                    )
                  },
                  "binaryAnnotations" -> span.binaryAnnotations.map {
                    case BinaryAnnotation(key, value, kind, host) => MongoDBObject(
                      "span" -> span.id,
                      "key" -> key,
                      "value" -> toByteArray(value),
                      "kind" -> kind.value,
                      "host" -> host.map(hostToMongoObject)
                    )
                  }
                )

              ),
              upsert = true
            )
            //Note: we don't really care that this isn't transactional
            span.name match {
              case "" => ()
              case name => span.serviceNames filter (_.nonEmpty) foreach {
                (serviceName) =>
                  servicesIndex.update(
                    MongoDBObject(
                      "serviceName" -> serviceName.toLowerCase
                    ),
                    MongoDBObject(
                      "$addToSet" -> MongoDBObject(
                        "methods" -> name
                      ),
                      "$set" -> MongoDBObject(
                        "expiresAt" -> spanTTL.fromNow.toDate //always update TTL on update
                      )
                    ),
                    upsert = true
                  )
              }
            }
          }
      })
      .toList) //Eagerly evaluate the map, so that all futures are immediately added to the thread pool queue, despite the
  //            implementation of Future.join

  override def close(deadline: Time): Future[Unit] = makeAsync.close(deadline).onSuccess(
    (_: Unit) => {
      client.close()
    })
}
