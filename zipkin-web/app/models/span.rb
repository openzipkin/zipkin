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
require 'ipaddr'

class Span
  attr_accessor :trace_id
  attr_accessor :name
  attr_accessor :id
  attr_accessor :parent_id
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
    initialize(t.trace_id, t.name, t.id, t.parent_id, t.annotations, t.binary_annotations)
  end

  def initialize(trace_id, name, id, parent_id, annotations, binary_annotations)
    @trace_id = trace_id.to_s
    @name = name
    @id = id.to_s
    @parent_id = parent_id.to_s
    @annotations = annotations
    @binary_annotations = binary_annotations
  end

  def sorted_annotations
    annotations.sort { |a,b| a.timestamp <=> b.timestamp }
  end

  def start_timestamp
    a = sorted_annotations
    a.any? ? a[0].timestamp : 0
  end

  def end_timestamp
    a = sorted_annotations
    a.any? ? a[-1].timestamp : 0
  end

  def duration_ms
    # Convert from micro seconds to milliseconds
    (end_timestamp - start_timestamp) / 1000.00
  end
  
  def service_names
    services = annotations.map do |a|
      if (a.host) 
        a.host.service_name
      else
        nil
      end
    end
    services.compact.uniq
  end    
end