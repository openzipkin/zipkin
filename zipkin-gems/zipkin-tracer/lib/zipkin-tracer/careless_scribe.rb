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

class CarelessScribe
  def initialize(scribe)
    @scribe = scribe
  end

  def log(*args)
    @scribe.log(*args)
  rescue ThriftClient::NoServersAvailable, Thrift::Exception
    # I couldn't care less
  end

  def batch(&block)
    @scribe.batch(&block)
  rescue ThriftClient::NoServersAvailable, Thrift::Exception
    # I couldn't care less
  end

  def method_missing(name, *args)
    @scribe.send(name, *args)
  end
end