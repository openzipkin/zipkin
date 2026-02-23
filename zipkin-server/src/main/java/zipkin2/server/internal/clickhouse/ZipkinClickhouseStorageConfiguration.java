package zipkin2.server.internal.clickhouse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import zipkin2.storage.StorageComponent;
import zipkin2.storage.clickhouse.ClickHouseStorage;

@ConditionalOnClass(ClickHouseStorage.class)
@EnableConfigurationProperties(ZipkinClickhouseStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "clickhouse")
public class ZipkinClickhouseStorageConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public StorageComponent storageComponent(ZipkinClickhouseStorageProperties properties) {
    return properties.toStorageBuilder()
      .setEnsureSchema(true)
      .build();
  }
}
