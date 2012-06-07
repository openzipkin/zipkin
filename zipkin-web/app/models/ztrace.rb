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

class ZTrace

  attr_accessor :spans, :filtered_spans

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]
    initialize(t.spans.collect { |s| Span.from_thrift(s) })
  end

  def initialize(spans)
    @spans = spans
    #TODO we have a bug somewhere that sends empty spans. this is to filter those out to avoid breakages
    @filtered_spans = spans.find_all {|s| s.annotations.any?}
  end

  def start_timestamp
    start_ts = 0
    annotations.each do |a|
      if (a.timestamp < start_ts || start_ts == 0) then start_ts = a.timestamp end
    end
    start_ts
  end

  def end_timestamp
    end_ts = 0
    annotations.each do |a|
      if (a.timestamp > end_ts) then end_ts = a.timestamp end
    end
    end_ts
  end

  def annotations
    # Simple method to get all of a trace's annotations
    annotations = []
    spans.each { |s| annotations += s.annotations }
    return annotations
  end

  def trace_id
    if (!spans || !spans.any?)
      raise "No spans found in trace. Cannot determine trace id"
    end
    spans[0].trace_id
  end

  #duration in microseconds
  def duration_micro
    end_timestamp - start_timestamp
  end

  # return hash with span id -> span mapping
  def span_hash
    hash = {}
    spans.each {|s| hash[s.id] = s}
    hash
  end

  # tries to find the root span. if no span without parent_id
  # can be found, we iterate through them and find one where the
  # parent id points to a span we do not have.
  def root_span
    if (!spans.any?)
      raise "Cannot find root span since there are no spans in trace"
    end

    root = spans.select{|s| !s.parent_id }
    if (root.size != 1)
      # oh noes, no valid root exists. let's find the rootmost span and settle for that
      hash = span_hash
      span = spans[0]
      while(hash[span.parent_id]) do
        span = hash[root.parent_id]
      end
      span
    else
      root[0]
    end
  end

  #returns hash of span_id -> depth in the trace tree. starts with depth=1
  def span_depths()
    span_depth_rc(spans, 1, root_span.id)
  end

  def as_json(opts={})
    trace_start_time = self.start_timestamp
    @filtered_spans.map do |s|
      {
         :id => s.id,
         :name => s.name,
         :start_time => (s.start_timestamp - trace_start_time) / 1000.0,
         :end_time => s.end_timestamp,
         :duration => s.duration_ms,
         :service_names => s.service_names,
         :parent_id => s.parent_id,
         :trace_id => s.trace_id
      }
    end
  end

  def self.get_traces_by_ids(trace_ids, opts = {})
    Zipkin::ZipkinClient.with_transport(Rails.configuration.zookeeper) do |client|
      adjusters = [] #[Zipkin::Adjust::TIME_SKEW] #TODO config
      traces = client.getTracesByIds(trace_ids.collect { |id| id.to_i }, adjusters)
      traces.collect { |trace| ZTrace.from_thrift(trace) }
    end
  end

  # what is the default ttl we set traces to?
  def self.get_default_ttl_sec(opts = {})
    Zipkin::ZipkinClient.with_transport(Rails.configuration.zookeeper) do |client|
      client.getDataTimeToLive()
    end
  end

  def self.get_ttl(trace_id, opts = {})
    Zipkin::ZipkinClient.with_transport(Rails.configuration.zookeeper) do |client|
      client.getTraceTimeToLive(trace_id) # returns seconds
    end
  end

  def self.set_ttl(trace_id, ttl_seconds, opts = {})
    Zipkin::ZipkinClient.with_transport(Rails.configuration.zookeeper) do |client|
      client.setTraceTimeToLive(trace_id.to_i, ttl_seconds.to_i)
    end
  end

  private
  def span_depth_rc(all_spans, depth, id)
    rv = {id => depth}
    children = all_spans.select { |s| s.parent_id==id }
    children.each do |c|
      rv = rv.merge(span_depth_rc(all_spans, depth+1, c.id))
    end
    rv
  end
end