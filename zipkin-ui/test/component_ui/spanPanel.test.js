import {
  formatAnnotationValue,
  formatBinaryAnnotationValue
} from '../../js/component_ui/spanPanel';

chai.config.truncateThreshold = 0;

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
