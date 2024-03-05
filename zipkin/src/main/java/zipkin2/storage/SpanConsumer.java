/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import zipkin2.Call;
import zipkin2.Span;

// @FunctionalInterface
public interface SpanConsumer {
  Call<Void> accept(List<Span> spans);
}
