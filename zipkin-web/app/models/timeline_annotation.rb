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

class TimelineAnnotation

  attr_accessor :timestamp
  attr_accessor :value
  attr_accessor :host
  attr_accessor :span_id
  attr_accessor :parent_id
  attr_accessor :service_name
  attr_accessor :span_name

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]
    initialize(t.timestamp, t.value, Endpoint.from_thrift(t.host), t.span_id, t.parent_id, t.service_name, t.span_name)
  end

  def initialize(timestamp, value, host, span_id, parent_id, service_name, span_name)
    @timestamp = timestamp
    @value = value
    @host = host
    @span_id = span_id.to_s
    @parent_id = parent_id.to_s
    @service_name = service_name
    @span_name = span_name
  end


  def pretty_value
    case self.value
    when "cs"
      "Client send"
    when "cr"
      "Client receive"
    when "ss"
      "Server send"
    when "sr"
      "Server receive"
    else self.value
    end
  end

end