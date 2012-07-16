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

# methods for fetching the trace ids the user is interested in from the index
class Index

  ORDER_DEFAULT = Zipkin::Order::NONE

  def self.get_trace_ids_by_span_name(service_name, span_name, end_ts, limit, opts = {})
    end_ts = secondsToMicroseconds(end_ts)
    order = opts[:order] || ORDER_DEFAULT

    tmp = nil
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      tmp = client.getTraceIdsBySpanName(service_name, span_name, end_ts, limit, order)
    end
    tmp
  end

  def self.get_trace_ids_by_service_name(service_name, end_ts, limit, opts = {})
    end_ts = secondsToMicroseconds(end_ts)
    order = opts[:order] || ORDER_DEFAULT

    tmp = nil
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      tmp = client.getTraceIdsByServiceName(service_name, end_ts, limit, order)
    end
    tmp
  end

  def self.get_trace_ids_by_annotation(service_name, annotation, value, end_ts, limit, opts = {})
    end_ts = secondsToMicroseconds(end_ts)
    order = opts[:order] || ORDER_DEFAULT

    tmp = nil
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      tmp = client.getTraceIdsByAnnotation(service_name, annotation, value, end_ts, limit, order)
    end
    tmp
  end

  private
  def self.secondsToMicroseconds(end_time)
    # Need to be in microseconds. Convert from seconds
    return end_time.to_i * 1000 * 1000
  end

end
