connect 127.0.0.1/9160;

create keyspace Zipkin;
use Zipkin;

create column family Traces;

create column family SpanNames;
create column family ServiceNames;

create column family ServiceSpanNameIndex with comparator = LongType;
create column family ServiceNameIndex with comparator = LongType;
create column family AnnotationsIndex with comparator = LongType;
create column family DurationIndex with comparator = LongType;

/*
TopAnnotations stores the top normal and key value annotations per service,
and dependencies stores the parents and number of calls to parents per service
*/

create column family TopAnnotations with comparator = LongType;
create column family Dependencies with comparator = LongType;
