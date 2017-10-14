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
package zipkin2.elasticsearch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import zipkin2.internal.Nullable;

/**
 * This returns a Dns provider that combines the IPv4 or IPv6 addresses from a supplied list of
 * urls, provided they are all http and share the same port.
 */
final class PseudoAddressRecordSet {

  static Dns create(List<String> urls, Dns actualDns) {
    Set<String> schemes = new LinkedHashSet<>();
    Set<String> hosts = new LinkedHashSet<>();
    Set<InetAddress> ipAddresses = new LinkedHashSet<>();
    Set<Integer> ports = new LinkedHashSet<>();

    for (String url : urls) {
      HttpUrl httpUrl = HttpUrl.parse(url);
      schemes.add(httpUrl.scheme());

      // Kick out if we can't cheaply read the address
      byte[] addressBytes = null;
      try {
        addressBytes = ipStringToBytes(httpUrl.host());
      } catch (RuntimeException e) {
      }

      if (addressBytes != null) {
        try {
          ipAddresses.add(InetAddress.getByAddress(addressBytes));
        } catch (UnknownHostException e) {
          hosts.add(httpUrl.host());
        }
      } else {
        hosts.add(httpUrl.host());
      }
      ports.add(httpUrl.port());
    }

    if (ports.size() != 1) {
      throw new IllegalArgumentException("Only one port supported with multiple hosts " + urls);
    }
    if (schemes.size() != 1 || !schemes.iterator().next().equals("http")) {
      throw new IllegalArgumentException("Only http supported with multiple hosts " + urls);
    }

    if (hosts.isEmpty()) return new StaticDns(ipAddresses);
    return new ConcatenatingDns(ipAddresses, hosts, actualDns);
  }

  static final class StaticDns implements Dns {
    private final List<InetAddress> ipAddresses;

    StaticDns(Set<InetAddress> ipAddresses) {
      this.ipAddresses = new ArrayList<>(ipAddresses);
    }

    @Override public List<InetAddress> lookup(String hostname) {
      return ipAddresses;
    }

    @Override public String toString() {
      return "StaticDns(" + ipAddresses + ")";
    }
  }

  static final class ConcatenatingDns implements Dns {
    final Set<InetAddress> ipAddresses;
    final Set<String> hosts;
    final Dns actualDns;

    ConcatenatingDns(Set<InetAddress> ipAddresses, Set<String> hosts, Dns actualDns) {
      this.ipAddresses = ipAddresses;
      this.hosts = hosts;
      this.actualDns = actualDns;
    }

    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
      List<InetAddress> result = new ArrayList<>(ipAddresses.size() + hosts.size());
      result.addAll(ipAddresses);
      for (String host : hosts) {
        result.addAll(actualDns.lookup(host));
      }
      return result;
    }

    @Override public String toString() {
      return "ConcatenatingDns(" + ipAddresses + "," + hosts + ")";
    }
  }

  //** Start code from Guava v20 **//
  private static final int IPV4_PART_COUNT = 4;
  private static final int IPV6_PART_COUNT = 8;

  /**
   * Returns the {@link InetAddress#getAddress()} having the given string representation or null if
   * unable to parse.
   *
   * <p>This deliberately avoids all nameservice lookups (e.g. no DNS).
   *
   * <p>This is the same as com.google.common.net.InetAddresses.ipStringToBytes(), except internally
   * Splitter isn't used (as that would introduce more dependencies).
   *
   * @param ipString {@code String} containing an IPv4 or IPv6 string literal, e.g. {@code
   * "192.168.0.1"} or {@code "2001:db8::1"}
   */
  @Nullable
  static byte[] ipStringToBytes(String ipString) {
    // PATCHED! adding null/empty escape
    if (ipString == null || ipString.isEmpty()) return null;
    // Make a first pass to categorize the characters in this string.
    boolean hasColon = false;
    boolean hasDot = false;
    for (int i = 0; i < ipString.length(); i++) {
      char c = ipString.charAt(i);
      if (c == '.') {
        hasDot = true;
      } else if (c == ':') {
        if (hasDot) {
          return null; // Colons must not appear after dots.
        }
        hasColon = true;
      } else if (Character.digit(c, 16) == -1) {
        return null; // Everything else must be a decimal or hex digit.
      }
    }

    // Now decide which address family to parse.
    if (hasColon) {
      if (hasDot) {
        ipString = convertDottedQuadToHex(ipString);
        if (ipString == null) {
          return null;
        }
      }
      return textToNumericFormatV6(ipString);
    } else if (hasDot) {
      return textToNumericFormatV4(ipString);
    }
    return null;
  }

  @Nullable
  private static byte[] textToNumericFormatV4(String ipString) {
    byte[] bytes = new byte[IPV4_PART_COUNT];
    int i = 0;
    try {
      // PATCHED! for (String octet : IPV4_SPLITTER.split(ipString)) {
      for (String octet : ipString.split("\\.", 5)) {
        bytes[i++] = parseOctet(octet);
      }
    } catch (NumberFormatException ex) {
      return null;
    }

    return i == IPV4_PART_COUNT ? bytes : null;
  }

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

  @Nullable
  private static String convertDottedQuadToHex(String ipString) {
    int lastColon = ipString.lastIndexOf(':');
    String initialPart = ipString.substring(0, lastColon + 1);
    String dottedQuad = ipString.substring(lastColon + 1);
    byte[] quad = textToNumericFormatV4(dottedQuad);
    if (quad == null) {
      return null;
    }
    String penultimate = Integer.toHexString(((quad[0] & 0xff) << 8) | (quad[1] & 0xff));
    String ultimate = Integer.toHexString(((quad[2] & 0xff) << 8) | (quad[3] & 0xff));
    return initialPart + penultimate + ":" + ultimate;
  }

  private static byte parseOctet(String ipPart) {
    // Note: we already verified that this string contains only hex digits.
    int octet = Integer.parseInt(ipPart);
    // Disallow leading zeroes, because no clear standard exists on
    // whether these should be interpreted as decimal or octal.
    if (octet > 255 || (ipPart.startsWith("0") && ipPart.length() > 1)) {
      throw new NumberFormatException();
    }
    return (byte) octet;
  }

  private static short parseHextet(String ipPart) {
    // Note: we already verified that this string contains only hex digits.
    int hextet = Integer.parseInt(ipPart, 16);
    if (hextet > 0xffff) {
      throw new NumberFormatException();
    }
    return (short) hextet;
  }
  //** End code from Guava v20 **//
}
