/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal.ui;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("zipkin.ui")
class ZipkinUiProperties {
  static final String DEFAULT_BASEPATH = "/zipkin";

  private String environment;
  private int queryLimit = 10;
  private int defaultLookback = (int) TimeUnit.DAYS.toMillis(7);
  private String instrumented = ".*";
  private String logsUrl = null;
  private String basepath = DEFAULT_BASEPATH;
  private boolean searchEnabled = true;
  private boolean suggestLens = true;
  private Dependency dependency = new Dependency();

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

  public String getInstrumented() {
    return instrumented;
  }

  public void setInstrumented(String instrumented) {
    this.instrumented = instrumented;
  }

  public String getLogsUrl() {
    return logsUrl;
  }

  public void setLogsUrl(String logsUrl) {
    if (!StringUtils.isEmpty(logsUrl)) {
      this.logsUrl = logsUrl;
    }
  }

  public boolean isSearchEnabled() {
    return searchEnabled;
  }

  public void setSearchEnabled(boolean searchEnabled) {
    this.searchEnabled = searchEnabled;
  }

  public Dependency getDependency() {
    return dependency;
  }

  public void setDependency(Dependency dependency) {
    this.dependency = dependency;
  }

  public String getBasepath() {
    return basepath;
  }

  public void setBasepath(String basepath) {
    this.basepath = basepath;
  }

  public boolean isSuggestLens() {
    return suggestLens;
  }

  public void setSuggestLens(boolean suggestLens) {
    this.suggestLens = suggestLens;
  }

  public static class Dependency {
    private float lowErrorRate = 0.5f; // 50% of calls in error turns line yellow
    private float highErrorRate = 0.75f; // 75% of calls in error turns line red

    public float getLowErrorRate() {
      return lowErrorRate;
    }

    public void setLowErrorRate(float lowErrorRate) {
      this.lowErrorRate = lowErrorRate;
    }

    public float getHighErrorRate() {
      return highErrorRate;
    }

    public void setHighErrorRate(float highErrorRate) {
      this.highErrorRate = highErrorRate;
    }
  }
}
