#!/bin/bash
#
# Copyright 2015-2016 The OpenZipkin Authors
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

set -xeuo pipefail

# Read filename arguments, passed in by CircleCI. This is our integration with the
# runtime-based balancing of tests across parallel test executors that CircleCI does.
tests="$(echo "$@" | xargs basename -s .java | tr '\n' ',' | sed -e 's/,$//')"
./mvnw -Dtest="$tests" -DfailIfNoTests=false test
