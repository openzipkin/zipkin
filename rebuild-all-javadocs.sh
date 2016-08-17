#!/usr/bin/env bash
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

set -euo pipefail

echo > versions
echo > download

curl 'http://search.maven.org/solrsearch/select?q=g:"io.zipkin.java"+AND+l:javadoc&wt=json&rows=713' > data.json
for url in $(cat data.json | jq '.response.docs | map("http://search.maven.org/remotecontent?filepath=io/zipkin/java/" + .a + "/" + .v + "/" + .a + "-" + .v + "-javadoc.jar") | join("\n")' -r); do
    module=$(echo $url | sed -e 's~.*io/zipkin/java/\([^/]*\)/.*~\1~')
    version=$(echo $url | sed -e "s~.*${module}/\([0-9.]*\)/.*~\1~")
    echo "$version" >> versions
    echo "$url -O $module-$version-javadoc.jar" >> download
done

cat download | xargs -n3 -P30 wget -L -c

for version in $(uniq versions | sort -rV); do
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

  # Update gh-pages
  rm -rf "$version"
  mv "javadoc-builddir/$version" ./
  rm -rf "javadoc-builddir"

  # Update simple version-level index
  if ! grep "$version" index.html 2>/dev/null; then
    echo "<li><a href=\"${version}/index.html\">${version}</a></li>" >> index.html
  fi
done

cat versions | xargs git add
git add index.html

rm *.jar
rm data.json
rm download
rm versions

git commit -m 'Re-published all javadocs of io.zipkin.java'
