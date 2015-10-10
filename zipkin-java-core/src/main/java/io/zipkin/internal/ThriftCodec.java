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
import io.zipkin.Dependencies;
import io.zipkin.DependencyLink;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import java.nio.ByteBuffer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;

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

  @Override
  public Span readSpan(byte[] bytes) {
    return read(SpanAdapter.INSTANCE, bytes);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SpanAdapter.INSTANCE, value);
  }

  interface ThriftAdapter<T> {
    T read(TProtocol iprot) throws TException;

    void write(T value, TProtocol oprot) throws TException;
  }

  enum EndpointAdapter implements ThriftAdapter<Endpoint> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("Endpoint");

    private static final TField IPV4_FIELD_DESC = new TField("ipv4", TType.I32, (short) 1);
    private static final TField PORT_FIELD_DESC = new TField("port", TType.I16, (short) 2);
    private static final TField SERVICE_NAME_FIELD_DESC = new TField("service_name", TType.STRING, (short) 3);

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
      oprot.writeI16(value.port);
      oprot.writeFieldEnd();
      oprot.writeFieldBegin(SERVICE_NAME_FIELD_DESC);
      oprot.writeString(value.serviceName);
      oprot.writeFieldEnd();

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  }

  enum AnnotationAdapter implements ThriftAdapter<Annotation> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("Annotation");
    private static final TField TIMESTAMP_FIELD_DESC = new TField("timestamp", TType.I64, (short) 1);
    private static final TField VALUE_FIELD_DESC = new TField("value", TType.STRING, (short) 2);
    private static final TField HOST_FIELD_DESC = new TField("host", TType.STRUCT, (short) 3);

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
              result.endpoint(EndpointAdapter.INSTANCE.read(iprot));
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
        EndpointAdapter.INSTANCE.write(value.endpoint, oprot);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  }

  enum BinaryAnnotationAdapter implements ThriftAdapter<BinaryAnnotation> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("BinaryAnnotation");
    private static final TField KEY_FIELD_DESC = new TField("key", TType.STRING, (short) 1);
    private static final TField VALUE_FIELD_DESC = new TField("value", TType.STRING, (short) 2);
    private static final TField ANNOTATION_TYPE_FIELD_DESC = new TField("annotation_type", TType.I32, (short) 3);
    private static final TField HOST_FIELD_DESC = new TField("host", TType.STRUCT, (short) 4);

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
              result.endpoint(EndpointAdapter.INSTANCE.read(iprot));
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
        EndpointAdapter.INSTANCE.write(value.endpoint, oprot);
        oprot.writeFieldEnd();
      }

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  }

  enum SpanAdapter implements ThriftAdapter<Span> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("Span");

    private static final TField TRACE_ID_FIELD_DESC = new TField("trace_id", TType.I64, (short) 1);
    private static final TField NAME_FIELD_DESC = new TField("name", TType.STRING, (short) 3);
    private static final TField ID_FIELD_DESC = new TField("id", TType.I64, (short) 4);
    private static final TField PARENT_ID_FIELD_DESC = new TField("parent_id", TType.I64, (short) 5);
    private static final TField ANNOTATIONS_FIELD_DESC = new TField("annotations", TType.LIST, (short) 6);
    private static final TField BINARY_ANNOTATIONS_FIELD_DESC = new TField("binary_annotations", TType.LIST, (short) 8);
    private static final TField DEBUG_FIELD_DESC = new TField("debug", TType.BOOL, (short) 9);

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
                result.addAnnotation(AnnotationAdapter.INSTANCE.read(iprot));
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
                result.addBinaryAnnotation(BinaryAnnotationAdapter.INSTANCE.read(iprot));
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
        AnnotationAdapter.INSTANCE.write(value.annotations.get(i), oprot);
      }
      oprot.writeListEnd();
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(BINARY_ANNOTATIONS_FIELD_DESC);
      oprot.writeListBegin(new TList(TType.STRUCT, value.binaryAnnotations.size()));
      for (int i = 0, length = value.binaryAnnotations.size(); i < length; i++) {
        BinaryAnnotationAdapter.INSTANCE.write(value.binaryAnnotations.get(i), oprot);
      }
      oprot.writeListEnd();
      oprot.writeFieldEnd();

      if (value.debug != null) {
        oprot.writeFieldBegin(DEBUG_FIELD_DESC);
        oprot.writeBool(value.debug);
        oprot.writeFieldEnd();
        oprot.writeFieldStop();
      }

      oprot.writeStructEnd();
    }
  }

  enum DependencyLinkAdapter implements ThriftAdapter<DependencyLink> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("DependencyLink");
    private static final TField PARENT_FIELD_DESC = new TField("parent", TType.STRING, (short) 1);
    private static final TField CHILD_FIELD_DESC = new TField("child", TType.STRING, (short) 2);
    private static final TField CALL_COUNT_FIELD_DESC = new TField("call_count", TType.I64, (short) 4);

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
  }

  @Override
  public DependencyLink readDependencyLink(byte[] bytes) {
    return read(DependencyLinkAdapter.INSTANCE, bytes);
  }

  @Override
  public byte[] writeDependencyLink(DependencyLink value) {
    return write(DependencyLinkAdapter.INSTANCE, value);
  }

  enum DependenciesAdapter implements ThriftAdapter<Dependencies> {
    INSTANCE;

    private static final TStruct STRUCT_DESC = new TStruct("Dependencies");
    private static final TField START_TS_FIELD_DESC = new TField("start_ts", TType.I64, (short) 1);
    private static final TField END_TS_FIELD_DESC = new TField("end_ts", TType.I64, (short) 2);
    private static final TField LINKS_FIELD_DESC = new TField("links", TType.LIST, (short) 3);

    @Override
    public Dependencies read(TProtocol iprot) throws TException {
      Dependencies.Builder result = new Dependencies.Builder();
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
          case 1: // START_TS
            if (field.type == TType.I64) {
              result.startTs(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 2: // END_TS
            if (field.type == TType.I64) {
              result.endTs(iprot.readI64());
            } else {
              skip(iprot, field.type);
            }
            break;
          case 3: // LINKS
            if (field.type == TType.LIST) {
              TList links = iprot.readListBegin();
              for (int i = 0; i < links.size; i++) {
                result.addLink(DependencyLinkAdapter.INSTANCE.read(iprot));
              }
              iprot.readListEnd();
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
    public void write(Dependencies value, TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      oprot.writeFieldBegin(START_TS_FIELD_DESC);
      oprot.writeI64(value.startTs);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(END_TS_FIELD_DESC);
      oprot.writeI64(value.endTs);
      oprot.writeFieldEnd();

      oprot.writeFieldBegin(LINKS_FIELD_DESC);
      oprot.writeListBegin(new TList(TType.STRUCT, value.links.size()));
      for (int i = 0, length = value.links.size(); i < length; i++) {
        DependencyLinkAdapter.INSTANCE.write(value.links.get(i), oprot);
      }
      oprot.writeListEnd();
      oprot.writeFieldEnd();

      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }
  }

  @Override
  public Dependencies readDependencies(byte[] bytes) {
    return read(DependenciesAdapter.INSTANCE, bytes);
  }

  @Override
  public byte[] writeDependencies(Dependencies value) {
    return write(DependenciesAdapter.INSTANCE, value);
  }

  private static <T> T read(ThriftAdapter<T> adapter, byte[] bytes) {
    try {
      return adapter.read(new TBinaryProtocol(new TMemoryInputTransport(bytes)));
    } catch (Exception e) {
      return null;
    }
  }

  private static <T> byte[] write(ThriftAdapter<T> adapter, T value) {
    TMemoryBuffer transport = new TMemoryBuffer(0);
    TBinaryProtocol protocol = new TBinaryProtocol(transport);
    try {
      adapter.write(value, protocol);
    } catch (Exception e) {
      return null;
    }
    return transport.getArray();
  }
}
