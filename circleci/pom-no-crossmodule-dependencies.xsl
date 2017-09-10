<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015-2017 The OpenZipkin Authors

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
    in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the License
    is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
    or implied. See the License for the specific language governing permissions and limitations under
    the License.

-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:pom="http://maven.apache.org/POM/4.0.0">
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="pom:dependency[pom:groupId = 'io.zipkin.java']" />
  <xsl:template match="pom:dependency[pom:groupId = 'io.zipkin.zipkin2']" />
  <xsl:template match="pom:dependency[pom:groupId = '${project.groupId}']" />
</xsl:stylesheet>
