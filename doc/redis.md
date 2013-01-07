## Redis
Redis is an alternative to the default storage backend, which is Cassandra.

### What is redis?
Redis is essentially a key-value store, where the "value" at the end of the key/value pair is actually a data structure.  Redis is in memory by default, although it can be persisted to disk, and now journals by default, which persists it to disk.

### Installing Redis
Your preferred package manager probably lets you install redis.  The piece of redis that you absolutely need is redis-server.  redis-cli will probably also come in handy, but redis-server is essential.  There is more information on how to download redis [here](http://redis.io/download).

### Using Redis
You can start redis by running redis-server, which will start an instance of redis-server that listens on port 6379.  For further configuration options, you can use a [redis.conf](https://raw.github.com/antirez/redis/2.6/redis.conf) file to configure it.

### Zipkin + Redis
There are a few configuration changes that must be made before you can use zipkin-redis in your zipkin project.

#### Config Changes
Go into zipkin/zipkin-collector-service/config/collector-dev.scala, and replace the lines which say:
```scala
  def storeBuilder = Store.Builder(
    cassandra.StorageBuilder(keyspaceBuilder),
    cassandra.IndexBuilder(keyspaceBuilder),
    cassandra.AggregatesBuilder(keyspaceBuilder)
  )
```

with

```scala
  def storeBuilder = Store.Builder(
    redis.StorageBuilder("0.0.0.0", 6379),
    redis.IndexBuilder("0.0.0.0", 6379)
  )
```

You'll also need to add an import
```scala
  import com.twitter.zipkin.redis
```
Then do the same in zipkin/zipkin-query-service/config/query-dev.scala.  Host and port should be the host and port your redis-server is listening on.

#### SBT Changes
Open up your project/Project.scala, and in the .dependsOn arguments list for collectorService and queryService, append redis to the end of the arguments list.

Then, run your zipkin instance normally!
