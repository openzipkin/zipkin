# zipkin-aggregate
 
This module is a Hadoop job that will collect spans from your datastore (only Cassandra is supported yet),
analyse them aggregate the data and store it for later presentation in the web UI.
 
## Running locally:
 
```
sbt
project zipkin-aggregate
run --source cassandra --hosts cassandra1.example.com,cassandra2.example.com,cassandra3.example.com --port 9160
```
 
## Building a fat jar and submitting the a Hadoop job scheduler
```
sbt
project zipkin-aggregate
assembly
```
This will build a fat jar `zipkin-aggregate-assembly-XXX.jar`.
Upload the jar to a job tracker and start it with arguments:
 
```
hadoop jar zipkin-aggregate-assembly-XXX.jar --source cassandra --hosts cassandra1.example.com,cassandra2.example.com,cassandra3.example.com --port 9160
```
