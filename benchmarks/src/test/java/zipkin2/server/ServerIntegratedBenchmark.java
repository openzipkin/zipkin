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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

/**
 * This benchmark runs zipkin-server, storage backends, an example application, prometheus, grafana,
 * and wrk using docker to generate a performance report.
 *
 * <p>Currently there are two environment variable knobs
 *
 * <ul>
 *   <li>
 *     ZIPKIN_VERSION - specify to a released zipkin server. If unspecified, will use the current code,
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

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Nullable static final String RELEASED_ZIPKIN_VERSION = System.getenv("ZIPKIN_VERSION");

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
    GenericContainer<?> elasticsearch = new GenericContainer<>("openzipkin/zipkin-elasticsearch7")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("elasticsearch")
      .withLabel("name", "elasticsearch")
      .withLabel("storageType", "elasticsearch")
      .withExposedPorts(9200)
      .waitingFor(new HttpWaitStrategy().forPath("/_cluster/health"));
    containers.add(elasticsearch);

    runBenchmark(elasticsearch);
  }

  @Test void cassandra() throws Exception {
    runBenchmark(createCassandra("cassandra"));
  }

  @Test void cassandra3() throws Exception {
    runBenchmark(createCassandra("cassandra3"));
  }

  private GenericContainer<?> createCassandra(String storageType) {
    GenericContainer<?> cassandra = new GenericContainer<>("openzipkin/zipkin-cassandra")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("cassandra")
      .withLabel("name", "cassandra")
      .withLabel("storageType", storageType)
      .withExposedPorts(9042)
      .waitingFor(Wait.forLogMessage(".*Starting listening for CQL clients.*", 1));
    containers.add(cassandra);
    return cassandra;
  }

  @Test void mysql() throws Exception {
    GenericContainer<?> mysql = new GenericContainer<>("openzipkin/zipkin-mysql")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("mysql")
      .withLabel("name", "mysql")
      .withLabel("storageType", "mysql")
      .withExposedPorts(3306);
    containers.add(mysql);

    runBenchmark(mysql);
  }

  // Benchmark for zipkin-aws XRay UDP storage. As UDP does not actually need a server running to
  // send to, we can reuse our benchmark logic here to check it. Note, this benchmark always uses
  // a docker image and ignores RELEASED_ZIPKIN_SERVER.
  @Test void xrayUdp() throws Exception {
    GenericContainer<?> zipkin = new GenericContainer<>("openzipkin/zipkin-aws")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("zipkin")
      .withEnv("STORAGE_TYPE", "xray")
      .withExposedPorts(9411);
    containers.add(zipkin);

    runBenchmark(null, zipkin);
  }

  void runBenchmark(@Nullable GenericContainer<?> storage) throws Exception {
    runBenchmark(storage, createZipkinContainer(storage));
  }

  void runBenchmark(@Nullable GenericContainer<?> storage, GenericContainer<?> zipkin)
    throws Exception {
    GenericContainer<?> backend = new GenericContainer<>("openzipkin/example-sleuth-webmvc")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("backend")
      .withCommand("backend")
      .withExposedPorts(9000)
      .waitingFor(Wait.forHttp("/actuator/health"));
    containers.add(backend);

    GenericContainer<?> frontend = new GenericContainer<>("openzipkin/example-sleuth-webmvc")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("frontend")
      .withCommand("frontend")
      .withExposedPorts(8081)
      .waitingFor(Wait.forHttp("/actuator/health"));
    containers.add(frontend);

    GenericContainer<?> prometheus = new GenericContainer<>("prom/prometheus")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("prometheus")
      .withExposedPorts(9090)
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("prometheus.yml"), "/etc/prometheus/prometheus.yml");
    containers.add(prometheus);

    GenericContainer<?> grafana = new GenericContainer<>("grafana/grafana")
      .withNetwork(Network.SHARED)
      .withNetworkAliases("grafana")
      .withExposedPorts(3000)
      .withEnv("GF_AUTH_ANONYMOUS_ENABLED", "true")
      .withEnv("GF_AUTH_ANONYMOUS_ORG_ROLE", "Admin");
    containers.add(grafana);

    GenericContainer<?> grafanaDashboards = new GenericContainer<>("appropriate/curl")
      .withNetwork(Network.SHARED)
      .withCommand("/create.sh")
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("create-datasource-and-dashboard.sh"), "/create.sh");
    containers.add(grafanaDashboards);

    GenericContainer<?> wrk = new GenericContainer<>("skandyla/wrk")
      .withNetwork(Network.SHARED)
      .withCommand("-t4 -c128 -d100s http://frontend:8081 --latency");
    containers.add(wrk);

    grafanaDashboards.dependsOn(grafana);
    wrk.dependsOn(frontend, backend, prometheus, grafanaDashboards, zipkin);
    if (storage != null) {
      wrk.dependsOn(storage);
    }

    Startables.deepStart(Stream.of(wrk)).join();

    System.out.println("Benchmark started.");
    if (zipkin != null) {
      printContainerMapping(zipkin);
    }
    if (storage != null) {{
      printContainerMapping(storage);
    }}
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
    Map<String, String> env = new HashMap<>();
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
    if (RELEASED_ZIPKIN_VERSION == null) {
      zipkin = new GenericContainer<>("gcr.io/distroless/java:11-debug");
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
      zipkin = new GenericContainer<>("openzipkin/zipkin:" + RELEASED_ZIPKIN_VERSION);
    }

    zipkin
      .withNetwork(Network.SHARED)
      .withNetworkAliases("zipkin")
      .withExposedPorts(9411)
      .withEnv(env)
      .waitingFor(new HttpWaitStrategy().forPath("/health"));
    containers.add(zipkin);
    return zipkin;
  }

  static void printContainerMapping(GenericContainer<?> container) {
    System.out.println(String.format(
      "Container %s ports exposed at %s",
      container.getDockerImageName(),
      container.getExposedPorts().stream()
        .map(port -> new AbstractMap.SimpleImmutableEntry<>(
          port,
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
