/*
 * This file is generated by jOOQ.
 */
package zipkin2.storage.postgres.v1.internal.generated.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import zipkin2.storage.postgres.v1.internal.generated.Indexes;
import zipkin2.storage.postgres.v1.internal.generated.Zipkin;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ZipkinAnnotations extends TableImpl<Record> {

    private static final long serialVersionUID = 719051056;

    /**
     * The reference instance of <code>zipkin.zipkin_annotations</code>
     */
    public static final ZipkinAnnotations ZIPKIN_ANNOTATIONS = new ZipkinAnnotations();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<Record> getRecordType() {
        return Record.class;
    }

    /**
     * The column <code>zipkin.zipkin_annotations.trace_id_high</code>.
     */
    public final TableField<Record, Long> TRACE_ID_HIGH = createField(DSL.name("trace_id_high"), org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.field("0", org.jooq.impl.SQLDataType.BIGINT)), this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.trace_id</code>.
     */
    public final TableField<Record, Long> TRACE_ID = createField(DSL.name("trace_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.span_id</code>.
     */
    public final TableField<Record, Long> SPAN_ID = createField(DSL.name("span_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.a_key</code>.
     */
    public final TableField<Record, String> A_KEY = createField(DSL.name("a_key"), org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.a_value</code>.
     */
    public final TableField<Record, byte[]> A_VALUE = createField(DSL.name("a_value"), org.jooq.impl.SQLDataType.BLOB, this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.a_type</code>.
     */
    public final TableField<Record, Integer> A_TYPE = createField(DSL.name("a_type"), org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.a_timestamp</code>.
     */
    public final TableField<Record, Long> A_TIMESTAMP = createField(DSL.name("a_timestamp"), org.jooq.impl.SQLDataType.BIGINT, this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.endpoint_ipv4</code>.
     */
    public final TableField<Record, Integer> ENDPOINT_IPV4 = createField(DSL.name("endpoint_ipv4"), org.jooq.impl.SQLDataType.INTEGER, this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.endpoint_ipv6</code>.
     */
    public final TableField<Record, byte[]> ENDPOINT_IPV6 = createField(DSL.name("endpoint_ipv6"), org.jooq.impl.SQLDataType.BLOB, this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.endpoint_port</code>.
     */
    public final TableField<Record, Short> ENDPOINT_PORT = createField(DSL.name("endpoint_port"), org.jooq.impl.SQLDataType.SMALLINT, this, "");

    /**
     * The column <code>zipkin.zipkin_annotations.endpoint_service_name</code>.
     */
    public final TableField<Record, String> ENDPOINT_SERVICE_NAME = createField(DSL.name("endpoint_service_name"), org.jooq.impl.SQLDataType.VARCHAR(255), this, "");

    /**
     * Create a <code>zipkin.zipkin_annotations</code> table reference
     */
    public ZipkinAnnotations() {
        this(DSL.name("zipkin_annotations"), null);
    }

    /**
     * Create an aliased <code>zipkin.zipkin_annotations</code> table reference
     */
    public ZipkinAnnotations(String alias) {
        this(DSL.name(alias), ZIPKIN_ANNOTATIONS);
    }

    /**
     * Create an aliased <code>zipkin.zipkin_annotations</code> table reference
     */
    public ZipkinAnnotations(Name alias) {
        this(alias, ZIPKIN_ANNOTATIONS);
    }

    private ZipkinAnnotations(Name alias, Table<Record> aliased) {
        this(alias, aliased, null);
    }

    private ZipkinAnnotations(Name alias, Table<Record> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    public <O extends Record> ZipkinAnnotations(Table<O> child, ForeignKey<O, Record> key) {
        super(child, key, ZIPKIN_ANNOTATIONS);
    }

    @Override
    public Schema getSchema() {
        return Zipkin.ZIPKIN;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.ZIPKIN_ANNOTATIONS_UNIQUE);
    }

    @Override
    public ZipkinAnnotations as(String alias) {
        return new ZipkinAnnotations(DSL.name(alias), this);
    }

    @Override
    public ZipkinAnnotations as(Name alias) {
        return new ZipkinAnnotations(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public ZipkinAnnotations rename(String name) {
        return new ZipkinAnnotations(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public ZipkinAnnotations rename(Name name) {
        return new ZipkinAnnotations(name, null);
    }
}
