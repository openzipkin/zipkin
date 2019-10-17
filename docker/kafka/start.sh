#!/busybox/sh
#
# Copyright 2015-2019 The OpenZipkin Authors
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


echo Starting Zookeeper
/busybox/sh /kafka/bin/kafka-run-class.sh -Dlog4j.configuration=file:/kafka/config/log4j.properties org.apache.zookeeper.server.quorum.QuorumPeerMain /kafka/zookeeper/conf/zoo_sample.cfg &
/busybox/sh /kafka/bin/wait-for-zookeeper.sh

if [[ -z "$KAFKA_ADVERTISED_HOST_NAME" ]]; then
listeners=PLAINTEXT://:9092
  # Have internal docker producers and consumers use the normal hostname:9092, and outside docker localhost:19092
  echo advertised.listeners=PLAINTEXT://${HOSTNAME}:9092,PLAINTEXT_HOST://localhost:19092 >> /kafka/config/server.properties
else
  # Have internal docker producers and consumers use the normal hostname:9092, and outside docker the advertised host on port 19092
  echo "advertised.listeners=PLAINTEXT://${HOSTNAME}:9092,PLAINTEXT_HOST://${KAFKA_ADVERTISED_HOST_NAME}:19092" >> /kafka/config/server.properties
fi

echo Starting Kafka
/busybox/sh /kafka/bin/kafka-run-class.sh -name kafkaServer -Dlog4j.configuration=file:/kafka/config/log4j.properties kafka.Kafka /kafka/config/server.properties
