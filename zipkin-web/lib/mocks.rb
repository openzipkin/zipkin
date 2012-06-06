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

require 'zipkin_types'

module Mocks

  class ZipkinQuery

    def getServiceNames
      results = []
      ( 0..(rand * 10).floor ).each do |count|
        results << "service_#{count}"
      end

      return results
    end

    def getSpanNames(service_name)
      results = []
      ( 0..(rand * 10).floor ).each do |count|
        results << "span_#{count}"
      end

      return results
    end

    def getTraceById(trace_id)
      spans = []

      ( 0..(rand * 10).floor ).each do |count|

        span = Zipkin::Span.new(
          :trace_id => trace_id,
          :service_name => "foo",
          :name => "bar",
          :id => count,
          :parent_id => 0,
          :annotations => []
        )

        spans << span

        ( 0..(rand * 5).floor ).each do |a_count|

          span.annotations << Zipkin::Annotation.new(
            :timestamp => a_count,
            :value => "value_#{a_count}",
            :host => Zipkin::Endpoint.new(:ipv4 => 100 + a_count, :port => a_count )
          )

        end
      end

      Zipkin::Trace.new( :spans => spans )
    end

    def getTraceSummaryById(trace_id)
      # Get a trace summary object by name

      o = Zipkin::TraceSummary.new(
        :trace_id => 123,
        :start_timestamp => 456,
        :end_timestamp => 789,
        :duration_micro => 456,
        :service_names => [],
        :endpoints => []
      )

      ( 0..(rand * 10).floor ).each do |sn|
         o.service_names << "service_#{sn}"
      end

      # Create some random endpoints
      ( 0..(rand * 5).floor ).each do |ep|
        o.endpoints << Zipkin::Endpoint.new( :ipv4 => 100 + ep, :port => ep )
      end

      return o
    end

    def getTraceSummariesByName(service_name, span_name, start_ts, end_ts, limit)

      limit ||= 20

      results = []

      ( 0..(rand * limit).floor ).each do |num|

        o = Zipkin::TraceSummary.new(
          :trace_id => num,
          :start_timestamp => num,
          :end_timestamp => num,
          :duration_micro => num,
          :service_names => [],
          :endpoints => []
        )

        ( 0..(rand * 10).floor ).each do |sn|
           o.service_names << "service_#{sn}"
        end

        # Create some random endpoints
        ( 0..(rand * 5).floor ).each do |ep|
          o.endpoints << Zipkin::Endpoint.new( :ipv4 => 100 + ep, :port => ep )
        end

        results << o
      end

      results
    end

  end

end