# zipkin-redis

## Service Configuration

"redis" is a storage backend to the following services
* [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)
* [zipkin-query-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/README.md)

Redis is configurable through environment variables. It uses the database `zipkin`:

   * `REDIS_PASSWORD`: Optional
   * `REDIS_HOST`: Defaults to 0.0.0.0
   * `REDIS_PORT`: Defaults to 6379

Example usage:
```bash
# in one terminal, install and start redis
$ redis-server
# in another terminal, start the query service (optionally a collector)
$ REDIS_PASSWORD=secret ./bin/query redis
```
