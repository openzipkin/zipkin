# spanstore-jdbc

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
$ mysql -uroot -Dzipkin < zipkin-spanstores/jdbc/src/main/resources/mysql.sql
```

## Generating the schema types

```bash
$ rm -rf zipkin-spanstores/jdbc/src/main/java/zipkin/jdbc/internal/generated/
$ ./mvnw -pl :spanstore-jdbc clean org.jooq:jooq-codegen-maven:generate com.mycila:license-maven-plugin:format
```
