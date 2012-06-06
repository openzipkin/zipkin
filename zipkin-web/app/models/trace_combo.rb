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

require 'zipkin_client'
require 'zipkin_types'
require 'ipaddr'

class TraceCombo

  attr_accessor :trace
  attr_accessor :summary
  attr_accessor :timeline
  attr_accessor :span_depths

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]
    initialize(ZTrace.from_thrift(t.trace), TraceSummary.from_thrift(t.summary), TraceTimeline.from_thrift(t.timeline), t.span_depths)
  end

  def initialize(trace, summary, timeline, span_depths)
    @trace = trace
    @summary = summary
    @timeline = timeline
    @span_depths = span_depths
  end

  def self.get_trace_combos_by_ids(trace_ids, adjusters, opts = {})
    ZipkinClient.with_transport(Rails.configuration.zookeeper) do |client|
      combos = client.getTraceCombosByIds(trace_ids.collect { |id| id.to_i }, adjusters)
      combos.collect { |combo| TraceCombo.from_thrift(combo) }
    end
  end

end