/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.internal;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.Codec;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import okio.Buffer;
import okio.ByteString;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryInputTransport;
import org.apache.thrift.transport.TTransport;

import static java.util.logging.Level.FINEST;
import static org.apache.thrift.protocol.TProtocolUtil.skip;

/**
 * This is a hard-coded thrift codec, which allows us to include thrift marshalling in a minified
 * core jar. The hard coding not only keeps us with a single data-model, it also allows the minified
 * core jar free of SLFJ classes otherwise included in generated types.
 *
 * <p/> The implementation appears scarier than it is. It was made by mechanically copying in the
 * method bodies of generated thrift classes, specifically {@link org.apache.thrift.TBase#read} and
 * {@link org.apache.thrift.TBase#write}. Zipkin uses thrifts that have been very stable in the last
 * few years. Moreover, users consolidate to {@link TBinaryProtocol} at rest, even if the structs
 * are later compressed with snappy.
 */
public final class ThriftCodec implements Codec {
  private static final Logger LOGGER = Logger.getLogger(ThriftCodec.class.getName());

  @Override
  public Span readSpan(byte[] bytes) {
    return read(SPAN_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SPAN_ADAPTER, value);
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
    return read(SPANS_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpans(List<Span> value) {
    return write(SPANS_ADAPTER, value);
  }

  @Override
  public byte[] writeTraces(List<List<Span>> value) {
    return write(TRACES_ADAPTER, value);
  }

  interface ThriftWriter<T> {
    void write(T value, TProtocol oprot) throws TException;
  }

  interface ThriftReader<T> {
    T read(TProtocol iprot) throws TException;
  }

  interface ThriftAdapter<T> extends ThriftReader<T>, ThriftWriter<T> {
  }

  static final ThriftAdapter<Endpoint> ENDPOINT_ADAPTER = new ThriftAdapter<Endpoint>() {

    private final TStruct STRUCT_DESC = new TStruct("Endpoint");
    private final TField IPV4_FIELD_DESC = new TField("ipv4", TType.I32, (short) 1);
    private final TField PORT_FIELD_DESC = new TField("port", TType.I16, (short) 2);
    private final TField SERVICE_NAME_FIELD_DESC = new TField("service_name", TType.STRING, (short) 3);

    @Override
    public Endpoint read(TProtocol iprot) throws TException {
      Endpoint.Builder result = new Endpoint.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // IPV4
            if (field.type == TType.I32) {
              result.ipv4(iprot.readI32());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 2: // PORT
            if (field.type == TType.I16) {
              result.port(iprot.readI16());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 3: // SERVICE_NAME
            if (field.type == TType.STRING) {
              result.serviceName(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          default:
            skip(iprot, field.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      return result.build();
    }

    @Override
    public void write(Endpoint value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(IPV4_FIELD_DESC);
      oprot.writeI32(value.ipv4);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(PORT_FIELD_DESC);
      oprot.writeI16(value.port == null ? 0 : value.port);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(SERVICE_NAME_FIELD_DESC);
      oprot.writeString(value.serviceName);
      oprot.writeFieldEnd();

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  };

  static final ThriftAdapter<Annotation> ANNOTATION_ADAPTER = new ThriftAdapter<Annotation>() {

    private final TStruct STRUCT_DESC = new TStruct("Annotation");
    private final TField TIMESTAMP_FIELD_DESC = new TField("timestamp", TType.I64, (short) 1);
    private final TField VALUE_FIELD_DESC = new TField("value", TType.STRING, (short) 2);
    private final TField HOST_FIELD_DESC = new TField("host", TType.STRUCT, (short) 3);

    @Override
    public Annotation read(TProtocol iprot) throws TException {
      Annotation.Builder result = new Annotation.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // TIMESTAMP
            if (field.type == TType.I64) {
              result.timestamp(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 2: // VALUE
            if (field.type == TType.STRING) {
              result.value(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 3: // HOST
            if (field.type == TType.STRUCT) {
              result.endpoint(ENDPOINT_ADAPTER.read(iprot));
            } else {
              skip(iprot, field.type);
            }
            break;
          default:
            skip(iprot, field.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      return result.build();
    }

    @Override
    public void write(Annotation value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
      oprot.writeI64(value.timestamp);
      oprot.writeFieldEnd();

      if (value.value != null) {
        oprot.writeFieldBegin(VALUE_FIELD_DESC);
        oprot.writeString(value.value);
        oprot.writeFieldEnd();
      }

      if (value.endpoint != null) {
        oprot.writeFieldBegin(HOST_FIELD_DESC);
        ENDPOINT_ADAPTER.write(value.endpoint, oprot);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  };

  static final ThriftAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new ThriftAdapter<BinaryAnnotation>() {

    private final TStruct STRUCT_DESC = new TStruct("BinaryAnnotation");
    private final TField KEY_FIELD_DESC = new TField("key", TType.STRING, (short) 1);
    private final TField VALUE_FIELD_DESC = new TField("value", TType.STRING, (short) 2);
    private final TField ANNOTATION_TYPE_FIELD_DESC = new TField("annotation_type", TType.I32, (short) 3);
    private final TField HOST_FIELD_DESC = new TField("host", TType.STRUCT, (short) 4);

    @Override
    public BinaryAnnotation read(TProtocol iprot) throws TException {
      BinaryAnnotation.Builder result = new BinaryAnnotation.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // KEY
            if (field.type == TType.STRING) {
              result.key(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 2: // VALUE
            if (field.type == TType.STRING) {
              ByteBuffer buffer = iprot.readBinary();
              byte[] value = new byte[buffer.remaining()];
              buffer.get(value);
              result.value(value);
            } else {
              skip(iprot, field.type);
            }
            break;
          case 3: // ANNOTATION_TYPE
            if (field.type == TType.I32) {
              result.type(BinaryAnnotation.Type.fromValue(iprot.readI32()));
            } else {
              skip(iprot, field.type);
            }
            break;
          case 4: // HOST
            if (field.type == TType.STRUCT) {
              result.endpoint(ENDPOINT_ADAPTER.read(iprot));
            } else {
              skip(iprot, field.type);
            }
            break;
          default:
            skip(iprot, field.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      return result.build();
    }

    @Override
    public void write(BinaryAnnotation value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(KEY_FIELD_DESC);
      oprot.writeString(value.key);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(VALUE_FIELD_DESC);
      oprot.writeBinary(ByteBuffer.wrap(value.value));
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(ANNOTATION_TYPE_FIELD_DESC);
      oprot.writeI32(value.type.value);
      oprot.writeFieldEnd();

      if (value.endpoint != null) {
        oprot.writeFieldBegin(HOST_FIELD_DESC);
        ENDPOINT_ADAPTER.write(value.endpoint, oprot);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  };

  static final ThriftAdapter<Span> SPAN_ADAPTER = new ThriftAdapter<Span>() {

    private final TStruct STRUCT_DESC = new TStruct("Span");
    private final TField TRACE_ID_FIELD_DESC = new TField("trace_id", TType.I64, (short) 1);
    private final TField NAME_FIELD_DESC = new TField("name", TType.STRING, (short) 3);
    private final TField ID_FIELD_DESC = new TField("id", TType.I64, (short) 4);
    private final TField PARENT_ID_FIELD_DESC = new TField("parent_id", TType.I64, (short) 5);
    private final TField ANNOTATIONS_FIELD_DESC = new TField("annotations", TType.LIST, (short) 6);
    private final TField BINARY_ANNOTATIONS_FIELD_DESC = new TField("binary_annotations", TType.LIST, (short) 8);
    private final TField DEBUG_FIELD_DESC = new TField("debug", TType.BOOL, (short) 9);
    private final TField TIMESTAMP_FIELD_DESC = new TField("timestamp", TType.I64, (short) 10);
    private final TField DURATION_FIELD_DESC = new TField("duration", TType.I64, (short) 11);

    @Override
    public Span read(TProtocol iprot) throws TException {
      Span.Builder result = new Span.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // TRACE_ID
            if (field.type == TType.I64) {
              result.traceId(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 3: // NAME
            if (field.type == TType.STRING) {
              result.name(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 4: // ID
            if (field.type == TType.I64) {
              result.id(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 5: // PARENT_ID
            if (field.type == TType.I64) {
              result.parentId(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 6: // ANNOTATIONS
            if (field.type == TType.LIST) {
              TList annotations = iprot.readListBegin();
              for (int i = 0; i < annotations.size; i++) {
                result.addAnnotation(ANNOTATION_ADAPTER.read(iprot));
              }
              iprot.readListEnd();
            } else {
              skip(iprot, field.type);
            }
            break;
          case 8: // BINARY_ANNOTATIONS
            if (field.type == TType.LIST) {
              TList binaryAnnotations = iprot.readListBegin();
              for (int i = 0; i < binaryAnnotations.size; i++) {
                result.addBinaryAnnotation(BINARY_ANNOTATION_ADAPTER.read(iprot));
              }
              iprot.readListEnd();
            } else {
              skip(iprot, field.type);
            }
            break;
          case 9: // DEBUG
            if (field.type == TType.BOOL) {
              result.debug(iprot.readBool());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 10: // TIMESTAMP
            if (field.type == TType.I64) {
              result.timestamp(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 11: // DURATION
            if (field.type == TType.I64) {
              result.duration(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          default:
            skip(iprot, field.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      return result.build();
    }

    @Override
    public void write(Span value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(TRACE_ID_FIELD_DESC);
      oprot.writeI64(value.traceId);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(NAME_FIELD_DESC);
      oprot.writeString(value.name);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(ID_FIELD_DESC);
      oprot.writeI64(value.id);
      oprot.writeFieldEnd();

      if (value.parentId != null) {
        oprot.writeFieldBegin(PARENT_ID_FIELD_DESC);
        oprot.writeI64(value.parentId);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldBegin(ANNOTATIONS_FIELD_DESC);
      oprot.writeListBegin(new TList(TType.STRUCT, value.annotations.size()));
      for (int i = 0, length = value.annotations.size(); i < length; i++) {
        ANNOTATION_ADAPTER.write(value.annotations.get(i), oprot);
      }
      oprot.writeListEnd();
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(BINARY_ANNOTATIONS_FIELD_DESC);
      oprot.writeListBegin(new TList(TType.STRUCT, value.binaryAnnotations.size()));
      for (int i = 0, length = value.binaryAnnotations.size(); i < length; i++) {
        BINARY_ANNOTATION_ADAPTER.write(value.binaryAnnotations.get(i), oprot);
      }
      oprot.writeListEnd();
      oprot.writeFieldEnd();

      if (value.debug != null) {
        oprot.writeFieldBegin(DEBUG_FIELD_DESC);
        oprot.writeBool(value.debug);
        oprot.writeFieldEnd();
      }

      if (value.timestamp != null) {
        oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
        oprot.writeI64(value.timestamp);
        oprot.writeFieldEnd();
      }

      if (value.duration != null) {
        oprot.writeFieldBegin(DURATION_FIELD_DESC);
        oprot.writeI64(value.duration);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  };

  static final ThriftAdapter<List<Span>> SPANS_ADAPTER = new ListAdapter<>(SPAN_ADAPTER);
  static final ThriftAdapter<List<List<Span>>> TRACES_ADAPTER = new ListAdapter<>(SPANS_ADAPTER);

  static final ThriftAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new ThriftAdapter<DependencyLink>() {

    private final TStruct STRUCT_DESC = new TStruct("DependencyLink");
    private final TField PARENT_FIELD_DESC = new TField("parent", TType.STRING, (short) 1);
    private final TField CHILD_FIELD_DESC = new TField("child", TType.STRING, (short) 2);
    private final TField CALL_COUNT_FIELD_DESC = new TField("call_count", TType.I64, (short) 4);

    @Override
    public DependencyLink read(TProtocol iprot) throws TException {
      DependencyLink.Builder result = new DependencyLink.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // PARENT
            if (field.type == TType.STRING) {
              result.parent(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 2: // CHILD
            if (field.type == TType.STRING) {
              result.child(iprot.readString());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 4: // CALL_COUNT
            if (field.type == TType.I64) {
              result.callCount(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          default:
            skip(iprot, field.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();
      return result.build();
    }

    @Override
    public void write(DependencyLink value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(PARENT_FIELD_DESC);
      oprot.writeString(value.parent);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(CHILD_FIELD_DESC);
      oprot.writeString(value.child);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(CALL_COUNT_FIELD_DESC);
      oprot.writeI64(value.callCount);
      oprot.writeFieldEnd();

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  };

  static final ThriftAdapter<List<DependencyLink>> DEPENDENCY_LINKS_ADAPTER = new ListAdapter<>(DEPENDENCY_LINK_ADAPTER);

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    return read(DEPENDENCY_LINKS_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    return write(DEPENDENCY_LINKS_ADAPTER, value);
  }

  private static <T> T read(ThriftReader<T> reader, byte[] bytes) {
    try {
      return reader.read(new TBinaryProtocol(new TMemoryInputTransport(bytes)));
    } catch (Exception e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Could not read " + reader + " from TBinary " + ByteString.of(bytes).base64(), e);
      }
      return null;
    }
  }

  private static <T> byte[] write(ThriftWriter<T> writer, T value) {
    BufferTransport transport = new BufferTransport();
    TBinaryProtocol protocol = new TBinaryProtocol(transport);
    try {
      writer.write(value, protocol);
    } catch (Exception e) {
      if (LOGGER.isLoggable(FINEST)) {
        LOGGER.log(FINEST, "Could not write " + value + " as TBinary", e);
      }
      return null;
    }
    return transport.buffer.readByteArray();
  }

  static <T> List<T> readList(ThriftReader<T> reader, TProtocol iprot) throws TException {
    TList spans = iprot.readListBegin();
    if (spans.size > 10000) { // don't allocate massive arrays
      throw new IllegalArgumentException(spans.size + " > 10000: possibly malformed thrift");
    }
    List<T> result = new ArrayList<>(spans.size);
    for (int i = 0; i < spans.size; i++) {
      result.add(reader.read(iprot));
    }
    iprot.readListEnd();
    return result;
  }

  static <T> void writeList(ThriftWriter<T> writer, List<T> value, TProtocol oprot) throws TException {
    oprot.writeListBegin(new TList(TType.STRUCT, value.size()));
    for (int i = 0, length = value.size(); i < length; i++) {
      writer.write(value.get(i), oprot);
    }
    oprot.writeListEnd();
  }

  private static final class ListAdapter<T> implements ThriftAdapter<List<T>> {
    private final ThriftAdapter<T> adapter;

    ListAdapter(ThriftAdapter<T> adapter) {
      this.adapter = adapter;
    }

    @Override
    public List<T> read(TProtocol iprot) throws TException {
      return readList(adapter, iprot);
    }

    @Override
    public void write(List<T> value, TProtocol oprot) throws TException {
      writeList(adapter, value, oprot);
    }

    @Override
    public String toString() {
      return "List<" + adapter + ">";
    }
  }

  static final class BufferTransport extends TTransport {
    final Buffer buffer = new Buffer();

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public int read(byte[] buf, int off, int len) {
      return buffer.read(buf, off, len);
    }

    @Override
    public void write(byte[] buf, int off, int len) {
      buffer.write(buf, off, len);
    }
  }
}
