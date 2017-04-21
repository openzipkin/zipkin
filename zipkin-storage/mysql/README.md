# storage-mysql

This MySQL storage component includes a blocking `SpanStore` and span consumer function.
`SpanStore.getDependencies()` aggregates dependency links on-demand.

The implementation uses JOOQ to generate MySQL SQL commands. It is only tested on MySQL 5.6-5.7.

See the [schema DDL](src/main/resources/mysql.sql).

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

If you are trying to debug from data in the database, it is helpful to
format IDs as hex, and timestamps as dates. The following is an example
query which will return one line for each update to a span in the last
5 minutes.

```sql
SELECT lower(concat(CASE trace_id_high
                        WHEN '0' THEN ''
                        ELSE hex(trace_id_high)
                    END,hex(trace_id))) AS trace_id,
       lower(hex(parent_id)) as parent_id,
       lower(hex(id)) as span_id,
       name,
       from_unixtime(start_ts/1000000) as timestamp
FROM zipkin_spans
where (start_ts/1000000) > UNIX_TIMESTAMP(now()) - 5 * 60;
```

For example, the output below shows two traces recently reported. One of
which is using 128-bit trace IDs. You could copy and paste the `trace_id`
into zipkin's UI to troubleshoot further.
```
+----------------------------------+------------------+------------------+------+--------------------------+
| trace_id                         | parent_id        | span_id          | name | timestamp                |
+----------------------------------+------------------+------------------+------+--------------------------+
| abbd9f5da49e5848aa4b729ff2bc90a3 | NULL             | aa4b729ff2bc90a3 | get  | 2017-04-19 12:43:00.9830 |
| abbd9f5da49e5848aa4b729ff2bc90a3 | aa4b729ff2bc90a3 | 7888a4aef81f074d | get  | 2017-04-19 12:43:01.1960 |
| 11b98d7107dac980                 | 11b98d7107dac980 | bc33c2d5ad25bf89 | get  | 2017-04-19 12:42:45.4240 |
| 11b98d7107dac980                 | NULL             | 11b98d7107dac980 | get  | 2017-04-19 12:42:45.0680 |
+----------------------------------+------------------+------------------+------+--------------------------+
```

## Applying the schema

```bash
# Barracuda supports compression (In AWS RDS, this must be assigned in a parameter group)
$ mysql -uroot -e "SET GLOBAL innodb_file_format=Barracuda"
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
