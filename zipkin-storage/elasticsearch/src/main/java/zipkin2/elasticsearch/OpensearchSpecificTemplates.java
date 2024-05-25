/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package zipkin2.elasticsearch;

import static zipkin2.elasticsearch.OpensearchVersion.V1_0;
import static zipkin2.elasticsearch.OpensearchVersion.V4_0;

import zipkin2.internal.Nullable;

final class OpensearchSpecificTemplates extends VersionSpecificTemplates<OpensearchVersion> {
  static class DistributionTemplate extends DistributionSpecificTemplates {
    private final OpensearchVersion version;

    DistributionTemplate(OpensearchVersion version) {
      this.version = version;
    }
    
    @Override String indexTemplatesUrl(String indexPrefix, String type, @Nullable Integer templatePriority) {
      if (version.compareTo(V1_0) >= 0 && templatePriority != null) {
        return "/_index_template/" + indexPrefix + type + "_template";
      }

      return "/_template/" + indexPrefix + type + "_template";
    }

    @Override char indexTypeDelimiter() {
      return OpensearchSpecificTemplates.indexTypeDelimiter(version);
    }

    @Override
    IndexTemplates get(String indexPrefix, int indexReplicas, int indexShards,
      boolean searchEnabled, boolean strictTraceId, Integer templatePriority) {
      return new OpensearchSpecificTemplates(indexPrefix, indexReplicas, indexShards, 
        searchEnabled, strictTraceId, templatePriority).get(version);
    }
  }

  OpensearchSpecificTemplates(String indexPrefix, int indexReplicas, int indexShards,
    boolean searchEnabled, boolean strictTraceId, Integer templatePriority) {
    super(indexPrefix, indexReplicas,indexShards, searchEnabled, strictTraceId, templatePriority); 
  }

  @Override String indexPattern(String type, OpensearchVersion version) {
    return '"'
      + "index_patterns"
      + "\": \""
      + indexPrefix
      + indexTypeDelimiter(version)
      + type
      + "-*"
      + "\"";
  }
  
  static char indexTypeDelimiter(OpensearchVersion version) {
    return '-';
  }
  
  @Override boolean useComposableTemplate(OpensearchVersion version) {
    return (templatePriority != null);
  }
  
  @Override String maybeWrap(String type, OpensearchVersion version, String json) {
    return json;
  }

  @Override IndexTemplates get(OpensearchVersion version) {
    if (version.compareTo(V1_0) < 0 || version.compareTo(V4_0) >= 0) {
      throw new IllegalArgumentException(
        "OpenSearch versions 1-3.x are supported, was: " + version);
    }
    return IndexTemplates.newBuilder()
      .version(version)
      .indexTypeDelimiter(indexTypeDelimiter(version))
      .span(spanIndexTemplate(version))
      .dependency(dependencyTemplate(version))
      .autocomplete(autocompleteTemplate(version))
      .build();
  }
}
