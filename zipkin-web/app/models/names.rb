# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

require 'ipaddr'

class Names

  def self.get_service_names
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      client.getServiceNames().sort
    end
  end

  def self.get_span_names(service_name)
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      client.getSpanNames(service_name).sort
    end
  end

end