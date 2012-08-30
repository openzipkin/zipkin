#!/usr/bin/env ruby
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

require 'rubygems'
require 'zipkin-query'
require 'thrift_client'
require 'finagle-thrift'
require 'zookeeper'
require 'optparse'
require 'ostruct'
require 'set'

class OptparseHasSampledArguments

  #
  # Return a structure describing the options.
  #
  def self.parse(args)
    # The options specified on the command line will be collected in *options*.
    # We set default values here.
    options = OpenStruct.new
    options.input = nil
    options.output = nil
    options.preprocessor = false

    opts = OptionParser.new do |opts|
      opts.banner = "Usage: run_job.rb -i INPUT -o OUTPUT"

      opts.separator ""
      opts.separator "Specific options:"

      opts.on("-i", "--input INPUT",
              "The INPUT file to read from") do |input|
        options.input = input
      end

      opts.on("-o", "--output OUTPUT",
              "The OUTPUT file to write to") do |output|
        options.output = output
      end

      opts.separator ""
      opts.separator "Common options:"      
      opts.on_tail("-h", "--help", "Show this message") do
        puts opts
        exit
      end
    end
    opts.parse!(args)
    options
  end
end

# Queries the client to ask whether the trace is stored
def sampled_traces(trace_ids)
  result = false
  traces = nil
  ZipkinQuery::Client.with_transport($config) do |client|
    traces = client.tracesExist(trace_ids)
  end
  return traces
end

# Gets trace id from input line
def get_trace_id(line)
  return line.split("\t")[1].to_i
end

# Reads the input from the first file, and then outputs the lines from the input which are sampled to the outputfile
def sample(inputfile, outputfile)
  File.open(outputfile, 'w') do |out_file|
    trace_list = []
    File.open(inputfile, 'r').each do |line|
      trace_list = trace_list << get_trace_id(line)
    end
    sampled = sampled_traces(trace_list)
    File.open(inputfile, 'r').each do |line|
      if (sampled.include?(get_trace_id(line)))
        out_file.print line
      end
    end  
  end
end

if __FILE__ == $0
  options = OptparseHasSampledArguments.parse(ARGV)
  sample(options.input, options.output)
end
