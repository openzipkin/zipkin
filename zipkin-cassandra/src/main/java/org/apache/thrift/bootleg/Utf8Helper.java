package org.apache.thrift.bootleg;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Apache Thrift
 * Copyright 2006-2009 The Apache Software Foundation, et al.
 *
 * This product includes software developed at
 * The Apache Software Foundation (http://www.apache.org/).
 */

/**
 * Stolen without mercy from https://issues.apache.org/jira/browse/THRIFT-765
 */
@SuppressWarnings("all")
@Deprecated
public final class Utf8Helper {
  private Utf8Helper() {}

  @Deprecated
  public static final int getByteLength(final String s) {
    int byteLength = 0;
    int codePoint;
    for (int i = 0; i < s.length(); i++) {
      codePoint = s.charAt(i);
      if (codePoint >= 0x07FF) {
        codePoint = s.codePointAt(i);
        if (Character.isSupplementaryCodePoint(codePoint)) {
          i++;
        }
      }
      if (codePoint >= 0 && codePoint <= 0x007F) {
        byteLength++;
      } else if (codePoint >= 0x80 && codePoint <= 0x07FF) {
        byteLength += 2;
      } else if ((codePoint >= 0x0800 && codePoint < 0xD800) || (codePoint > 0xDFFF && codePoint <= 0xFFFD)) {
        byteLength+=3;
      } else if (codePoint >= 0x10000 && codePoint <= 0x10FFFF) {
        byteLength+=4;
      } else {
        throw new RuntimeException("Unknown unicode codepoint in string! "
            + Integer.toHexString(codePoint));
      }
    }
    return byteLength;
  }

  @Deprecated
  public static byte[] encode(String s) {
    byte[] buf = new byte[getByteLength(s)];
    encode(s, buf, 0);
    return buf;
  }

  @Deprecated
  public static void encode(final String s, final byte[] buf, final int offset) {
    int nextByte = 0;
    int codePoint;
    final int strLen = s.length();
    for (int i = 0; i < strLen; i++) {
      codePoint = s.charAt(i);
      if (codePoint >= 0x07FF) {
        codePoint = s.codePointAt(i);
        if (Character.isSupplementaryCodePoint(codePoint)) {
          i++;
        }
      }
      if (codePoint <= 0x007F) {
        buf[offset + nextByte] = (byte)codePoint;
        nextByte++;
      } else if (codePoint <= 0x7FF) {
        buf[offset + nextByte    ] = (byte)(0xC0 | ((codePoint >> 6) & 0x1F));
        buf[offset + nextByte + 1] = (byte)(0x80 | ((codePoint >> 0) & 0x3F));
        nextByte+=2;
      } else if ((codePoint < 0xD800) || (codePoint > 0xDFFF && codePoint <= 0xFFFD)) {
        buf[offset + nextByte    ] = (byte)(0xE0 | ((codePoint >> 12) & 0x0F));
        buf[offset + nextByte + 1] = (byte)(0x80 | ((codePoint >>  6) & 0x3F));
        buf[offset + nextByte + 2] = (byte)(0x80 | ((codePoint >>  0) & 0x3F));
        nextByte+=3;
      } else if (codePoint >= 0x10000 && codePoint <= 0x10FFFF) {
        buf[offset + nextByte    ] = (byte)(0xF0 | ((codePoint >> 18) & 0x07));
        buf[offset + nextByte + 1] = (byte)(0x80 | ((codePoint >> 12) & 0x3F));
        buf[offset + nextByte + 2] = (byte)(0x80 | ((codePoint >>  6) & 0x3F));
        buf[offset + nextByte + 3] = (byte)(0x80 | ((codePoint >>  0) & 0x3F));
        nextByte+=4;
      } else {
        throw new RuntimeException("Unknown unicode codepoint in string! "
            + Integer.toHexString(codePoint));
      }
    }
  }

  @Deprecated
  public static String decode(byte[] buf) {
    char[] charBuf = new char[buf.length];
    int charsDecoded = decode(buf, 0, buf.length, charBuf);
    return new String(charBuf, 0, charsDecoded);
  }

  public static final int UNI_SUR_HIGH_START = 0xD800;
  public static final int UNI_SUR_HIGH_END = 0xDBFF;
  public static final int UNI_SUR_LOW_START = 0xDC00;
  public static final int UNI_SUR_LOW_END = 0xDFFF;
  public static final int UNI_REPLACEMENT_CHAR = 0xFFFD;

  private static final int HALF_BASE = 0x0010000;
  private static final long HALF_SHIFT = 10;
  private static final long HALF_MASK = 0x3FFL;

  @Deprecated
  public static int decode(final byte[] buf, final int offset, final int byteLength, final char[] charBuf) {
    int curByteIdx = offset;
    int endByteIdx = offset + byteLength;

    int curCharIdx = 0;

    while (curByteIdx < endByteIdx) {
      final int b = buf[curByteIdx++]&0xff;
      final int ch;

      if (b < 0xC0) {
        ch = b;
      } else if (b < 0xE0) {
        ch = ((b & 0x1F) << 6) + (buf[curByteIdx++] & 0x3F);
      } else if (b < 0xf0) {
        ch = ((b & 0xF) << 12) + ((buf[curByteIdx++] & 0x3F) << 6) + (buf[curByteIdx++] & 0x3F);
      } else {
        ch = ((b & 0x7) << 18) + ((buf[curByteIdx++]& 0x3F) << 12) + ((buf[curByteIdx++] & 0x3F) << 6) + (buf[curByteIdx++] & 0x3F);
      }

      if (ch <= 0xFFFF) {
        // target is a character <= 0xFFFF
        charBuf[curCharIdx++] = (char) ch;
      } else {
        // target is a character in range 0xFFFF - 0x10FFFF
        final int chHalf = ch - HALF_BASE;
        charBuf[curCharIdx++] = (char) ((chHalf >> HALF_SHIFT) + UNI_SUR_HIGH_START);
        charBuf[curCharIdx++] = (char) ((chHalf & HALF_MASK) + UNI_SUR_LOW_START);
      }
    }
    return curCharIdx;
  }
}
