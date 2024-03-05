/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import java.util.LinkedHashMap;
import java.util.Map;

public class Aggregation {
  transient final String field;

  AggTerms terms;
  Map<String, String> min;
  Map<String, Aggregation> aggs;

  Aggregation(String field) {
    this.field = field;
  }

  public static Aggregation terms(String field, int size) {
    Aggregation result = new Aggregation(field);
    result.terms = new AggTerms(field, size);
    return result;
  }

  public Aggregation orderBy(String subAgg, String direction) {
    terms.order(subAgg, direction);
    return this;
  }

  public static Aggregation min(String field) {
    Aggregation result = new Aggregation(field);
    result.min = Map.of("field", field);
    return result;
  }

  public AggTerms getTerms() {
    return terms;
  }

  public Map<String, String> getMin() {
    return min;
  }

  public Map<String, Aggregation> getAggs() {
    return aggs;
  }

  static class AggTerms {
    AggTerms(String field, int size) {
      this.field = field;
      this.size = size;
    }

    final String field;
    final int size;
    Map<String, String> order;

    void order(String agg, String direction) {
      order = Map.of(agg, direction);
    }

    public String getField() {
      return field;
    }

    public int getSize() {
      return size;
    }

    public Map<String, String> getOrder() {
      return order;
    }
  }

  public Aggregation addSubAggregation(Aggregation agg) {
    if (aggs == null) aggs = new LinkedHashMap<>();
    aggs.put(agg.field, agg);
    return this;
  }
}
