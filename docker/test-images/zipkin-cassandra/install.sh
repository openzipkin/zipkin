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

# install script used only in building the docker image, but not at runtime.
# This uses relative path so that you can change the home dir without editing this file.
# This also trims dependencies to only those used at runtime.
set -eux

echo "*** Installing Cassandra"

cat > pom.xml <<-'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.zipkin.cassandra</groupId>
  <artifactId>get-cassandra</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <dependencies>
    <dependency>
      <groupId>org.apache.cassandra</groupId>
      <artifactId>cassandra-all</artifactId>
      <version>${cassandra.version}</version>
      <exclusions>
        <exclusion>
          <groupId>org.slf4j</groupId>
          <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
          <groupId>ch.qos.logback</groupId>
          <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
          <groupId>net.java.dev.jna</groupId>
          <artifactId>*</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>net.java.dev.jna</groupId>
      <artifactId>jna</artifactId>
      <version>5.6.0</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>1.7.30</version>
    </dependency>
  </dependencies>
</project>
EOF
mvn -q --batch-mode -DoutputDirectory=libs \
    -Dcassandra.version=${CASSANDRA_VERSION} \
    org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy-dependencies
rm pom.xml

# Make sure you use relative paths in references like this, so that installation
# is decoupled from runtime
mkdir -p conf data commitlog saved_caches hints triggers

# Generate basic required configuration from default values
cat > conf/cassandra.yaml <<-'EOF'
partitioner: org.apache.cassandra.dht.Murmur3Partitioner
commitlog_sync: periodic
commitlog_sync_period_in_ms: 10000
endpoint_snitch: SimpleSnitch

# override via -Dcassandra.storage_port=7000
storage_port: 7000
# override via -Dcassandra.native_transport_port=9042
native_transport_port: 9042
listen_address: 127.0.0.1
rpc_address: 127.0.0.1
start_native_transport: true
seed_provider:
    - class_name: org.apache.cassandra.locator.SimpleSeedProvider
      parameters:
          - seeds: "127.0.0.1"
EOF

# Avoid conflicts during multi-arch build (buildx starting two archs in the same container)
ARCH=$(uname -m)
case ${ARCH} in
  aarch64* )
    TEMP_STORAGE_PORT=7020
    TEMP_NATIVE_TRANSPORT_PORT=9062
    ;;
  * )
    TEMP_STORAGE_PORT=7010
    TEMP_NATIVE_TRANSPORT_PORT=9052
    ;;
esac

# Keep INFO logs as if this fails in CI, we'll get more insight. These aren't displayed unless we
# have a crash.
cat > conf/log4j.properties <<-'EOF'
log4j.rootLogger=INFO, stderr

log4j.appender.stderr=org.apache.log4j.ConsoleAppender
log4j.appender.stderr.layout=org.apache.log4j.PatternLayout
log4j.appender.stderr.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.appender.stderr.Target=System.err
EOF

# Run cassandra on a different port temporarily in order to setup the schema.
java -cp 'libs/*' -Xms64m -Xmx64m -XX:+ExitOnOutOfMemoryError -verbose:gc \
  -Dcassandra.storage_port=${TEMP_STORAGE_PORT} \
  -Dcassandra.native_transport_port=${TEMP_NATIVE_TRANSPORT_PORT} \
  -Dcassandra.storagedir=${PWD} \
  -Dcassandra.triggers_dir=${PWD}/triggers \
  -Dcassandra.config=file:${PWD}/conf/cassandra.yaml \
  -Dlog4j.configuration=file:${PWD}/conf/log4j.properties \
  org.apache.cassandra.service.CassandraDaemon > temp_cassandra.out 2>&1 &
TEMP_CASSANDRA_PID=$!

function is_cassandra_alive() {
  if ! kill -0 ${TEMP_CASSANDRA_PID}; then
    cat temp_cassandra.out
    maybe_crash_file=hs_err_pid${TEMP_CASSANDRA_PID}.log
    test -f $maybe_crash_file && cat $maybe_crash_file
    return 1
  fi
  return 0
}

is_cassandra_alive || exit 1

echo "*** Installing cqlsh"
apk add --update --no-cache python2 py2-setuptools
python2 -m easy_install pip
pip install -Iq cqlsh
function cql() {
  cqlsh --cqlversion=3.4.4 "$@" 127.0.0.1 ${TEMP_NATIVE_TRANSPORT_PORT}
}

# Excessively long timeout to avoid having to create an ENV variable, decide its name, etc.
timeout=180
echo "Will wait up to ${timeout} seconds for Cassandra to come up before installing Schema"
while [ "$timeout" -gt 0 ] && ! cql -e 'SHOW VERSION' > /dev/null 2>&1; do
    is_cassandra_alive || exit 1
    sleep 1
    timeout=$(($timeout - 1))
done

echo "*** Importing Scheme"
cat zipkin-schemas/cassandra-schema.cql | cql --debug
cat zipkin-schemas/zipkin2-schema.cql | cql --debug
cat zipkin-schemas/zipkin2-schema-indexes.cql | cql --debug

echo "*** Stopping Cassandra"
kill ${TEMP_CASSANDRA_PID}

# The image will use a less chatty Log4J conf which only logs warnings (to stderr).
cat > conf/log4j.properties <<-'EOF'
log4j.rootLogger=WARN, stderr

log4j.appender.stderr=org.apache.log4j.ConsoleAppender
log4j.appender.stderr.layout=org.apache.log4j.PatternLayout
log4j.appender.stderr.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.appender.stderr.Target=System.err

# Ignore that we are using log4j as we aren't starting JMX anyway
log4j.logger.org.apache.cassandra.utils.logging=ERROR
# Ignore that we cannot increase RLIMIT_MEMLOCK or run Cassandra as root
log4j.logger.org.apache.cassandra.utils.NativeLibrary=ERROR
# Ignore that we disabled JMX and haven't installed jemalloc (not available on Alpine)
log4j.logger.org.apache.cassandra.service.StartupChecks=OFF
# Ignore warnings about less than 64GB disk
log4j.logger.org.apache.cassandra.config.DatabaseDescriptor=ERROR
EOF

# Take a backup so that we can safely mount an empty volume over the data directory and maintain the schema
cp -R data/ data-backup/

echo "*** Cleaning Up"
rm -rf zipkin-schemas/ temp_cassandra.out

echo "*** Image build complete"
