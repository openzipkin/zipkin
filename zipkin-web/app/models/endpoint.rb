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

class Endpoint

  attr_accessor :ipv4
  attr_accessor :port
  attr_accessor :service_name

  def self.from_thrift(*args)
    object = allocate
    object.from_thrift(*args)
    object
  end

  # from thrift struct. this is to avoid a whole slew of problems
  # with injecting new code into thrift structs
  def from_thrift(*args)
    t = args[0]
    initialize(t.ipv4, t.port, t.service_name)
  end

  def initialize(ipv4, port, service_name)
    @ipv4 = ipv4
    @port = port
    @service_name = service_name
  end

  def pretty_ip
    begin
      # java uses signed ints, we don't want negative numbers
      uip = @ipv4 < 0 ? @ipv4 + (2*2**31) : @ipv4
      IPAddr.new(uip, Socket::AF_INET).to_s
    rescue
      ipv4
    end
  end

  def pretty_port
    # java uses signed shorts, we don't want negative numbers
    self.port < 0 ? self.port + (2*2**15) : self.port
  end

  def as_json(opts={})
    {
      :pretty_ip => self.pretty_ip,
      :ipv4 => self.ipv4,
      :port => self.pretty_port
    }
  end
end
