# zipkin-anormdb

AnormDB is a SQL layer for zipkin span storage.

## Service Configuration

"dev" and "mysql" are anormdb storage backends to the following services
* [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)
* [zipkin-query-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/README.md)

Currently, only MySQL is configurable through environment variables:

    * `MYSQL_DB`: The database to use. Defaults to "zipkin".
    * `MYSQL_USER` and `MYSQL_PASS`: MySQL authentication, which defaults to empty string.
    * `MYSQL_HOST`: Defaults to localhost
    * `MYSQL_TCP_PORT`: Defaults to 3306
    * `MYSQL_MAX_CONNECTIONS`: Maximum concurrent connections, defaults to 10
    * `MYSQL_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

Example MySQL usage:
```bash
# Barracuda supports compression (In AWS RDS, this must be assigned in a parameter group)
$ mysql -uroot -e "SET GLOBAL innodb_file_format=Barracuda"
# This command should work even in RDS, and return "Barracuda"
$ mysql -uroot -e "show global variables like 'innodb_file_format'"

# install the schema and indexes
$ mysql -uroot -e "create database if not exists zipkin"
$ mysql -uroot -Dzipkin < zipkin-anormdb/src/main/resources/mysql.sql
$ MYSQL_USER=root ./bin/query mysql
```

## Troubleshooting

### Looking up raw data by trace ID 

Zipkin's api, http header encoding and web urls represent ids as hex strings. Data in SQL tables is keyed by the same
id, except in numeric form, in the `zipkin_spans` and `zipkin_annotations` tables. 

Let's say you are trying to debug a trace that looks wrong in the UI `http://zipkinweb/traces/ee3ba633f8cb4ad3`. In this
case, the trace ID is `ee3ba633f8cb4ad3`.

First, look at the raw json as that might help: `http://zipkinquery:9411/api/v1/trace/ee3ba633f8cb4ad3`.

If you are still unable to diagnose the problem, you can explore the data in SQL using the same id:
```SQL
mysql> select * from zipkin_spans where trace_id = CAST(X'ee3ba633f8cb4ad3' AS SIGNED);
mysql> select * from zipkin_annotations where trace_id = CAST(X'ee3ba633f8cb4ad3' AS SIGNED);
```

