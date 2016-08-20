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

if [ $# -eq 1 ]; then
    # After releasing a non-SNAPSHOT version
    # We need this extra logic because by the time `publish-stable.sh` is finished,
    # the current project version is (release-version + 0.0.1)-SNAPSHOT
    version="$(echo $1 | sed 's/^release-//')"
else
    # After releasing a SNAPSHOT version
    version="$(./mvnw help:evaluate -N -Dexpression=project.version|grep -v '\[')"
fi

rm -rf javadoc-builddir
builddir="javadoc-builddir/$version"

# Collect javadoc for all modules
for jar in $(find . -name "*${version}-javadoc.jar"); do
    module="$(echo "$jar" | sed "s~.*/\(.*\)-${version}-javadoc.jar~\1~")"
    this_builddir="$builddir/$module"
    mkdir -p "$this_builddir"
    unzip "$jar" -d "$this_builddir"
    # Build a simple module-level index
    echo "<li><a href=\"${module}/index.html\">${module}</a></li>" >> "${builddir}/index.html"
done

# Update the live docs
git checkout gh-pages
rm -rf "$version"
mv "javadoc-builddir/$version" ./
rm -rf "javadoc-builddir"

# Update simple version-level index
if ! grep "$version" index.html 2>/dev/null; then
    echo "<li><a href=\"${version}/index.html\">${version}</a></li>" >> index.html
fi

# Ensure the docs are in descending order of version numbers
sort -rV index.html > index.html.sorted
mv index.html.sorted index.html

# Publish
git add "$version" index.html
git commit -m "Automatically updated javadocs for $version"
git push
