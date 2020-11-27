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

echo "*** Installing Kafka and dependencies"
# Create directories for the Java classpath
mkdir classes lib

# Dist includes large dependencies needed by streams and connect: retain only broker and ZK.
# We can do this because broker is independent from both kafka-streams and connect modules.
# See KAFKA-10380
#
# TODO: MDEP-723 if addressed can remove the pom.xml here
cat > pom.xml <<-'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.zipkin.kafka</groupId>
  <artifactId>get-kafka</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <dependencies>
    <dependency>
      <groupId>org.apache.kafka</groupId>
      <artifactId>kafka_${scala.version}</artifactId>
      <version>${kafka.version}</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-log4j12</artifactId>
      <version>1.7.30</version>
    </dependency>
  </dependencies>
</project>
EOF
mvn -q --batch-mode -DoutputDirectory=lib \
    -Dscala.version=${SCALA_VERSION} -Dkafka.version=${KAFKA_VERSION} \
    org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy-dependencies
rm pom.xml

# Make sure you use relative paths in references like this, so that installation
# is decoupled from runtime
mkdir -p bin config data/kafka data/zookeeper

# Make a basic log4j config which only logs warnings (to stdout)
#
# NOTE: Two unavoidable log WARN messages remain:
# 1. Either no config or no quorum defined in config, running  in standalone mode (org.apache.zookeeper.server.quorum.QuorumPeerMain)
#   * https://github.com/apache/zookeeper/blob/e91455c1e3c50405666cd8afad71d99dceb7b340/zookeeper-server/src/main/java/org/apache/zookeeper/server/quorum/QuorumPeerMain.java#L138-L140
# 2. No meta.properties file under dir /kafka/./data/kafka/meta.properties (kafka.server.BrokerMetadataCheckpoint)
#   * meta.properties file is generated when broker joins the cluster, using an auto-generated cluster id:
cat > config/log4j.properties <<-'EOF'
log4j.rootLogger=WARN, stdout

log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=[%d] %p %m (%c)%n
log4j.appender.stdout.Target=System.out
EOF

# Set explicit, basic configuration
cat > config/zookeeper.properties <<-'EOF'
dataDir=./data/zookeeper
clientPort=2181
maxClientCnxns=0
admin.enableServer=false
# allow ruok command for testing ZK health
4lw.commands.whitelist=srvr,ruok
admin.enableServer=false
EOF

cat > config/server.properties <<-'EOF'
broker.id=0
zookeeper.connect=127.0.0.1:2181
replica.socket.timeout.ms=1500
# log.dirs is about Kafka's data not Log4J
log.dirs=./data/kafka
auto.create.topics.enable=true
offsets.topic.replication.factor=1
listeners=PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:19092
listener.security.protocol.map=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
EOF

# Make a basic script for launching Kafka commands
cat > bin/kafka-run-class.sh <<-'EOF'
#!/bin/sh
set -eu
# classes allows layers to patch the image without packaging or overwriting jars
exec java -cp 'classes:lib/*' ${JAVA_OPTS} \
  -Djava.io.tmpdir=/tmp \
  -Dlog4j.configuration=file:./config/log4j.properties \
  "$@"
EOF
chmod 755 bin/kafka-run-class.sh

echo "*** Image build complete"
