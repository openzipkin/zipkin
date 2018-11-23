import {
  ensureV2
} from '../../js/component_ui/uploadTrace';
import {httpTrace} from '../component_ui/traceTestHelpers';

chai.config.truncateThreshold = 0;

describe('ensureV2', () => {
  it('does not throw on v2 format', () => {
    ensureV2(httpTrace);
  });

  it('should raise error if not a list', () => {
    let error;
    try {
      ensureV2();
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('input is not a list');

    try {
      ensureV2({traceId: 'a', id: 'b'});
    } catch (err) {
      expect(err.message).to.eql(error.message);
    }
  });

  it('should raise error if missing trace ID or span ID', () => {
    let error;
    try {
      ensureV2([{traceId: 'a'}]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('List<Span> implies at least traceId and id fields');

    try {
      ensureV2([{id: 'b'}]);
    } catch (err) {
      expect(err.message).to.eql(error.message);
    }
  });

  it('should raise error if in v1 format', () => {
    let error;
    try {
      ensureV2([{traceId: 'a', id: 'b', binaryAnnotations: []}]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin');
  });
});
