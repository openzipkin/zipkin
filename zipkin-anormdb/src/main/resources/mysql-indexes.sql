ALTER TABLE zipkin_spans ADD PRIMARY KEY(span_id);
ALTER TABLE zipkin_spans ADD INDEX(trace_id);
ALTER TABLE zipkin_spans ADD INDEX(span_name(64));
ALTER TABLE zipkin_spans ADD INDEX(created_ts);

ALTER TABLE zipkin_annotations ADD FOREIGN KEY(span_id) REFERENCES zipkin_spans(span_id) ON DELETE CASCADE;
ALTER TABLE zipkin_annotations ADD INDEX(trace_id);
ALTER TABLE zipkin_annotations ADD INDEX(span_name(64));
ALTER TABLE zipkin_annotations ADD INDEX(value(64));
ALTER TABLE zipkin_annotations ADD INDEX(a_timestamp);

ALTER TABLE zipkin_binary_annotations ADD FOREIGN KEY(span_id) REFERENCES zipkin_spans(span_id) ON DELETE CASCADE;
ALTER TABLE zipkin_binary_annotations ADD INDEX(trace_id);
ALTER TABLE zipkin_binary_annotations ADD INDEX(span_name(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_key(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_value(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_key(64),annotation_value(64));
ALTER TABLE zipkin_binary_annotations ADD INDEX(annotation_ts);
