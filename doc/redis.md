## Redis
Redis is an alternative to the default storage backend, which is Cassandra.

### What is redis?
Redis is essentially a key-value store, where the "value" at the end of the key/value pair is actually a data structure.  Redis is in memory by default, although it can be persisted to disk, and now journals by default, which persists it to disk.

### Installing Redis
Your preferred package manager probably lets you install redis.  The piece of redis that you absolutely need is redis-server.  redis-cli will probably also come in handy, but redis-server is essential.  There is more information on how to download redis [here](http://redis.io/download).

### Using Redis
You can start redis by running redis-server, which will start an instance of redis-server that listens on port 6379.  For further configuration options, you can use a [redis.conf](https://raw.github.com/antirez/redis/2.6/redis.conf) file to configure it.

### Zipkin + Redis
If you have Redis installed and configured you can run Zipkin normally, except that you should pass "redis" as an argument to the collector and query daemons:

    bin/collector redis
    bin/query redis
    bin/web
