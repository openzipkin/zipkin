# zipkin-aggregate
 
This module is a Hadoop job that will collect spans from your datastore (only Cassandra is supported yet),
analyse them aggregate the data and store it for later presentation in the web UI.
 
## Running locally:
 
```bash
# to connect to a cassandra on localhost:9160
./gradlew :zipkin-aggregate:run
# to specify a different cassandra cluster
./gradlew :zipkin-aggregate:run -Phosts=cassandra1.example.com,cassandra2.example.com,cassandra3.example.com -Pport=9160
```
 
## Building a fat jar and submitting the a Hadoop job scheduler
```
./gradlew :zipkin-aggregate:build
```
This will build a fat jar `zipkin-aggregate/build/libs/zipkin-aggregate-XXX-all.jar`.
Upload the jar to a job tracker and start it with arguments:
 
```
hadoop jar zipkin-aggregate-XXX-all.jar --source cassandra --hosts cassandra1.example.com,cassandra2.example.com,cassandra3.example.com --port 9160
```
