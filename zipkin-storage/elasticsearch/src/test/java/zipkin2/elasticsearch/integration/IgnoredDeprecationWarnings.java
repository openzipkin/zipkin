package zipkin2.elasticsearch.integration;

/**
 * When ES emits a deprecation warning header in response to a method being called, the integration
 * test will fail. We cannot always fix our code however to take into account all deprecation warnings,
 * as we have to support multiple versions of ES. For these cases, add the warning message to
 * {@link #IGNORE_THESE_WARNINGS} array so it will not raise an exception anymore.
 */
abstract class IgnoredDeprecationWarnings {

  // these will be matched using header.contains(ignored[i]), so find a unique substring of the
  // warning header for it to be ignored
  static String[] IGNORE_THESE_WARNINGS = {
    "Elasticsearch 7.x will read, but not allow creation of new indices containing ':'",
    "has index patterns [*] matching patterns from existing older templates"
  };
}
