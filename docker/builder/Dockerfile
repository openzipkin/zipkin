#
# Copyright 2015-2019 The OpenZipkin Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

FROM maven:3-jdk-11-slim

COPY . /code/

# We build the project so all its dependencies get downloaded. There doesn't seem to be a good way
# of only downloading dependencies without building too or we get unresolved dependency failures for
# the local modules, but since we don't copy any code in the compilation itself is fast.
RUN cd /code && mvn -B package -DskipTests=true && cd / && rm -rf /code
