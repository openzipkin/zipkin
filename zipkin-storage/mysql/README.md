# storage-mysql

This MySQL storage component includes a blocking `SpanStore` and span consumer function.
`SpanStore.getDependencies()` aggregates dependency links on-demand.

The implementation uses JOOQ to generate MySQL SQL commands. It is only tested on MySQL 5.6-5.7.

The schema is the same as [zipkin-scala](https://github.com/openzipkin/zipkin/tree/master/zipkin-anormdb).

`zipkin.storage.mysql.MySQLStorage.Builder` includes defaults that will
operate against a given Datasource.

## Testing this component
This module conditionally runs integration tests against a local MySQL instance.

You minimally need to export the variable `MYSQL_USER` to run tests.
Ex.
```
$ MYSQL_USER=root ./mvnw clean install -pl :zipkin-storage-mysql
```

If you run tests via Maven or otherwise without specifying `MYSQL_USER`,
you'll notice tests are silently skipped.
```
Results :

Tests run: 49, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.

## Exploring Zipkin Data

When troubleshooting, it is important to note that zipkin ids are encoded as hex.
If you want to view data in mysql, you'll need to use the hex function accordingly. 

For example, all the below query the same trace using different tools:
* zipkin-ui: `http://1.2.3.4:9411/traces/27960dafb1ea7454`
* zipkin-api: `http://1.2.3.4:9411/api/v1/trace/27960dafb1ea7454?raw`
* mysql: `select * from zipkin_spans where trace_id = x'27960dafb1ea7454';`

## Applying the schema

```bash
# Barracuda supports compression (In AWS RDS, this must be assigned in a parameter group)
$ mysql -uroot -e "SET GLOBAL innodb_file_format=Barracuda"
# If using MySQL 5.7, you'll need to disable ONLY_FULL_GROUP_BY
$ mysql -uroot -e "SET GLOBAL sql_mode=(SELECT REPLACE(@@sql_mode,'ONLY_FULL_GROUP_BY',''))"
# This command should work even in RDS, and return "Barracuda"
$ mysql -uroot -e "show global variables like 'innodb_file_format'"

# install the schema and indexes
$ mysql -uroot -e "create database if not exists zipkin"
$ mysql -uroot -Dzipkin < zipkin-storage/mysql/src/main/resources/mysql.sql
```

## Generating the schema types

```bash
$ rm -rf zipkin-storage/mysql/src/main/java/zipkin/storage/mysql/internal/generated/
$ ./mvnw -pl :zipkin-storage-mysql clean org.jooq:jooq-codegen-maven:generate com.mycila:license-maven-plugin:format
```
