package zipkin2.server.internal;


import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import zipkin.server.ZipkinServer;

@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "zipkin.storage.type=", // cheat and test empty storage type
    "spring.config.name=zipkin-server",
    "zipkin.collector.grpc.enabled=true",
    "zipkin.collector.grpc.type=GRPC_JAVA"
  })
@RunWith(SpringRunner.class)
public class ITZipkinServerGrpcJavaServer extends ITZipkinServerGrpcServer {
}
