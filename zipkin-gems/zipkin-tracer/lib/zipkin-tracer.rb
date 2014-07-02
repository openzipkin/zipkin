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
require 'finagle-thrift'
require 'finagle-thrift/trace'
require 'scribe'

require 'zipkin-tracer/careless_scribe'

module ZipkinTracer extend self

  class RackHandler
    B3_REQUIRED_HEADERS = %w[HTTP_X_B3_TRACEID HTTP_X_B3_PARENTSPANID HTTP_X_B3_SPANID HTTP_X_B3_SAMPLED]
    B3_HEADERS = B3_REQUIRED_HEADERS + %w[HTTP_X_B3_FLAGS]

    def initialize(app, config=nil)
      @app = app
      @lock = Mutex.new

      config ||= app.config.zipkin_tracer # if not specified, try on app (e.g. Rails 3+)
      @service_name = config[:service_name]
      @service_port = config[:service_port]

      scribe =
        if config[:scribe_server] then
          Scribe.new(config[:scribe_server])
        else
          Scribe.new()
        end

      scribe_max_buffer =
        if config[:scribe_max_buffer] then
          config[:scribe_max_buffer]
        else
          10
        end

      @sample_rate =
        if config[:sample_rate] then
          config[:sample_rate]
        else
          0.1
        end

      ::Trace.tracer = ::Trace::ZipkinTracer.new(CarelessScribe.new(scribe), scribe_max_buffer)
    end

    def call(env)
      ::Trace.default_endpoint = ::Trace.default_endpoint.with_service_name(@service_name).with_port(@service_port)
      ::Trace.sample_rate=(@sample_rate)
      id = get_or_create_trace_id(env) # note that this depends on the sample rate being set
      tracing_filter(id, env) { @app.call(env) }
    end

    private
    def tracing_filter(trace_id, env)
      @lock.synchronize do
        ::Trace.push(trace_id)
        ::Trace.set_rpc_name(env["REQUEST_METHOD"]) # get/post and all that jazz
        ::Trace.record(::Trace::BinaryAnnotation.new("http.uri", env["PATH_INFO"], "STRING", ::Trace.default_endpoint))
        ::Trace.record(::Trace::Annotation.new(::Trace::Annotation::SERVER_RECV, ::Trace.default_endpoint))
      end
      yield if block_given?
    ensure
      @lock.synchronize do
        ::Trace.record(::Trace::Annotation.new(::Trace::Annotation::SERVER_SEND, ::Trace.default_endpoint))
        ::Trace.pop
      end
    end

    private
    def get_or_create_trace_id(env, default_flags = ::Trace::Flags::EMPTY)
      trace_parameters = if B3_REQUIRED_HEADERS.all? { |key| env.has_key?(key) }
                           env.values_at(*B3_HEADERS)
                         else
                           new_id = Trace.generate_id
                           [new_id, nil, new_id, ("true" if Trace.should_sample?), default_flags]
                         end
      trace_parameters[3] = (trace_parameters[3] == "true")
      trace_parameters[4] = (trace_parameters[4] || default_flags).to_i

      Trace::TraceId.new(*trace_parameters)
    end
  end

end
