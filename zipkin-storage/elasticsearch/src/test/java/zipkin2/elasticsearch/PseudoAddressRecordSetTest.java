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

import com.google.common.net.InetAddresses;
import java.net.UnknownHostException;
import okhttp3.Dns;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class PseudoAddressRecordSetTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  Dns underlying = hostname -> {
    throw new UnsupportedOperationException();
  };

  @Test public void mixedPortsNotSupported() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
      "Only one port supported with multiple hosts [http://1.1.1.1:9200, http://2.2.2.2:9201]");

    PseudoAddressRecordSet.create(asList("http://1.1.1.1:9200", "http://2.2.2.2:9201"), underlying);
  }

  @Test public void httpsNotSupported() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
      "Only http supported with multiple hosts [https://1.1.1.1:9200, https://2.2.2.2:9200]");

    PseudoAddressRecordSet.create(asList("https://1.1.1.1:9200", "https://2.2.2.2:9200"),
      underlying);
  }

  @Test public void concatenatesIPv4List() throws UnknownHostException {
    Dns result = PseudoAddressRecordSet.create(asList("http://1.1.1.1:9200", "http://2.2.2.2:9200"),
      underlying);

    assertThat(result).isInstanceOf(PseudoAddressRecordSet.StaticDns.class);
    assertThat(result.lookup("foo"))
      .containsExactly(InetAddresses.forString("1.1.1.1"), InetAddresses.forString("2.2.2.2"));
  }

  @Test public void onlyLooksUpHostnames() throws UnknownHostException {
    underlying = hostname -> {
      assertThat(hostname).isEqualTo("myhost");
      return asList(InetAddresses.forString("2.2.2.2"));
    };

    Dns result = PseudoAddressRecordSet.create(asList("http://1.1.1.1:9200", "http://myhost:9200"),
      underlying);

    assertThat(result.lookup("foo"))
      .containsExactly(InetAddresses.forString("1.1.1.1"), InetAddresses.forString("2.2.2.2"));
  }

  @Test public void concatenatesMixedIpLengths() throws UnknownHostException {
    Dns result =
      PseudoAddressRecordSet.create(asList("http://1.1.1.1:9200", "http://[2001:db8::c001]:9200"),
        underlying);

    assertThat(result).isInstanceOf(PseudoAddressRecordSet.StaticDns.class);
    assertThat(result.lookup("foo"))
      .containsExactly(InetAddresses.forString("1.1.1.1"),
        InetAddresses.forString("2001:db8::c001"));
  }
}
