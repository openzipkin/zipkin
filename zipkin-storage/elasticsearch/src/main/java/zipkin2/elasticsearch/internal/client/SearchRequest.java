/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin2.internal.Nullable;

public final class SearchRequest {

  public static SearchRequest create(List<String> indices) {
    return new SearchRequest(indices, null);
  }

  public static SearchRequest create(List<String> indices, String type) {
    return new SearchRequest(indices, type);
  }

  /**
   * The maximum results returned in a query. This only affects non-aggregation requests.
   *
   * <p>Not configurable as it implies adjustments to the index template (index.max_result_window)
   *
   * <p> See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html
   */
  static final int MAX_RESULT_WINDOW = 10000; // the default elasticsearch allowed limit

  transient final List<String> indices;
  @Nullable transient final String type;

  Integer size = MAX_RESULT_WINDOW;
  Boolean _source;
  Object query;
  Map<String, Aggregation> aggs;

  SearchRequest(List<String> indices, @Nullable String type) {
    this.indices = indices;
    this.type = type;
  }

  public static class Filters extends ArrayList<Object> {
    public Filters addRange(String field, long from, Long to) {
      add(new Range(field, from, to));
      return this;
    }

    public Filters addTerm(String field, String value) {
      add(new Term(field, value));
      return this;
    }
  }

  public SearchRequest filters(Filters filters) {
    return query(new BoolQuery("must", filters));
  }

  public SearchRequest term(String field, String value) {
    return query(new Term(field, value));
  }

  public SearchRequest terms(String field, Collection<String> values) {
    return query(new Terms(field, values));
  }

  public SearchRequest addAggregation(Aggregation agg) {
    size = null; // we return aggs, not source data
    _source = false;
    if (aggs == null) aggs = new LinkedHashMap<>();
    aggs.put(agg.field, agg);
    return this;
  }

  public Integer getSize() {
    return size;
  }

  public Boolean get_source() {
    return _source;
  }

  public Object getQuery() {
    return query;
  }

  public Map<String, Aggregation> getAggs() {
    return aggs;
  }

  String tag() {
    return aggs != null ? "aggregation" : "search";
  }

  SearchRequest query(Object filter) {
    query = Map.of("bool", Map.of("filter", filter));
    return this;
  }

  static class Term {

    final Map<String, String> term;

    Term(String field, String value) {
      term = Map.of(field, value);
    }
    public Map<String, String> getTerm() {
      return term;
    }
  }

  static class Terms {
    final Map<String, Collection<String>> terms;

    Terms(String field, Collection<String> values) {
      this.terms = Map.of(field, values);
    }

    public Map<String, Collection<String>> getTerms() {
      return terms;
    }
  }

  static class Range {
    final Map<String, Bounds> range;

    Range(String field, long from, Long to) {
      range = Map.of(field, new Bounds(from, to));
    }

    public Map<String, Bounds> getRange() {
      return range;
    }

    static class Bounds {
      final long gte;
      final Long lte;

      Bounds(long gte, Long lte) {
        this.gte = gte;
        this.lte = lte;
      }

      public long getGte() {
        return gte;
      }

      public Long getLte() {
        return lte;
      }
    }
  }

  static class BoolQuery {
    final Map<String, Object> bool;

    BoolQuery(String op, Object clause) {
      bool = Map.of(op, clause);
    }

    public Map<String, Object> getBool() {
      return bool;
    }
  }
}
