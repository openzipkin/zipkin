/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.poponline;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Span;

import java.math.BigInteger;
import java.util.List;
import java.util.Properties;

import static zipkin.internal.ApplyTimestampAndDuration.guessTimestamp;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.envOr;
import static zipkin.internal.Util.toLowerHex;

public class kafkaprod {
    private static kafkaprod ourInstance = new kafkaprod();

    public static kafkaprod getInstance() {
        return ourInstance;
    }
    private KafkaProducer<String, String> producer;
    private final Properties props = new Properties();
    private  ObjectMapper gdecoder = null;

    private String kafka_broker_output=envOr("KAFKA_BROKER_OUTPUT", "kafka.marathon.mesos:9092");
    private String kafka_topic_output=envOr("KAFKA_TOPIC_OUTPUT", "zipkin_spans");


    private kafkaprod() {
        System.out.println("New object generated");
        props.put("bootstrap.servers", kafka_broker_output);
        props.put("auto.commit.interval.ms",1000);
        props.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        //props.put("serializer.class", "kafka.serializer.StringEncoder");
        props.put("acks", "all");
        //ProducerConfig config = new ProducerConfig(props);
        producer = new KafkaProducer<>(props);
        //Producer<Long, String> producer = new Producer<Long, String>(config);
        //gdecoder  =new com.google.gson.Gson();
         gdecoder = new ObjectMapper();
    }

    public void send(pmhModel pm) {

        Long  runtime = (System.currentTimeMillis());
        String message = null;
        try {
            message = gdecoder.writeValueAsString(pm);
        } catch (Exception e) {
            System.out.println("Error getting json");
            e.printStackTrace();
            message = "[]";
        }
        //System.out.println("Adding span to kafka"+message);
        producer.send(new ProducerRecord<String, String>(kafka_topic_output,message));

    }
    public void add(List<Span> rawSpans) {
        for (Span span : rawSpans) {
            // indexing occurs by timestamp, so derive one if not present.
            Long timestamp = guessTimestamp(span);
            TraceIdUDT traceId = new TraceIdUDT(span.traceIdHigh, span.traceId);
            boolean isServerRecvSpan = isServerRecvSpan(span);


            for (String serviceName : span.serviceNames()) {
                // QueryRequest.min/maxDuration
                if (timestamp != null) {

                    pmhModel pm = new pmhModel();
                    if (span.parentId!=null)
                        pm.setParent_id(span.parentId);
                    pm.setId(span.id);
                    pm.setLinkerd(isServerRecvSpan);
                    pm.setService_name(serviceName);
                    pm.setSpan_name(span.name);
                    pm.setTimestamp(timestamp);
                    pm.setDuration(span.duration);
                    pm.setTraceId(traceId.toString());
                    pm = analizespan(span,pm);
                    this.send(pm);
                }
            }
        }
    }





    private static boolean isServerRecvSpan(Span span) {
        for (int i = 0, length = span.annotations.size(); i < length; i++) {
            Annotation annotation = span.annotations.get(i);
            if (annotation.value.equals(Constants.SERVER_RECV)) {
                return true;
            }
        }
        return false;
    }

    private static pmhModel analizespan(Span span, pmhModel pm) {

        for (int i=0, lenght = span.binaryAnnotations.size(); i< lenght; i++ ) {
            BinaryAnnotation ban = span.binaryAnnotations.get(i);
            if (ban.key.equals("http.req.host")) {
                byte[] bytes = ban.value;
                pm.setReq_host(new String(bytes, UTF_8));
            }

            if (ban.key.equals("http.rsp.status")) {
                byte[] bytes =ban.value;
                pm.setResp_code(new BigInteger(bytes).intValue());
            }

            if (ban.key.equals("http.req.method")) {
                byte[] bytes = ban.value;
                pm.setReq_method(new String(bytes, UTF_8));

            }
            if (ban.key.equals("http.rsp.content-type")) {
                byte[] bytes = ban.value;
                pm.setReq_response_type(new String(bytes, UTF_8));
            }


        }
        //calculate duration//
        Long start_time=null;
        Long duration=0L;
        for (int i=0, lenght = span.annotations.size(); i< lenght; i++ ) {
            Annotation a = span.annotations.get(i);
            if (start_time==null) {
                start_time = a.timestamp;
                continue;
            }
            duration+=(a.timestamp-start_time);
        }
        if (duration>0)
            pm.setDuration(duration);
        return pm;
    }
    static final class pmhModel {


        private Boolean isLinkerd=Boolean.FALSE;
        private String service_name="";
        private String span_name="";
        private Long   timestamp=0L ;
        private Long duration = -1L ;
        private String traceId;
        private Long parent_id=-1L;
        private Long id;
        private Integer resp_code =-1;
        private String req_host="";
        private String req_method="";
        private String req_response_type="";


        public String getReq_response_type() {
            return req_response_type;
        }

        public void setReq_response_type(String req_response_type) {
            this.req_response_type = req_response_type;
        }

        public String getReq_host() {
            return req_host;
        }

        public void setReq_host(String req_host) {
            this.req_host = req_host;
        }

        public String getReq_method() {
            return req_method;
        }

        public void setReq_method(String req_method) {
            this.req_method = req_method;
        }

        public Boolean getLinkerd() {
            return isLinkerd;
        }

        public void setLinkerd(Boolean linkerd) {
            isLinkerd = linkerd;
        }

        public Integer getResp_code() {
            return resp_code;
        }

        public void setResp_code(Integer resp_code) {
            this.resp_code = resp_code;
        }

        public Long getParent_id() {
            return parent_id;
        }

        public void setParent_id(Long parent_id) {
            this.parent_id = parent_id;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }



        public String getService_name() {
            return service_name;
        }

        public void setService_name(String service_name) {
            this.service_name = service_name;
        }

        public String getSpan_name() {
            return span_name;
        }

        public void setSpan_name(String span_name) {
            this.span_name = span_name;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }

    static final class TraceIdUDT {

        private long high;
        private long low;

        TraceIdUDT() {
            this.high = 0L;
            this.low = 0L;
        }

        TraceIdUDT(long high, long low) {
            this.high = high;
            this.low = low;
        }

        Long getHigh() {
            return high;
        }

        long getLow() {
            return low;
        }

        void setHigh(Long high) {
            this.high = high;
        }

        void setLow(long low) {
            this.low = low;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o instanceof TraceIdUDT) {
                TraceIdUDT that = (TraceIdUDT) o;
                return (this.high == that.high) && (this.low == that.low);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = 1;
            h *= 1000003;
            h ^= (high >>> 32) ^ high;
            h *= 1000003;
            h ^= (low >>> 32) ^ low;
            return h;
        }

        @Override
        public String toString() {
            return toLowerHex(high, low);
        }
    }
}
