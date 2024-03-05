/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.integration;

import java.util.List;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.compile;

/**
 * When ES emits a deprecation warning header in response to a method being called, the integration
 * test will fail. We cannot always fix our code however to take into account all deprecation
 * warnings, as we have to support multiple versions of ES. For these cases, add the warning message
 * to {@link #IGNORE_THESE_WARNINGS} array so it will not raise an exception anymore.
 */
abstract class IgnoredDeprecationWarnings {

  // These will be matched using header.contains(ignored[i]), so find a unique substring of the
  // warning header for it to be ignored
  static List<Pattern> IGNORE_THESE_WARNINGS = asList(
    // Basic license doesn't include x-pack.
    // https://www.elastic.co/guide/en/elasticsearch/reference/7.17/security-minimal-setup.html#_enable_elasticsearch_security_features
    compile("Elasticsearch built-in security features are not enabled."),
    compile("Elasticsearch 7\\.x will read, but not allow creation of new indices containing ':'"),
    compile("has index patterns \\[.*] matching patterns from existing older templates"),
    compile("has index patterns \\[.*] matching patterns from existing composable templates")
  );
}
