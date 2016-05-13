/**
 * Copyright 2015-2016 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.storage.elasticsearch;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

final class IndexNameFormatter {

  private static final String DAILY_INDEX_FORMAT = "yyyy-MM-dd";

  private final String index;
  private final DateFormat dateFormat;

  IndexNameFormatter(String index) {
    this.index = index;
    this.dateFormat = new SimpleDateFormat(DAILY_INDEX_FORMAT);
  }

  String indexNameForTimestamp(long timestampMillis) {
    return index + "-" + dateFormat.format(new Date(timestampMillis));
  }

  String catchAll() {
    return index + "-*";
  }
}
