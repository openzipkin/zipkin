/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.client.WebClient;
import io.netty.handler.codec.http.QueryStringEncoder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import static org.testcontainers.utility.DockerImageName.parse;

/**
 * This benchmark runs zipkin-server, storage backends, an example application, prometheus, grafana,
 * and wrk using docker to generate a performance report.
 *
 * <p>Currently there are two environment variable knobs
 *
 * <ul>
 *   <li>
 *     RELEASE_VERSION - specify to a released zipkin server. If unspecified, will use the current code,
 *     i.e., the code currently displayed in your IDE.
 *   </li>
 *   <li>
 *     ZIPKIN_BENCHMARK_WAIT - set to true to have the benchmark wait until user manually terminates at the end.
 *     Useful to manually inspect prometheus / grafana.
 *   </li>
 * </ul>
 *
 * <p>Note to Windows laptop users: you will probably need to restart the Docker daemon before a
 * session of benchmarks. Docker containers seem to have time get out of sync when a computer sleeps
 * until you restart the daemon - this causes Prometheus metrics to not scrape properly.
 */
@Disabled  // Run manually
class ServerIntegratedBenchmark {
  static final Logger LOG = LoggerFactory.getLogger(ServerIntegratedBenchmark.class);

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Nullable static final String RELEASE_VERSION = System.getenv("RELEASE_VERSION");

  static final boolean WAIT_AFTER_BENCHMARK = "true".equals(System.getenv("ZIPKIN_BENCHMARK_WAIT"));

  List<GenericContainer<?>> containers;

  @BeforeEach void setUp() {
    containers = new ArrayList<>();
  }

  @AfterEach void tearDown() {
    containers.forEach(GenericContainer::stop);
  }

  @Test void inMemory() throws Exception {
    runBenchmark(null);
  }

  @Test void elasticsearch() throws Exception {
    GenericContainer<?> elasticsearch =
      new GenericContainer<>(parse("ghcr.io/openzipkin/zipkin-elasticsearch7:2.23.2"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("elasticsearch")
        .withLabel("name", "elasticsearch")
        .withLabel("storageType", "elasticsearch")
        .withExposedPorts(9200)
        .waitingFor(Wait.forHealthcheck());
    containers.add(elasticsearch);

    runBenchmark(elasticsearch);
  }

  @Test void cassandra3() throws Exception {
    GenericContainer<?> cassandra =
      new GenericContainer<>(parse("ghcr.io/openzipkin/zipkin-cassandra:2.23.2"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("cassandra")
        .withLabel("name", "cassandra")
        .withLabel("storageType", "cassandra3")
        .withExposedPorts(9042)
        .waitingFor(Wait.forHealthcheck());
    containers.add(cassandra);

    runBenchmark(cassandra);
  }

  @Test void mysql() throws Exception {
    GenericContainer<?> mysql =
      new GenericContainer<>(parse("ghcr.io/openzipkin/zipkin-mysql:2.23.2"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("mysql")
        .withLabel("name", "mysql")
        .withLabel("storageType", "mysql")
        .withExposedPorts(3306)
        .waitingFor(Wait.forHealthcheck());
    containers.add(mysql);

    runBenchmark(mysql);
  }

  void runBenchmark(@Nullable GenericContainer<?> storage) throws Exception {
    runBenchmark(storage, createZipkinContainer(storage));
  }

  void runBenchmark(@Nullable GenericContainer<?> storage, GenericContainer<?> zipkin)
    throws Exception {
    GenericContainer<?> backend =
      new GenericContainer<>(parse("ghcr.io/openzipkin/brave-example:armeria"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("backend")
        .withCommand("backend")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHealthcheck());

    GenericContainer<?> frontend =
      new GenericContainer<>(parse("ghcr.io/openzipkin/brave-example:armeria"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("frontend")
        .withCommand("frontend")
        .withExposedPorts(8081)
        .waitingFor(Wait.forHealthcheck());
    containers.add(frontend);

    // Use a quay.io mirror to prevent build outages due to Docker Hub pull quotas
    // Use same version as in docker/examples/docker-compose-prometheus.yml
    GenericContainer<?> prometheus =
      new GenericContainer<>(parse("quay.io/prometheus/prometheus:v2.23.0"))
        .withNetwork(Network.SHARED)
        .withNetworkAliases("prometheus")
        .withExposedPorts(9090)
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("prometheus.yml"), "/etc/prometheus/prometheus.yml");
    containers.add(prometheus);

    // Use a quay.io mirror to prevent build outages due to Docker Hub pull quotas
    // Use same version as in docker/examples/docker-compose-prometheus.yml
    GenericContainer<?> grafana = new GenericContainer<>(parse("quay.io/app-sre/grafana:7.3.4"))
      .withNetwork(Network.SHARED)
      .withNetworkAliases("grafana")
      .withExposedPorts(3000)
      .withEnv("GF_AUTH_ANONYMOUS_ENABLED", "true")
      .withEnv("GF_AUTH_ANONYMOUS_ORG_ROLE", "Admin");
    containers.add(grafana);

    // This is an arbitrary small image that has curl installed
    // Use a quay.io mirror to prevent build outages due to Docker Hub pull quotas
    // Use same version as in docker/examples/docker-compose-prometheus.yml
    GenericContainer<?> grafanaDashboards =
      new GenericContainer<>(parse("quay.io/rackspace/curl:7.70.0"))
        .withNetwork(Network.SHARED)
        .withWorkingDirectory("/tmp")
        .withLogConsumer(new Slf4jLogConsumer(LOG))
        .withCreateContainerCmdModifier(it -> it.withEntrypoint("/tmp/create.sh"))
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("create-datasource-and-dashboard.sh", 555),
          "/tmp/create.sh");
    containers.add(grafanaDashboards);

    // Use a quay.io mirror to prevent build outages due to Docker Hub pull quotas
    GenericContainer<?> wrk = new GenericContainer<>(parse("quay.io/dim/wrk:stable"))
      .withNetwork(Network.SHARED)
      .withCreateContainerCmdModifier(it -> it.withEntrypoint("wrk"))
      .withCommand("-t4 -c128 -d100s http://frontend:8081 --latency");
    containers.add(wrk);

    grafanaDashboards.dependsOn(grafana);
    wrk.dependsOn(frontend, backend, prometheus, grafanaDashboards, zipkin);
    if (storage != null) wrk.dependsOn(storage);

    Startables.deepStart(Stream.of(wrk)).join();

    System.out.println("Benchmark started.");
    if (zipkin != null) printContainerMapping(zipkin);
    if (storage != null) printContainerMapping(storage);
    printContainerMapping(backend);
    printContainerMapping(frontend);
    printContainerMapping(prometheus);
    printContainerMapping(grafana);

    while (wrk.isRunning()) {
      Thread.sleep(1000);
    }

    // Wait for prometheus to do a final scrape.
    Thread.sleep(5000);

    System.out.println("Benchmark complete, wrk output:");
    System.out.println(wrk.getLogs().replace("\n\n", "\n"));

    WebClient prometheusClient = WebClient.of(
      "h1c://" + prometheus.getContainerIpAddress() + ":" + prometheus.getFirstMappedPort());

    System.out.println(String.format("Messages received: %s", prometheusValue(
      prometheusClient, "sum(zipkin_collector_messages_total)")));
    System.out.println(String.format("Spans received: %s", prometheusValue(
      prometheusClient, "sum(zipkin_collector_spans_total)")));
    System.out.println(String.format("Spans dropped: %s", prometheusValue(
      prometheusClient, "sum(zipkin_collector_spans_dropped_total)")));

    System.out.println("Memory quantiles:");
    printQuartiles(prometheusClient, "jvm_memory_used_bytes{area=\"heap\"}");
    printQuartiles(prometheusClient, "jvm_memory_used_bytes{area=\"nonheap\"}");

    System.out.println(String.format("Total GC time (s): %s",
      prometheusValue(prometheusClient, "sum(jvm_gc_pause_seconds_sum)")));
    System.out.println(String.format("Number of GCs: %s",
      prometheusValue(prometheusClient, "sum(jvm_gc_pause_seconds_count)")));

    System.out.println("POST Spans latency (s)");
    printHistogram(prometheusClient, "http_server_requests_seconds_bucket{"
      + "method=\"POST\",status=\"202\",uri=\"/api/v2/spans\"}");

    if (WAIT_AFTER_BENCHMARK) {
      System.out.println("Keeping containers running until explicit termination. "
        + "Feel free to poke around in grafana.");
      Thread.sleep(Long.MAX_VALUE);
    }
  }

  GenericContainer<?> createZipkinContainer(@Nullable GenericContainer<?> storage)
    throws Exception {
    Map<String, String> env = new LinkedHashMap<>();
    if (storage != null) {
      String name = storage.getLabels().get("name");
      String host = name;
      int port = storage.getExposedPorts().get(0);
      String address = host + ":" + port;

      env.put("STORAGE_TYPE", storage.getLabels().get("storageType"));
      switch (name) {
        case "elasticsearch":
          env.put("ES_HOSTS", "http://" + address);
          break;
        case "cassandra":
        case "cassandra3":
          env.put("CASSANDRA_CONTACT_POINTS", address);
          break;
        case "mysql":
          env.put("MYSQL_HOST", host);
          env.put("MYSQL_TCP_PORT", Integer.toString(port));
          env.put("MYSQL_USER", "zipkin");
          env.put("MYSQL_PASS", "zipkin");
          break;
        default:
          throw new IllegalArgumentException("Unknown storage " + name +
            ". Update startZipkin to map it to properties.");
      }
    }

    final GenericContainer<?> zipkin;
    if (RELEASE_VERSION == null) {
      zipkin = new GenericContainer<>(parse("ghcr.io/openzipkin/java:15.0.1_p9"));
      List<String> classpath = new ArrayList<>();
      for (String item : System.getProperty("java.class.path").split(File.pathSeparator)) {
        Path path = Paths.get(item);
        final String containerPath;
        if (Files.isDirectory(path)) {
          Path root = path.getParent();
          while (root != null) {
            try (Stream<Path> f = Files.list(root)) {
              if (f.anyMatch(p -> p.getFileName().toString().equals("mvnw"))) {
                break;
              }
            }
            root = root.getParent();
          }
          containerPath = root.relativize(path).toString().replace('\\', '/');
        } else {
          containerPath = path.getFileName().toString();
        }
        // Test containers currently doesn't support copying in a path with subdirectories that
        // need to be created, so we mangle directory structure into a single directory with
        // hyphens.
        String classPathItem = "/classpath-" + containerPath.replace('/', '-');
        zipkin.withCopyFileToContainer(MountableFile.forHostPath(item), classPathItem);
        classpath.add(classPathItem);
      }
      zipkin.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"));
      zipkin.setCommand("-cp", String.join(":", classpath), "zipkin.server.ZipkinServer");
      // Don't fail on classpath problem from missing lens, as we don't use it.
      env.put("ZIPKIN_UI_ENABLED", "false");
    } else {
      zipkin = new GenericContainer<>(parse("ghcr.io/openzipkin/zipkin:" + RELEASE_VERSION));
    }

    zipkin
      .withNetwork(Network.SHARED)
      .withNetworkAliases("zipkin")
      .withExposedPorts(9411)
      .withEnv(env)
      .waitingFor(Wait.forHealthcheck());
    containers.add(zipkin);
    return zipkin;
  }

  static void printContainerMapping(GenericContainer<?> container) {
    System.out.println(String.format(
      "Container %s ports exposed at %s",
      container.getDockerImageName(),
      container.getExposedPorts().stream()
        .map(port -> new SimpleImmutableEntry<>(port,
          "http://" + container.getContainerIpAddress() + ":" + container.getMappedPort(port)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
  }

  static void printQuartiles(WebClient prometheus, String metric) throws Exception {
    for (double quantile : Arrays.asList(0.0, 0.25, 0.5, 0.75, 1.0)) {
      String value = prometheusValue(prometheus, "quantile(" + quantile + ", " + metric + ")");
      System.out.println(String.format("%s[%s] = %s", metric, quantile, value));
    }
  }

  static void printHistogram(WebClient prometheus, String metric) throws Exception {
    for (double quantile : Arrays.asList(0.5, 0.9, 0.99)) {
      String value =
        prometheusValue(prometheus, "histogram_quantile(" + quantile + ", " + metric + ")");
      System.out.println(String.format("%s[%s] = %s", metric, quantile, value));
    }
  }

  static String prometheusValue(WebClient prometheus, String query) throws Exception {
    QueryStringEncoder encoder = new QueryStringEncoder("/api/v1/query");
    encoder.addParam("query", query);
    String response = prometheus.get(encoder.toString()).aggregate().join().contentUtf8();
    return OBJECT_MAPPER.readTree(response).at("/data/result/0/value/1").asText();
  }
}
