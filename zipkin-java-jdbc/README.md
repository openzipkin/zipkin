# zipkin-java-jdbc

## Applying the schema

```bash
# Barracuda supports compression (In AWS RDS, this must be assigned in a parameter group)
$ mysql -uroot -e "SET GLOBAL innodb_file_format=Barracuda"
# This command should work even in RDS, and return "Barracuda"
$ mysql -uroot -e "show global variables like 'innodb_file_format'"

# install the schema and indexes
$ mysql -uroot -e "create database if not exists zipkin"
$ mysql -uroot -Dzipkin < zipkin-java-jdbc/src/main/resources/mysql.sql
```

## Generating the schema types

```bash
$ rm -rf zipkin-java-jdbc/src/main/java/io/zipkin/jdbc/internal/generated/
$ ./mvnw -pl zipkin-java-jdbc clean org.jooq:jooq-codegen-maven:generate com.mycila:license-maven-plugin:format
```
