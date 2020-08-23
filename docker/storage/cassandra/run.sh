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

set -e

# If the schema has been removed due to mounting, restore from our backup. see: install
if [ ! -d "/cassandra/data/data/zipkin2" ]; then
    cp -rf /cassandra/data-backup/* /cassandra/data/
fi

_ip_address() {
	# scrape the first non-localhost IP address of the container
	ip address | awk '
		$1 == "inet" && $NF != "lo" {
			gsub(/\/.+$/, "", $2)
			print $2
			exit
		}
	'
}

IP="$(_ip_address)"
sed -i "/listen_address: localhost/clisten_address: $IP" /cassandra/conf/cassandra.yaml
sed -i '/rpc_address: localhost/crpc_address: 0.0.0.0' /cassandra/conf/cassandra.yaml
sed -i "/# broadcast_address: 1.2.3.4/cbroadcast_address: $IP" /cassandra/conf/cassandra.yaml
sed -i "/# broadcast_rpc_address: 1.2.3.4/cbroadcast_rpc_address: $IP" /cassandra/conf/cassandra.yaml
sed -i "/          - seeds: \"127.0.0.1\"/c\          - seeds: $IP" /cassandra/conf/cassandra.yaml

exec /cassandra/bin/cassandra -f
