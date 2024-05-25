#!/bin/sh
#
# Copyright The OpenZipkin Authors
# SPDX-License-Identifier: Apache-2.0
#

# install script used only in building the docker image, but not at runtime.
# This uses relative path so that you can change the home dir without editing this file.
# This also trims dependencies to only those used at runtime.
set -eux

echo "*** Installing Cassandra"

# Create directories for the Java classpath
mkdir classes lib

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
          <groupId>net.java.dev.jna</groupId>
          <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.slf4j</groupId>
          <artifactId>*</artifactId>
        </exclusion>
        <exclusion>
          <groupId>ch.qos.logback</groupId>
          <artifactId>*</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <!-- Use latest to support alpine and additional architecture -->
    <dependency>
      <groupId>net.java.dev.jna</groupId>
      <artifactId>jna</artifactId>
      <version>5.14.0</version>
    </dependency>
    <!-- log4j not logback -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>1.7.36</version>
    </dependency>
  </dependencies>
</project>
EOF
mvn -q --batch-mode -DoutputDirectory=lib \
    -Dcassandra.version=${CASSANDRA_VERSION} \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies
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
start_native_transport: true
seed_provider:
    - class_name: org.apache.cassandra.locator.SimpleSeedProvider
      parameters:
          - seeds: "127.0.0.1"

# Disabled by default in Cassandra 4
enable_sasi_indexes: true
EOF

temp_storage_port=7010
temp_native_transport_port=9052

# Keep INFO logs as if this fails in CI, we'll get more insight. These aren't displayed unless we
# have a crash.
cat > conf/log4j.properties <<-'EOF'
log4j.rootLogger=INFO, stdout

log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.appender.stdout.Target=System.out
EOF

cat zipkin-schemas/zipkin2-schema.cql zipkin-schemas/zipkin2-schema-indexes.cql > schema

# read_repair_chance options were removed and make Cassandra crash starting in v4
# See https://cassandra.apache.org/doc/latest/operating/read_repair.html#background-read-repair
sed -i '/read_repair_chance/d' schema

# Run cassandra on a different port temporarily in order to setup the schema.
#
# We also add exports and opens from both Cassandra v4 and v5, except for
# attach, compiler and rmi because our JRE excludes these modules.
#
# Merging makes adding Cassandra v5 easier and lets us share a common JRE 17+
# with other test images even if Cassandra v4 will never officially support it.
# https://github.com/apache/cassandra/blob/cassandra-4.0.11/conf/jvm11-server.options
# https://github.com/apache/cassandra/blob/cassandra-5.0/conf/jvm17-server.options
#
# Finally, we allow security manager to prevent JRE 21 crashing when Cassandra
# attempts ThreadAwareSecurityManager.install()
java -cp 'classes:lib/*' -Xms64m -Xmx64m -XX:+ExitOnOutOfMemoryError -verbose:gc \
  -Djava.security.manager=allow \
  -Djdk.attach.allowAttachSelf=true \
  --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-exports java.base/sun.nio.ch=ALL-UNNAMED \
  --add-exports java.sql/java.sql=ALL-UNNAMED \
  --add-exports java.base/java.lang.ref=ALL-UNNAMED \
  --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED \
  --add-opens java.base/java.lang.module=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.loader=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.reflect=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.math=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.module=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.util.jar=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  -Dcassandra.storage_port=${temp_storage_port} \
  -Dcassandra.native_transport_port=${temp_native_transport_port} \
  -Dcassandra.storagedir=${PWD} \
  -Dcassandra.triggers_dir=${PWD}/triggers \
  -Dcassandra.config=file:${PWD}/conf/cassandra.yaml \
  -Dlog4j.configuration=file:${PWD}/conf/log4j.properties \
  org.apache.cassandra.service.CassandraDaemon > temp_cassandra.out 2>&1 &
temp_cassandra_pid=$!

is_cassandra_alive() {
  if ! kill -0 ${temp_cassandra_pid}; then
    cat temp_cassandra.out
    maybe_crash_file=hs_err_pid${temp_cassandra_pid}.log
    test -f $maybe_crash_file && cat $maybe_crash_file
    return 1
  fi
  return 0
}

is_cassandra_alive || exit 1

echo "*** Installing cqlsh"
# cqlsh 4.x is not compatible with Python 3.12 by default.
# See https://issues.apache.org/jira/browse/CASSANDRA-19206
python3_version=3.12
apk add --update --no-cache python3=~${python3_version}
# Installing cqlsh requires cffi package. Normally this doesn't need
# to be compiled, but something isn't right with aarch64 when installing
# cqlsh it needs to build cffi. To unblock support for aarch64, adding
# the following are necessary for compiling cffi. If pip someday changes and
# doesn't compile cffi on aarch64 then we can remove these dependencies.
# libev is required when using python 3.12
# TODO: remove git when https://github.com/jeffwidman/cqlsh/pull/37 is released
apk add --update --no-cache gcc python3-dev=~${python3_version} musl-dev libffi-dev libev libev-dev git
# PEP 668 protects against mixing system and pip packages. Setup virtual env to avoid this.
python3 -m venv .venv
. .venv/bin/activate
python3 -m ensurepip --upgrade
# TODO: just cqlsh when https://github.com/jeffwidman/cqlsh/pull/37 is released
pip install -Iq git+https://github.com/jeffwidman/cqlsh@master
cql() {
  cqlsh "$@" 127.0.0.1 ${temp_native_transport_port}
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
cat schema | cql --debug && rm schema

echo "*** Stopping Cassandra"
kill ${temp_cassandra_pid}
wait

# The image will use a less chatty Log4J conf which only logs warnings (to stdout).
cat > conf/log4j.properties <<-'EOF'
log4j.rootLogger=WARN, stdout

log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.appender.stdout.Target=System.out

# Ignore that we are using log4j as we aren't starting JMX anyway
log4j.logger.org.apache.cassandra.utils.logging=ERROR
# Ignore that we cannot increase RLIMIT_MEMLOCK or run Cassandra as root
log4j.logger.org.apache.cassandra.utils.NativeLibrary=ERROR
# Ignore that we disabled JMX and haven't installed jemalloc (not available on Alpine)
log4j.logger.org.apache.cassandra.service.StartupChecks=OFF
# Ignore C* 3.x java.lang.NoSuchMethodError: 'sun.misc.Cleaner sun.nio.ch.DirectBuffer.cleaner()'
log4j.logger.org.apache.cassandra.io.util.FileUtils=OFF
# Ignore warnings about less than 64GB disk
log4j.logger.org.apache.cassandra.config.DatabaseDescriptor=ERROR
EOF

# Take a backup so that we can safely mount an empty volume over the data directory and maintain the schema
cp -R data/ data-backup/

echo "*** Cleaning Up"
rm -rf zipkin-schemas/ temp_cassandra.out

echo "*** Image build complete"
