/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Locale;
import zipkin2.internal.Nullable;

/** The network context of a node in the service graph. */
//@Immutable
public final class Endpoint implements Serializable { // for Spark and Flink jobs
  private static final long serialVersionUID = 0L;

  /**
   * Lower-case label of this node in the service graph, such as "favstar". Leave absent if
   * unknown.
   *
   * <p>This is a primary label for trace lookup and aggregation, so it should be intuitive and
   * consistent. Many use a name from service discovery.
   */
  @Nullable public String serviceName() {
    return serviceName;
  }

  /**
   * The text representation of the primary IPv4 address associated with this a connection. Ex.
   * 192.168.99.100 Absent if unknown.
   */
  @Nullable public String ipv4() {
    return ipv4;
  }

  /**
   * The text representation of the primary IPv6 address associated with this a connection. Ex.
   * 2001:db8::c001 Absent if unknown.
   *
   * <p>Prefer using the {@link #ipv4()} field for mapped addresses.
   */
  @Nullable public String ipv6() {
    return ipv6;
  }

  /**
   * Port of the IP's socket or null, if not known.
   *
   * @see java.net.InetSocketAddress#getPort()
   */
  @Nullable public Integer port() {
    return port;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    String serviceName, ipv4, ipv6;
    Integer port;

    Builder(Endpoint source) {
      serviceName = source.serviceName;
      ipv4 = source.ipv4;
      ipv6 = source.ipv6;
      port = source.port;
    }

    /** @see Endpoint#serviceName */
    public Builder serviceName(@Nullable String serviceName) {
      this.serviceName = serviceName == null || serviceName.isEmpty()
        ? null : serviceName.toLowerCase(Locale.ROOT);
      return this;
    }

    /** Chaining variant of {@link #parseIp(InetAddress)} */
    public Builder ip(@Nullable InetAddress addr) {
      parseIp(addr);
      return this;
    }

    /**
     * Returns true if {@link Endpoint#ipv4()} or {@link Endpoint#ipv6()} could be parsed from the
     * input.
     *
     * <p>Returns boolean not this for conditional parsing. For example:
     * <pre>{@code
     * if (!builder.parseIp(input.getHeader("X-Forwarded-For"))) {
     *   builder.parseIp(input.getRemoteAddr());
     * }
     * }</pre>
     *
     * @see #parseIp(String)
     */
    public final boolean parseIp(@Nullable InetAddress addr) {
      if (addr == null) return false;
      if (addr instanceof Inet4Address) {
        ipv4 = addr.getHostAddress();
      } else if (addr instanceof Inet6Address) {
        byte[] addressBytes = addr.getAddress();
        String ipv4 = parseEmbeddedIPv4(addressBytes);
        if (ipv4 != null) {
          this.ipv4 = ipv4;
        } else {
          ipv6 = writeIpV6(addressBytes);
        }
      } else {
        return false;
      }
      return true;
    }

    /** Chaining variant of {@link #parseIp(String)} */
    public Builder ip(@Nullable String ipString) {
      parseIp(ipString);
      return this;
    }

    /**
     * Returns true if {@link Endpoint#ipv4()} or {@link Endpoint#ipv6()} could be parsed from the
     * input.
     *
     * <p>Returns boolean not this for conditional parsing. For example:
     * <pre>{@code
     * if (!builder.parseIp(input.getHeader("X-Forwarded-For"))) {
     *   builder.parseIp(input.getRemoteAddr());
     * }
     * }</pre>
     *
     * @see #parseIp(InetAddress)
     */
    public final boolean parseIp(@Nullable String ipString) {
      if (ipString == null || ipString.isEmpty()) return false;
      IpFamily format = detectFamily(ipString);
      if (format == IpFamily.IPv4) {
        ipv4 = ipString;
      } else if (format == IpFamily.IPv4Embedded) {
        ipv4 = ipString.substring(ipString.lastIndexOf(':') + 1);
      } else if (format == IpFamily.IPv6) {
        byte[] addressBytes = textToNumericFormatV6(ipString);
        if (addressBytes == null) return false;
        ipv6 = writeIpV6(addressBytes); // ensures consistent format
      } else {
        return false;
      }
      return true;
    }

    /**
     * Use this to set the port to an externally defined value.
     *
     * @param port port associated with the endpoint. zero coerces to null (unknown)
     * @see Endpoint#port()
     */
    public Builder port(@Nullable Integer port) {
      if (port != null) {
        if (port > 0xffff) throw new IllegalArgumentException("invalid port " + port);
        if (port <= 0) port = null;
      }
      this.port = port;
      return this;
    }

    public Endpoint build() {
      return new Endpoint(this);
    }

    Builder() {
    }
  }

  static @Nullable String parseEmbeddedIPv4(byte[] ipv6) {
    for (int i = 0; i < 10; i++) { // Embedded IPv4 addresses start with unset 80 bits
      if (ipv6[i] != 0) return null;
    }

    int flag = (ipv6[10] & 0xff) << 8 | (ipv6[11] & 0xff);
    if (flag != 0 && flag != -1) return null; // IPv4-Compatible or IPv4-Mapped

    int o1 = ipv6[12] & 0xff, o2 = ipv6[13] & 0xff, o3 = ipv6[14] & 0xff, o4 = ipv6[15] & 0xff;
    if (flag == 0 && o1 == 0 && o2 == 0 && o3 == 0 && o4 == 1) {
      return null; // ::1 is localhost, not an embedded compat address
    }

    return String.valueOf(o1) + '.' + o2 + '.' + o3 + '.' + o4;
  }

  enum IpFamily {
    Unknown,
    IPv4,
    IPv4Embedded,
    IPv6
  }

  /**
   * Adapted from code in {@code com.google.common.net.InetAddresses.ipStringToBytes}. This version
   * separates detection from parsing and checks more carefully about embedded addresses.
   */
  static IpFamily detectFamily(String ipString) {
    boolean hasColon = false;
    boolean hasDot = false;
    for (int i = 0, length = ipString.length(); i < length; i++) {
      char c = ipString.charAt(i);
      if (c == '.') {
        hasDot = true;
      } else if (c == ':') {
        if (hasDot) return IpFamily.Unknown; // Colons must not appear after dots.
        hasColon = true;
      } else if (notHex(c)) {
        return IpFamily.Unknown; // Everything else must be a decimal or hex digit.
      }
    }

    // Now decide which address family to parse.
    if (hasColon) {
      if (hasDot) {
        int lastColon = ipString.lastIndexOf(':');
        if (!isValidIpV4Address(ipString, lastColon + 1, ipString.length())) {
          return IpFamily.Unknown;
        }
        if (lastColon == 1 && ipString.charAt(0) == ':') {// compressed like ::1.2.3.4
          return IpFamily.IPv4Embedded;
        }
        if (lastColon != 6 || ipString.charAt(0) != ':' || ipString.charAt(1) != ':') {
          return IpFamily.Unknown;
        }
        for (int i = 2; i < 6; i++) {
          char c = ipString.charAt(i);
          if (c != 'f' && c != 'F' && c != '0') return IpFamily.Unknown;
        }
        return IpFamily.IPv4Embedded;
      }
      return IpFamily.IPv6;
    } else if (hasDot && isValidIpV4Address(ipString, 0, ipString.length())) {
      return IpFamily.IPv4;
    }
    return IpFamily.Unknown;
  }

  private static boolean notHex(char c) {
    return (c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F');
  }

  private static final ThreadLocal<char[]> IPV6_TO_STRING = new ThreadLocal<char[]>() {
    @Override protected char[] initialValue() {
      return new char[39]; // maximum length of encoded ipv6
    }
  };

  static String writeIpV6(byte[] ipv6) {
    int pos = 0;
    char[] buf = IPV6_TO_STRING.get();

    // Compress the longest string of zeros
    int zeroCompressionIndex = -1;
    int zeroCompressionLength = -1;
    int zeroIndex = -1;
    boolean allZeros = true;
    for (int i = 0; i < ipv6.length; i += 2) {
      if (ipv6[i] == 0 && ipv6[i + 1] == 0) {
        if (zeroIndex < 0) zeroIndex = i;
        continue;
      }
      allZeros = false;
      if (zeroIndex >= 0) {
        int zeroLength = i - zeroIndex;
        if (zeroLength > zeroCompressionLength) {
          zeroCompressionIndex = zeroIndex;
          zeroCompressionLength = zeroLength;
        }
        zeroIndex = -1;
      }
    }

    // handle all zeros: 0:0:0:0:0:0:0:0 -> ::
    if (allZeros) return "::";

    // handle trailing zeros: 2001:0:0:4:0:0:0:0 -> 2001:0:0:4::
    if (zeroCompressionIndex == -1 && zeroIndex != -1) {
      zeroCompressionIndex = zeroIndex;
      zeroCompressionLength = 16 - zeroIndex;
    }

    int i = 0;
    while (i < ipv6.length) {
      if (i == zeroCompressionIndex) {
        buf[pos++] = ':';
        i += zeroCompressionLength;
        if (i == ipv6.length) buf[pos++] = ':';
        continue;
      }
      if (i != 0) buf[pos++] = ':';

      byte high = ipv6[i++];
      byte low = ipv6[i++];

      // handle leading zeros: 2001:0:0:4:0000:0:0:8 -> 2001:0:0:4::8
      boolean leadingZero;
      char val = HEX_DIGITS[(high >> 4) & 0xf];
      if (!(leadingZero = val == '0')) buf[pos++] = val;
      val = HEX_DIGITS[high & 0xf];
      if (!(leadingZero = (leadingZero && val == '0'))) buf[pos++] = val;
      val = HEX_DIGITS[(low >> 4) & 0xf];
      if (!(leadingZero && val == '0')) buf[pos++] = val;
      buf[pos++] = HEX_DIGITS[low & 0xf];
    }
    return new String(buf, 0, pos);
  }

  static final char[] HEX_DIGITS =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  // Begin code from com.google.common.net.InetAddresses 23
  private static final int IPV6_PART_COUNT = 8;

  @Nullable
  private static byte[] textToNumericFormatV6(String ipString) {
    // An address can have [2..8] colons, and N colons make N+1 parts.
    String[] parts = ipString.split(":", IPV6_PART_COUNT + 2);
    if (parts.length < 3 || parts.length > IPV6_PART_COUNT + 1) {
      return null;
    }

    // Disregarding the endpoints, find "::" with nothing in between.
    // This indicates that a run of zeroes has been skipped.
    int skipIndex = -1;
    for (int i = 1; i < parts.length - 1; i++) {
      if (parts[i].length() == 0) {
        if (skipIndex >= 0) {
          return null; // Can't have more than one ::
        }
        skipIndex = i;
      }
    }

    int partsHi; // Number of parts to copy from above/before the "::"
    int partsLo; // Number of parts to copy from below/after the "::"
    if (skipIndex >= 0) {
      // If we found a "::", then check if it also covers the endpoints.
      partsHi = skipIndex;
      partsLo = parts.length - skipIndex - 1;
      if (parts[0].length() == 0 && --partsHi != 0) {
        return null; // ^: requires ^::
      }
      if (parts[parts.length - 1].length() == 0 && --partsLo != 0) {
        return null; // :$ requires ::$
      }
    } else {
      // Otherwise, allocate the entire address to partsHi. The endpoints
      // could still be empty, but parseHextet() will check for that.
      partsHi = parts.length;
      partsLo = 0;
    }

    // If we found a ::, then we must have skipped at least one part.
    // Otherwise, we must have exactly the right number of parts.
    int partsSkipped = IPV6_PART_COUNT - (partsHi + partsLo);
    if (!(skipIndex >= 0 ? partsSkipped >= 1 : partsSkipped == 0)) {
      return null;
    }

    // Now parse the hextets into a byte array.
    ByteBuffer rawBytes = ByteBuffer.allocate(2 * IPV6_PART_COUNT);
    try {
      for (int i = 0; i < partsHi; i++) {
        rawBytes.putShort(parseHextet(parts[i]));
      }
      for (int i = 0; i < partsSkipped; i++) {
        rawBytes.putShort((short) 0);
      }
      for (int i = partsLo; i > 0; i--) {
        rawBytes.putShort(parseHextet(parts[parts.length - i]));
      }
    } catch (NumberFormatException ex) {
      return null;
    }
    return rawBytes.array();
  }

  private static short parseHextet(String ipPart) {
    // Note: we already verified that this string contains only hex digits.
    int hextet = Integer.parseInt(ipPart, 16);
    if (hextet > 0xffff) {
      throw new NumberFormatException();
    }
    return (short) hextet;
  }
  // End code from com.google.common.net.InetAddresses 23

  // Begin code from io.netty.util.NetUtil 4.1
  private static boolean isValidIpV4Address(String ip, int from, int toExcluded) {
    int len = toExcluded - from;
    int i;
    return len <= 15 && len >= 7 &&
      (i = ip.indexOf('.', from + 1)) > 0 && isValidIpV4Word(ip, from, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      isValidIpV4Word(ip, i + 1, toExcluded);
  }

  private static boolean isValidIpV4Word(CharSequence word, int from, int toExclusive) {
    int len = toExclusive - from;
    char c0, c1, c2;
    if (len < 1 || len > 3 || (c0 = word.charAt(from)) < '0') {
      return false;
    }
    if (len == 3) {
      return (c1 = word.charAt(from + 1)) >= '0' &&
        (c2 = word.charAt(from + 2)) >= '0' &&
        ((c0 <= '1' && c1 <= '9' && c2 <= '9') ||
          (c0 == '2' && c1 <= '5' && (c2 <= '5' || (c1 < '5' && c2 <= '9'))));
    }
    return c0 <= '9' && (len == 1 || isValidNumericChar(word.charAt(from + 1)));
  }

  private static boolean isValidNumericChar(char c) {
    return c >= '0' && c <= '9';
  }
  // End code from io.netty.util.NetUtil 4.1

  // clutter below mainly due to difficulty working with Kryo which cannot handle AutoValue subclass
  // See https://github.com/openzipkin/zipkin/issues/1879
  final String serviceName, ipv4, ipv6;
  final Integer port;

  Endpoint(Builder builder) {
    serviceName = builder.serviceName;
    ipv4 = builder.ipv4;
    ipv6 = builder.ipv6;
    port = builder.port;
  }

  Endpoint(SerializedForm serializedForm) {
    serviceName = serializedForm.serviceName;
    ipv4 = serializedForm.ipv4;
    ipv6 = serializedForm.ipv6;
    port = serializedForm.port;
  }

  @Override public String toString() {
    return "Endpoint{"
      + "serviceName=" + serviceName + ", "
      + "ipv4=" + ipv4 + ", "
      + "ipv6=" + ipv6 + ", "
      + "port=" + port
      + "}";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Endpoint)) return false;
    Endpoint that = (Endpoint) o;
    return ((serviceName == null)
      ? (that.serviceName == null) : serviceName.equals(that.serviceName))
      && ((ipv4 == null) ? (that.ipv4 == null) : ipv4.equals(that.ipv4))
      && ((ipv6 == null) ? (that.ipv6 == null) : ipv6.equals(that.ipv6))
      && ((port == null) ? (that.port == null) : port.equals(that.port));
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (serviceName == null) ? 0 : serviceName.hashCode();
    h *= 1000003;
    h ^= (ipv4 == null) ? 0 : ipv4.hashCode();
    h *= 1000003;
    h ^= (ipv6 == null) ? 0 : ipv6.hashCode();
    h *= 1000003;
    h ^= (port == null) ? 0 : port.hashCode();
    return h;
  }

  // As this is an immutable object (no default constructor), defer to a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(this);
  }

  private static final class SerializedForm implements Serializable {
    static final long serialVersionUID = 0L;

    final String serviceName, ipv4, ipv6;
    final Integer port;

    SerializedForm(Endpoint endpoint) {
      serviceName = endpoint.serviceName;
      ipv4 = endpoint.ipv4;
      ipv6 = endpoint.ipv6;
      port = endpoint.port;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return new Endpoint(this);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
