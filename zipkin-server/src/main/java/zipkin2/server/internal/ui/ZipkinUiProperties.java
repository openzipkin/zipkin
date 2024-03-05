/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.ui;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("zipkin.ui")
class ZipkinUiProperties {
  // TODO: this isn't honored in lens https://github.com/openzipkin/zipkin/issues/2519
  static final String DEFAULT_BASEPATH = "/zipkin";

  private String environment = "";
  private int queryLimit = 10;
  private int defaultLookback = (int) TimeUnit.DAYS.toMillis(7);
  private String instrumented = ".*";
  private String logsUrl = null;
  private String supportUrl = null;
  private String archivePostUrl = null;
  private String archiveUrl = null;
  private String basepath = DEFAULT_BASEPATH;
  private boolean searchEnabled = true;
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

  public String getArchivePostUrl() {
    return archivePostUrl;
  }


  public String getArchiveUrl() {
    return archiveUrl;
  }

  public void setLogsUrl(String logsUrl) {
    if (!StringUtils.isEmpty(logsUrl)) {
      this.logsUrl = logsUrl;
    }
  }

  public String getSupportUrl() {
    return supportUrl;
  }

  public void setSupportUrl(String supportUrl) {
    if (!StringUtils.isEmpty(supportUrl)) {
      this.supportUrl = supportUrl;
    }

  }

  public void setArchivePostUrl(String archivePostUrl) {
    if (!StringUtils.isEmpty(archivePostUrl)) {
      this.archivePostUrl = archivePostUrl;
    }
  }

  public void setArchiveUrl(String archiveUrl) {
    if (!StringUtils.isEmpty(archiveUrl)) {
      this.archiveUrl = archiveUrl;
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

  public static class Dependency {
    private boolean enabled = true;
    private float lowErrorRate = 0.5f; // 50% of calls in error turns line yellow
    private float highErrorRate = 0.75f; // 75% of calls in error turns line red

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

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
