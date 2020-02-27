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

set -xeuo pipefail

if ! curl --retry 5 --retry-connrefused --retry-delay 0 -sf http://grafana:3000/api/datasources/name/prom; then
    curl -sf -X POST -H "Content-Type: application/json" \
         --data-binary '{"name":"prom","type":"prometheus","url":"http://prometheus:9090","access":"proxy","isDefault":true}' \
         http://grafana:3000/api/datasources
fi

dashboard_id=1598
last_revision=$(curl -sf https://grafana.com/api/dashboards/${dashboard_id}/revisions | grep '"revision":' | sed 's/ *"revision": \([0-9]*\),/\1/' | sort -n | tail -1)

echo '{"dashboard": ' > data.json
curl -s https://grafana.com/api/dashboards/${dashboard_id}/revisions/${last_revision}/download >> data.json
echo ', "inputs": [{"name": "DS_PROMETHEUS", "pluginId": "prometheus", "type": "datasource", "value": "prom"}], "overwrite": false}' >> data.json
curl --retry-connrefused --retry 5 --retry-delay 0 -sf \
     -X POST -H "Content-Type: application/json" \
     --data-binary @data.json \
     http://grafana:3000/api/dashboards/import
