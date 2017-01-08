import {Constants} from '../../js/component_ui/traceConstants';
import {
  maybeMarkTransientError,
  formatAnnotationValue,
  formatBinaryAnnotationValue
} from '../../js/component_ui/spanPanel';
import {endpoint, annotation} from './traceTestHelpers';

const ep = endpoint(123, 123, 'service1');

chai.config.truncateThreshold = 0;

describe('maybeMarkTransientError', () => {
  const row = {
    className: '',
    addClass(className) {this.className = className;}
  };

  it('should not add class when annotation is not error', () => {
    const anno = annotation(100, Constants.CLIENT_SEND, ep);

    maybeMarkTransientError(row, anno);
    row.className.should.equal('');
  });

  it('should add class when annotation is error', () => {
    const anno = annotation(100, Constants.ERROR, ep);

    maybeMarkTransientError(row, anno);
    row.className.should.equal('anno-error-transient');
  });

  // uses an actual value from Finagle
  it('should add class when annotation matches error', () => {
    const anno = annotation(
      100,
      'Server Send Error: TimeoutException: socket timed out',
      ep
    );

    maybeMarkTransientError(row, anno);
    row.className.should.equal('anno-error-transient');
  });
});

describe('formatAnnotationValue', () => {
  it('should return same value when string', () => {
    formatAnnotationValue('foo').should.equal('foo');
  });

  it('should format object as one-line json', () => {
    formatAnnotationValue({foo: 'bar'}).should.equal(
      '{"foo":"bar"}'
    );
  });

  it('should format array as one-line json', () => {
    formatAnnotationValue([{foo: 'bar'}, {baz: 'qux'}]).should.equal(
      '[{"foo":"bar"},{"baz":"qux"}]'
    );
  });
});

describe('formatBinaryAnnotationValue', () => {
  it('should return same value when string', () => {
    formatBinaryAnnotationValue('foo').should.equal('foo');
  });

  it('should format object as pre-formatted multi-line json', () => {
    formatBinaryAnnotationValue({foo: 'bar'}).should.equal(
      '<pre>{\n  "foo": "bar"\n}</pre>'
    );
  });

  it('should format array as pre-formatted multi-line json', () => {
    formatBinaryAnnotationValue([{foo: 'bar'}, {baz: 'qux'}]).should.equal(
      '<pre>[\n  {\n    "foo": "bar"\n  },\n  {\n    "baz": "qux"\n  }\n]</pre>'
    );
  });
});
