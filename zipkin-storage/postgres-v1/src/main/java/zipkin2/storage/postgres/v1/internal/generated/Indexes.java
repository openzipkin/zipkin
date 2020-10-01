/*
 * This file is generated by jOOQ.
 */
package zipkin2.storage.postgres.v1.internal.generated;


import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;

import zipkin2.storage.postgres.v1.internal.generated.tables.ZipkinAnnotations;


/**
 * A class modelling indexes of tables of the <code>zipkin</code> schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index ZIPKIN_ANNOTATIONS_UNIQUE = Indexes0.ZIPKIN_ANNOTATIONS_UNIQUE;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index ZIPKIN_ANNOTATIONS_UNIQUE = Internal.createIndex("zipkin_annotations_unique", ZipkinAnnotations.ZIPKIN_ANNOTATIONS, new OrderField[] { ZipkinAnnotations.ZIPKIN_ANNOTATIONS.TRACE_ID_HIGH, ZipkinAnnotations.ZIPKIN_ANNOTATIONS.TRACE_ID, ZipkinAnnotations.ZIPKIN_ANNOTATIONS.SPAN_ID, ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_KEY, ZipkinAnnotations.ZIPKIN_ANNOTATIONS.A_TIMESTAMP }, true);
    }
}
