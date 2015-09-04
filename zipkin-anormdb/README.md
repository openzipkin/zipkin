# zipkin-anormdb

AnormDB is a SQL layer for zipkin span storage.
See [sql-databases](https://github.com/openzipkin/zipkin/blob/master/doc/sql-databases.db) for details.

## Service Configuration

"dev" and "mysql" are anormdb storage backends to the following services
* [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)
* [zipkin-query-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/README.md)

Currently, only MySQL is configurable through environment variables. It uses the database `zipkin`:

    * `MYSQL_USER` and `MYSQL_PASS`: MySQL authentication, which defaults to empty string.
    * `MYSQL_HOST`: Defaults to localhost
    * `MYSQL_TCP_PORT`: Defaults to 3306

Example usage:
```bash
$ mysql -uroot -e "create database if not exists zipkin"
$ MYSQL_USER=root ./bin/collector mysql
```
