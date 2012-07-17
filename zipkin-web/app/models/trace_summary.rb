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
require 'endpoint'

class TraceSummary

  attr_accessor :trace_id
  attr_accessor :start_timestamp
  attr_accessor :end_timestamp
  attr_accessor :duration_micro
  attr_accessor :service_counts
  attr_accessor :endpoints
  attr_accessor :url

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]
    initialize(t.trace_id, t.start_timestamp, t.end_timestamp, t.duration_micro, t.service_counts, t.endpoints)
  end

  def self.from_timeline(*args)
    object = allocate
    object.from_timeline(*args)
    object
  end

  def from_timeline(*args)
    t = args[0]
    initialize(t.trace_id, t.start_timestamp, t.end_timestamp, t.duration_micro,
      t.annotations.collect {|a| a.service_name}.uniq, t.annotations.collect {|a| a.host}.uniq)
  end

  def initialize(trace_id, start_timestamp, end_timestamp, duration_micro, service_counts, endpoints)
    @trace_id = trace_id.to_s
    @start_timestamp = start_timestamp
    @end_timestamp = end_timestamp
    @duration_micro = duration_micro
    @service_counts = service_counts
    @endpoints = endpoints
  end

  def self.get_trace_summaries_by_ids(trace_ids, adjusters, opts={})
    tmp = nil
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      ids = trace_ids.collect { |id| id.to_i }
      summaries = client.getTraceSummariesByIds(ids, adjusters)
      tmp = summaries.collect { |summary| TraceSummary.from_thrift(summary) }
    end
    tmp
  end

  def start_timestamp_ms
    # Modify start and end times to milliseconds
    Time.zone.at(start_timestamp / 1000.0)
  end

  def end_timestamp_ms
    # Modify start and end times to milliseconds
    Time.zone.at(end_timestamp / 1000.0)
  end

  def duration_ms
    # Change micro to milliseconds
    (end_timestamp - start_timestamp) / 1000.00
  end

  def get_endpoints
    # Get a formatted list of endpoints
    endpoints.nil? ? [] : endpoints.collect { |ep| "#{ep.pretty_ip}:#{ep.port}" }
  end

  def sorted_service_counts
    arr = @service_counts.to_a
    arr.sort! {|a,b| b[1] <=> a[1]}
    return arr
  end

  def set_url(url)
    @url = url
  end

  def as_json(opts={})
    {
      :start_time => Time.at(self.start_timestamp/1000000).strftime("%Y %m %d %H:%M:%S"),
      :end_time => Time.at(self.end_timestamp/1000000).strftime("%Y %m %d %H:%M:%S"),
      :duration => self.duration_ms,
      :trace_id => self.trace_id,
      :service_counts => self.sorted_service_counts,
      :url => self.url
    }
  end

end
