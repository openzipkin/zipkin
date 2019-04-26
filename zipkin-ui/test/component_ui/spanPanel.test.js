/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  maybeMarkTransientError,
  formatAnnotationValue,
  formatTagValue,
  isDupeTag
} from '../../js/component_ui/spanPanel';
import {frontend} from './traceTestHelpers';

chai.config.truncateThreshold = 0;

describe('maybeMarkTransientError', () => {
  const row = {
    className: '',
    addClass(className) {this.className = className;}
  };

  it('should not add class when annotation is not error', () => {
    const anno = {timestamp: 100, value: 'cs', endpoint: frontend};

    maybeMarkTransientError(row, anno);
    row.className.should.equal('');
  });

  it('should add class when annotation is error', () => {
    const anno = {timestamp: 100, value: 'error', endpoint: frontend};

    maybeMarkTransientError(row, anno);
    row.className.should.equal('anno-error-transient');
  });

  // uses an actual value from Finagle
  it('should add class when annotation matches error', () => {
    const anno = {
      timestamp: 100,
      value: 'Server Send Error: TimeoutException: socket timed out',
      endpoint: frontend
    };

    maybeMarkTransientError(row, anno);
    row.className.should.equal('anno-error-transient');
  });
});

describe('formatAnnotationValue', () => {
  it('should return same value when string', () => {
    formatAnnotationValue('foo').should.equal('foo');
  });

  it('should return string when false', () => {
    formatAnnotationValue(false).should.equal('false');
  });

  it('should return string when true', () => {
    formatAnnotationValue(true).should.equal('true');
  });

  it('should format object as one-line json', () => {
    formatAnnotationValue({foo: 'bar'}).should.equal(
      '{&quot;foo&quot;:&quot;bar&quot;}'
    );
  });

  it('should format array as one-line json', () => {
    formatAnnotationValue([{foo: 'bar'}, {baz: 'qux'}]).should.equal(
      '[{&quot;foo&quot;:&quot;bar&quot;},{&quot;baz&quot;:&quot;qux&quot;}]'
    );
  });

  it('should format null as json', () => {
    formatAnnotationValue(null).should.equal('null');
  });

  it('should escape html', () => {
    formatAnnotationValue('<script>alert(1)</script>').should.equal(
      '&lt;script&gt;alert(1)&lt;&#x2F;script&gt;'
    );
  });
});

describe('formatTagValue', () => {
  it('should return same value when string', () => {
    formatTagValue('foo').should.equal('foo');
  });

  it('should return string when false', () => {
    formatTagValue(false).should.equal('false');
  });

  it('should return string when true', () => {
    formatTagValue(true).should.equal('true');
  });

  it('should format object as pre-formatted multi-line json', () => {
    formatTagValue({foo: 'bar'}).should.equal(
      '<pre><code>{\n  &quot;foo&quot;: &quot;bar&quot;\n}</code></pre>'
    );
  });

  it('should format array as pre-formatted multi-line json', () => {
    formatTagValue([{foo: 'bar'}, {baz: 'qux'}]).should.equal(
      '<pre><code>[\n  {\n    &quot;foo&quot;: &quot;bar&quot;\n  },\n'
      + '  {\n    &quot;baz&quot;: &quot;qux&quot;\n  }\n]</code></pre>'
    );
  });

  it('should format null as pre-formatted json', () => {
    formatTagValue(null).should.equal('<pre><code>null</code></pre>');
  });

  it('should format multi-line string as pre-formatted', () => {
    formatTagValue('foo\nbar\n').should.equal(
      '<pre><code>foo\nbar\n</code></pre>'
    );
  });

  it('should escape html', () => {
    formatTagValue('<script>alert(1)</script>').should.equal(
      '&lt;script&gt;alert(1)&lt;&#x2F;script&gt;'
    );
  });
});

describe('isDupeTag', () => {
  const tagMap = {};

  it('should return false on new key', () => {
    isDupeTag(tagMap, {key: 'key-1', value: 'value-1'}).should.equal(false);
  });

  it('should return true on dupe key with exact matched value', () => {
    isDupeTag(tagMap, {key: 'key-1', value: 'value-1'}).should.equal(true);
  });

  it('should return false on dupe key with different value', () => {
    isDupeTag(tagMap, {key: 'key-1', value: 'value-2'}).should.equal(false);
  });
});
