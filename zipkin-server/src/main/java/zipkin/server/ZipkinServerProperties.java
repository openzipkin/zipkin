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
package zipkin.server;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zipkin")
class ZipkinServerProperties {
  private Store store = new Store();

  public Store getStore() {
    return store;
  }

  private Ui ui = new Ui();

  public Ui getUi() {
    return ui;
  }

  static class Store {
    enum Type {
      cassandra, mysql, elasticsearch, mem
    }

    private Type type = Type.mem;

    public Type getType() {
      return type;
    }

    public void setType(Type type) {
      this.type = type;
    }
  }

  static class Ui {
    private String environment;
    private int queryLimit = 10;
    private int defaultLookback = (int) TimeUnit.DAYS.toMillis(7);

    public int getDefaultLookback() {
      return defaultLookback;
    }

    public void setDefaultLookback(int defaultLookback) {
      this.defaultLookback = defaultLookback;
    }

    public String getEnvironment() {
      return environment;
    }

    public void setEnvironment(String environment) {
      this.environment = environment;
    }

    public int getQueryLimit() {
      return queryLimit;
    }

    public void setQueryLimit(int queryLimit) {
      this.queryLimit = queryLimit;
    }
  }
}
