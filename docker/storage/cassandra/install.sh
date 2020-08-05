#!/bin/sh

#
# Copyright 2015-2020 The OpenZipkin Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

set -eu

echo "*** Temporarily installing Python and curl"
apk add --update --no-cache python2 curl

echo "*** Installing Cassandra"
# DataStax only hosts 3.0 series at the moment
curl -SL https://archive.apache.org/dist/cassandra/$CASSANDRA_VERSION/apache-cassandra-$CASSANDRA_VERSION-bin.tar.gz | tar xz
mv apache-cassandra-$CASSANDRA_VERSION/* /cassandra/

# Merge in our custom configuration
sed -i '/enable_user_defined_functions: false/cenable_user_defined_functions: true' /cassandra/conf/cassandra.yaml

# Default conf for Cassandra 3.x does not work on modern JVMs due to many deprecated flags
sed -i '/-XX:ThreadPriorityPolicy=42/c\#-XX:ThreadPriorityPolicy=42' /cassandra/conf/jvm.options
sed -i '/-XX:+UseParNewGC/c\#-XX:+UseParNewGC' /cassandra/conf/jvm.options
sed -i '/-XX:+UseConcMarkSweepGC/c\#-XX:+UseConcMarkSweepGC' /cassandra/conf/jvm.options
sed -i '/-XX:+PrintGCDateStamps/c\#-XX:+PrintGCDateStamps' /cassandra/conf/jvm.options
sed -i '/-XX:+PrintHeapAtGC/c\#-XX:+PrintHeapAtGC' /cassandra/conf/jvm.options
sed -i '/-XX:+PrintTenuringDistribution/c\#-XX:+PrintTenuringDistribution' /cassandra/conf/jvm.options
sed -i '/-XX:+PrintGCApplicationStoppedTime/c\#-XX:+PrintGCApplicationStoppedTime' /cassandra/conf/jvm.options
sed -i '/-XX:+PrintPromotionFailure/c\#-XX:+PrintPromotionFailure' /cassandra/conf/jvm.options
sed -i '/-XX:+UseGCLogFileRotation/c\#-XX:+UseGCLogFileRotation' /cassandra/conf/jvm.options
sed -i '/-XX:NumberOfGCLogFiles=10/c\#-XX:NumberOfGCLogFiles=10' /cassandra/conf/jvm.options
sed -i '/-XX:GCLogFileSize=10M/c\#-XX:GCLogFileSize=10M' /cassandra/conf/jvm.options

# TODO: Add native snappy lib. Native loader stacktraces in the cassandra log as a results, which is distracting.

# Remove bash as Cassandra scripts we use don't have it, and it isn't required
sed -i 's~#!/bin/bash~#!/bin/sh~g' /cassandra/bin/*sh

echo "*** Starting Cassandra"
/cassandra/bin/cassandra -R

timeout=300
while [[ "$timeout" -gt 0 ]] && ! /cassandra/bin/cqlsh -e 'SHOW VERSION' localhost >/dev/null 2>/dev/null; do
    echo "Waiting ${timeout} seconds for cassandra to come up"
    sleep 10
    timeout=$(($timeout - 10))
done

echo "*** Importing Scheme"
cat /zipkin-schemas/cassandra-schema.cql | /cassandra/bin/cqlsh --debug localhost
cat /zipkin-schemas/zipkin2-schema.cql | /cassandra/bin/cqlsh --debug localhost
cat /zipkin-schemas/zipkin2-schema.cql | sed 's/ zipkin2/ zipkin2_udts/g' | /cassandra/bin/cqlsh --debug localhost
cat /zipkin-schemas/zipkin2-schema-indexes.cql | /cassandra/bin/cqlsh --debug localhost

echo "*** Adding custom UDFs to zipkin2 keyspace"
/cassandra/bin/cqlsh -e "CREATE FUNCTION zipkin2.plus (x bigint, y bigint) RETURNS NULL ON NULL INPUT RETURNS bigint LANGUAGE java AS 'return x+y;';"
/cassandra/bin/cqlsh -e "CREATE FUNCTION zipkin2.minus (x bigint, y bigint) RETURNS NULL ON NULL INPUT RETURNS bigint LANGUAGE java AS 'return x-y;';"
/cassandra/bin/cqlsh -e "CREATE FUNCTION zipkin2.toTimestamp (x bigint) RETURNS NULL ON NULL INPUT RETURNS timestamp LANGUAGE java AS 'return new java.util.Date(x/1000);';"
/cassandra/bin/cqlsh -e "CREATE FUNCTION zipkin2.value (x map<text,text>, y text) RETURNS NULL ON NULL INPUT RETURNS text LANGUAGE java AS 'return x.get(y);';"

echo "*** Stopping Cassandra"
pkill -f java

echo "*** Cleaning Up"
apk del curl python2 --purge
rm -rf /cassandra/javadoc/ /cassandra/pylib/ /cassandra/tools/ /cassandra/lib/*.zip /zipkin-schemas/

echo "*** Changing to cassandra user"
adduser -S cassandra

# Take a backup so that we can safely mount an empty volume over the data directory and maintain the schema
cp -R /cassandra/data/ /cassandra/data-backup/

chown -R cassandra /cassandra

echo "*** Image build complete"
