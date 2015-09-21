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

Example MySQL usage:
```bash
# Barracuda supports compression (In AWS RDS, this must be assigned in a parameter group)
$ mysql -uroot -e "SET GLOBAL innodb_file_format=Barracuda"
# This command should work even in RDS, and return "Barracuda"
$ mysql -uroot -e "show global variables like 'innodb_file_format'"

# install the schema and indexes
$ mysql -uroot -e "create database if not exists zipkin"
$ mysql -uroot -Dzipkin < zipkin-anormdb/src/main/resources/mysql.sql
# load test data for http://localhost:8080/dependency
$ mysql -uroot -Dzipkin < zipkin-tracegen/src/testdata/dependencies.sql
$ MYSQL_USER=root ./bin/collector mysql
```
