import {
  convertV2ToV1
} from '../../js/component_ui/uploadTrace';
import {mergeV2ById} from '../../js/spanCleaner';
import {SPAN_V1} from '../../js/spanConverter';
import {httpTrace} from '../component_ui/traceTestHelpers';

chai.config.truncateThreshold = 0;

describe('convertV2ToV1', () => {
  // TODO: this is temporary, as soon this will not need to convert
  it('should convert to v1 format', () => {
    const v1Trace = convertV2ToV1(httpTrace);

    expect(v1Trace).to.deep.equal(SPAN_V1.convertTrace(mergeV2ById(httpTrace)));
  });

  it('should raise error if not a list', () => {
    let error;
    try {
      convertV2ToV1();
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('input is not a list');

    try {
      convertV2ToV1({traceId: 'a', id: 'b'});
    } catch (err) {
      expect(err.message).to.eql(error.message);
    }
  });

  it('should raise error if missing trace ID or span ID', () => {
    let error;
    try {
      convertV2ToV1([{traceId: 'a'}]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('List<Span> implies at least traceId and id fields');

    try {
      convertV2ToV1([{id: 'b'}]);
    } catch (err) {
      expect(err.message).to.eql(error.message);
    }
  });

  it('should raise error if in v1 format', () => {
    let error;
    try {
      convertV2ToV1([{traceId: 'a', id: 'b', binaryAnnotations: []}]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin');
  });
});
