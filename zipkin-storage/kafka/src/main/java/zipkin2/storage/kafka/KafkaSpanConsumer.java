package zipkin2.storage.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

import java.util.List;

public class KafkaSpanConsumer implements SpanConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaSpanStore.class);

    public KafkaSpanConsumer(KafkaStorage kafkaStorage) {
    }

    @Override
    public Call<Void> accept(List<Span> list) {
        LOG.info("Hey; I'm still implementing this! Be patient!");
        return null;
    }
}
