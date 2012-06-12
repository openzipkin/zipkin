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

class TraceTimeline

  attr_accessor :trace_id
  attr_accessor :root_most_span_id
  attr_accessor :annotations
  attr_accessor :binary_annotations

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]

    initialize(t.trace_id, t.root_most_span_id,
      t.annotations.collect { |annotation| TimelineAnnotation.from_thrift(annotation) }, t.binary_annotations)
  end

  def initialize(trace_id, root_most_span_id, annotations, binary_annotations)
    @trace_id = trace_id.to_s
    @root_most_span_id = root_most_span_id.to_s
    @annotations = annotations
    @binary_annotations = binary_annotations
  end

  def self.get_trace_timelines_by_ids(trace_ids, opts = {})
    ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      adjusters = [] # [Zipkin::Adjust::TIME_SKEW] #TODO config
      ids = trace_ids.collect { |id| id.to_i }
      timelines = client.getTraceTimelinesByIds(ids, adjusters)
      timelines.collect { |timeline| TraceTimeline.from_thrift(timeline) }
    end
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

  #duration in microseconds
  def duration_micro
    end_timestamp - start_timestamp
  end

  # convert into array of annotations suitable for display
  def get_annotation_rows
    annotation_rows = []

    first = annotations.first.timestamp

    annotations.each do |a|
      if a.pretty_value.downcase.index("/(error)|(exception)/")
        label = "label-red"
      elsif ["Client send", "Client receive", "Server send", "Server receive"].any? {|s| s == a.pretty_value}
        label = "label-grey"
      else
        label = "label-green"
      end
      annotation_rows << {
        :timestamp  => a.timestamp,
        :delta      => ((a.timestamp - first) / 1000.00).round(1),
        :annotation => a.pretty_value,
        :host       => a.host,
        :port       => a.host.pretty_port.to_s,
        :service    => a.service_name,
        :name       => a.span_name,
        :hostname   => a.host.pretty_ip, #TODO hostname lookups took too long, using ip for now
        :id         => a.span_id.to_s,
        :label      => label
      }
    end
    annotation_rows
  end

  # get all binary annotations for this span
  def get_binary_annotations
    kv_annotation_rows = []
    binary_annotations.each do |ba|
      kv_annotation_rows << {
        :key => ba.key,
        :value => ba.value
      }
    end
    kv_annotation_rows
  end

  def as_json(opts={})
    #TODO error handling if no annotations
    depths = opts[:depths]
    first_ts = annotations[0].timestamp
    annotations.map do |a|
      {
        :port => a.host.port,
        :ipv4 => a.host.ipv4,
        #:pretty_ip => self.end_time,
        :value => a.value,
        :span_id => a.span_id,
        :depth => depths[a.span_id],
        :timestamp => a.timestamp,
        :time_relative => (a.timestamp - first_ts) / 1000.0
      }
    end
  end
end