/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import zipkin2.internal.Platform;

import static zipkin2.internal.HexCodec.HEX_DIGITS;

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
   * IPv4 endpoint address packed into 4 bytes or null if unknown.
   *
   * @see #ipv6()
   * @see java.net.Inet4Address#getAddress()
   */
  @Nullable public byte[] ipv4Bytes() {
    return ipv4Bytes;
  }

  /**
   * The text representation of the primary IPv6 address associated with this a connection. Ex.
   * 2001:db8::c001 Absent if unknown.
   *
   * @see #ipv4() for mapped addresses
   * @see #ipv6Bytes()
   */
  @Nullable public String ipv6() {
    return ipv6;
  }

  /**
   * IPv6 endpoint address packed into 16 bytes or null if unknown.
   *
   * @see #ipv6()
   * @see java.net.Inet6Address#getAddress()
   */
  @Nullable public byte[] ipv6Bytes() {
    return ipv6Bytes;
  }

  /**
   * Port of the IP's socket or null, if not known.
   *
   * @see java.net.InetSocketAddress#getPort()
   */
  @Nullable public Integer port() {
    return port != 0 ? port : null;
  }

  /**
   * Like {@link #port()} except returns a primitive where zero implies absent.
   *
   * <p>Using this method will avoid allocation, so is encouraged when copying data.
   */
  public int portAsInt() {
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
    byte[] ipv4Bytes, ipv6Bytes;
    int port; // zero means null

    Builder(Endpoint source) {
      serviceName = source.serviceName;
      ipv4 = source.ipv4;
      ipv6 = source.ipv6;
      ipv4Bytes = source.ipv4Bytes;
      ipv6Bytes = source.ipv6Bytes;
      port = source.port;
    }

    Builder merge(Endpoint source) {
      if (serviceName == null) serviceName = source.serviceName;
      if (ipv4 == null) ipv4 = source.ipv4;
      if (ipv6 == null) ipv6 = source.ipv6;
      if (ipv4Bytes == null) ipv4Bytes = source.ipv4Bytes;
      if (ipv6Bytes == null) ipv6Bytes = source.ipv6Bytes;
      if (port == 0) port = source.port;
      return this;
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
        ipv4Bytes = addr.getAddress();
      } else if (addr instanceof Inet6Address) {
        byte[] addressBytes = addr.getAddress();
        if (!parseEmbeddedIPv4(addressBytes)) {
          ipv6 = writeIpV6(addressBytes);
          ipv6Bytes = addressBytes;
        }
      } else {
        return false;
      }
      return true;
    }

    /**
     * Like {@link #parseIp(String)} except this accepts a byte array.
     *
     * @param ipBytes byte array whose ownership is exclusively transferred to this endpoint.
     */
    public final boolean parseIp(byte[] ipBytes) {
      if (ipBytes == null) return false;
      if (ipBytes.length == 4) {
        ipv4Bytes = ipBytes;
        ipv4 = writeIpV4(ipBytes);
      } else if (ipBytes.length == 16) {
        if (!parseEmbeddedIPv4(ipBytes)) {
          ipv6 = writeIpV6(ipBytes);
          ipv6Bytes = ipBytes;
        }
      } else {
        return false;
      }
      return true;
    }

    static String writeIpV4(byte[] ipBytes) {
      char[] buf = Platform.shortStringBuffer();
      int pos = 0;
      pos = writeBackwards(ipBytes[0] & 0xff, pos, buf);
      buf[pos++] = '.';
      pos = writeBackwards(ipBytes[1] & 0xff, pos, buf);
      buf[pos++] = '.';
      pos = writeBackwards(ipBytes[2] & 0xff, pos, buf);
      buf[pos++] = '.';
      pos = writeBackwards(ipBytes[3] & 0xff, pos, buf);
      return new String(buf, 0, pos);
    }

    static int writeBackwards(int b, int pos, char[] buf) {
      if (b < 10) {
        buf[pos] = HEX_DIGITS[b];
        return pos + 1;
      }
      int i = pos += b < 100 ? 2 : 3; // We write backwards from right to left.
      while (b != 0) {
        int digit = b % 10;
        buf[--i] = HEX_DIGITS[digit];
        b /= 10;
      }
      return pos;
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
        ipv4Bytes = getIpv4Bytes(ipv4);
      } else if (format == IpFamily.IPv4Embedded) {
        ipv4 = ipString.substring(ipString.lastIndexOf(':') + 1);
        ipv4Bytes = getIpv4Bytes(ipv4);
      } else if (format == IpFamily.IPv6) {
        byte[] addressBytes = textToNumericFormatV6(ipString);
        if (addressBytes == null) return false;
        ipv6 = writeIpV6(addressBytes); // ensures consistent format
        ipv6Bytes = addressBytes;
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
        if (port <= 0) port = 0;
      }
      this.port = port != null ? port : 0;
      return this;
    }

    /** @see Endpoint#portAsInt() */
    public Builder port(int port) {
      if (port > 0xffff) throw new IllegalArgumentException("invalid port " + port);
      if (port < 0) port = 0;
      this.port = port;
      return this;
    }

    public Endpoint build() {
      return new Endpoint(this);
    }

    Builder() {
    }

    boolean parseEmbeddedIPv4(byte[] ipv6) {
      for (int i = 0; i < 10; i++) { // Embedded IPv4 addresses start with unset 80 bits
        if (ipv6[i] != 0) return false;
      }

      int flag = (ipv6[10] & 0xff) << 8 | (ipv6[11] & 0xff);
      if (flag != 0) return false; // IPv4-Compatible or IPv4-Mapped

      byte o1 = ipv6[12], o2 = ipv6[13], o3 = ipv6[14], o4 = ipv6[15];
      if (o1 == 0 && o2 == 0 && o3 == 0 && o4 == 1) {
        return false; // ::1 is localhost, not an embedded compat address
      }

      ipv4 = String.valueOf(o1 & 0xff) + '.' + (o2 & 0xff) + '.' + (o3 & 0xff) + '.' + (o4 & 0xff);
      ipv4Bytes = new byte[] {o1, o2, o3, o4};
      return true;
    }
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
        int lastColonIndex = ipString.lastIndexOf(':');
        if (!isValidIpV4Address(ipString, lastColonIndex + 1, ipString.length())) {
          return IpFamily.Unknown;
        }
        if (lastColonIndex == 1 && ipString.charAt(0) == ':') {// compressed like ::1.2.3.4
          return IpFamily.IPv4Embedded;
        }
        if (lastColonIndex != 6 || ipString.charAt(0) != ':' || ipString.charAt(1) != ':') {
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

  static boolean notHex(char c) {
    return (c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F');
  }

  static String writeIpV6(byte[] ipv6) {
    int pos = 0;
    char[] buf = Platform.shortStringBuffer();

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

  // Begin code from com.google.common.net.InetAddresses 23
  static final int IPV6_PART_COUNT = 8;

  @Nullable
  static byte[] textToNumericFormatV6(String ipString) {
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

  static short parseHextet(String ipPart) {
    // Note: we already verified that this string contains only hex digits.
    int hextet = Integer.parseInt(ipPart, 16);
    if (hextet > 0xffff) {
      throw new NumberFormatException();
    }
    return (short) hextet;
  }
  // End code from com.google.common.net.InetAddresses 23

  // Begin code from io.netty.util.NetUtil 4.1
  static boolean isValidIpV4Address(String ip, int from, int toExcluded) {
    int len = toExcluded - from;
    int i;
    return len <= 15 && len >= 7 &&
      (i = ip.indexOf('.', from + 1)) > 0 && isValidIpV4Word(ip, from, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      (i = ip.indexOf('.', from = i + 2)) > 0 && isValidIpV4Word(ip, from - 1, i) &&
      isValidIpV4Word(ip, i + 1, toExcluded);
  }

  static boolean isValidIpV4Word(CharSequence word, int from, int toExclusive) {
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

  static boolean isValidNumericChar(char c) {
    return c >= '0' && c <= '9';
  }
  // End code from io.netty.util.NetUtil 4.1

  // clutter below mainly due to difficulty working with Kryo which cannot handle AutoValue subclass
  // See https://github.com/openzipkin/zipkin/issues/1879
  final String serviceName, ipv4, ipv6;
  final byte[] ipv4Bytes, ipv6Bytes;
  final int port;

  Endpoint(Builder builder) {
    serviceName = builder.serviceName;
    ipv4 = builder.ipv4;
    ipv4Bytes = builder.ipv4Bytes;
    ipv6 = builder.ipv6;
    ipv6Bytes = builder.ipv6Bytes;
    port = builder.port;
  }

  Endpoint(SerializedForm serializedForm) {
    serviceName = serializedForm.serviceName;
    ipv4 = serializedForm.ipv4;
    ipv4Bytes = serializedForm.ipv4Bytes;
    ipv6 = serializedForm.ipv6;
    ipv6Bytes = serializedForm.ipv6Bytes;
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
      && port == that.port;
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
    h ^= port;
    return h;
  }

  // As this is an immutable object (no default constructor), defer to a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(this);
  }

  // TODO: replace this with native proto3 encoding
  static final class SerializedForm implements Serializable {
    static final long serialVersionUID = 0L;

    final String serviceName, ipv4, ipv6;
    final byte[] ipv4Bytes, ipv6Bytes;
    final int port;

    SerializedForm(Endpoint endpoint) {
      serviceName = endpoint.serviceName;
      ipv4 = endpoint.ipv4;
      ipv4Bytes = endpoint.ipv4Bytes;
      ipv6 = endpoint.ipv6;
      ipv6Bytes = endpoint.ipv6Bytes;
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

  static byte[] getIpv4Bytes(String ipv4) {
    byte[] result = new byte[4];
    int pos = 0;
    for (int i = 0, len = ipv4.length(); i < len; ) {
      char ch = ipv4.charAt(i++);
      int octet = ch - '0';
      if (i == len || (ch = ipv4.charAt(i++)) == '.') {
        // then we have a single digit octet
        result[pos++] = (byte) octet;
        continue;
      }
      // push the decimal
      octet = (octet * 10) + (ch - '0');
      if (i == len || (ch = ipv4.charAt(i++)) == '.') {
        // then we have a two digit octet
        result[pos++] = (byte) octet;
        continue;
      }
      // otherwise, we have a three digit octet
      octet = (octet * 10) + (ch - '0');
      result[pos++] = (byte) octet;
      i++; // skip the dot
    }
    return result;
  }
}
