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

set -eux

echo "*** Installing Kafka and dependencies"
apk add --update --no-cache jq curl

APACHE_MIRROR=$(curl --stderr /dev/null https://www.apache.org/dyn/closer.cgi\?as_json\=1 | jq -r '.preferred')

# download kafka binaries
curl -sSL $APACHE_MIRROR/kafka/$KAFKA_VERSION/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz | tar xz
mv kafka_$SCALA_VERSION-$KAFKA_VERSION/* .

# Remove bash as our images doesn't have it, and it isn't required
sed -i 's~#!/bin/bash~#!/bin/sh~g' /kafka/bin/*sh

# Set explicit, basic configuration
cat > config/server.properties <<-EOF
broker.id=0
zookeeper.connect=127.0.0.1:2181
replica.socket.timeout.ms=1500
log.dirs=/kafka/logs
auto.create.topics.enable=true
offsets.topic.replication.factor=1
listeners=PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:19092
listener.security.protocol.map=PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
EOF

mkdir /kafka/logs

echo "*** Image build complete"
