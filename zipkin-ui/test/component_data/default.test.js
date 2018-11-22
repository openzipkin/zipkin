import {
  convertSuccessResponse, convertToApiQuery, convertDurationToMicrosecond,
} from '../../js/component_data/default';
import {errorTrace, httpTrace, skewedTrace} from '../component_ui/traceTestHelpers';
import queryString from 'query-string';

describe('convertSuccessResponse', () => {
  const apiURL = '/api/v2/traces?serviceName=all&spanName=all&endTs=1459169770000';

  it('should convert an http trace', () => {
    const expectedTemplate = {
      traceId: 'bb1f0e21882325b8',
      startTs: '11-02-2018T05:56:09.255+0000',
      timestamp: 1541138169255688,
      duration: 168.731,
      durationStr: '168.731ms',
      width: 100,
      spanCount: 3,
      serviceSummaries: [
        // Where both sides are instrumented, this prefers server duration.
        {serviceName: 'frontend', spanCount: 2, maxSpanDurationStr: '168.731ms'},
        {serviceName: 'backend', spanCount: 1, maxSpanDurationStr: '26.326ms'}
      ],
      infoClass: ''
    };

    const rawResponse = [httpTrace];
    convertSuccessResponse(rawResponse, 'all', apiURL, true).should.deep.equal(
      {traces: [expectedTemplate], apiURL, rawResponse}
    );
  });

  it('should include service percentage from the URL', () => {
    const urlIncludingServiceName =
      '/api/v2/traces?serviceName=backend&spanName=all&endTs=1459169770000';

    const expectedTemplate = {
      traceId: 'bb1f0e21882325b8',
      startTs: '11-02-2018T05:56:09.255+0000',
      timestamp: 1541138169255688,
      duration: 168.731,
      durationStr: '168.731ms',
      width: 100,
      spanCount: 3,
      serviceSummaries: [
        {serviceName: 'frontend', spanCount: 2, maxSpanDurationStr: '168.731ms'},
        {serviceName: 'backend', spanCount: 1, maxSpanDurationStr: '26.326ms'}
      ],
      infoClass: '',
      servicePercentage: 15 // what percentage of total duration was in backend?
    };

    const rawResponse = [httpTrace];
    convertSuccessResponse(rawResponse, 'backend', urlIncludingServiceName, true).should.deep.equal(
      {traces: [expectedTemplate], apiURL: urlIncludingServiceName, rawResponse}
    );
  });

  it('should convert an error trace', () => {
    const expectedTemplate = {
      traceId: '1e223ff1f80f1c69',
      startTs: '11-02-2018T05:56:09.377+0000',
      timestamp: 1541138169377997,
      duration: 0.017,
      durationStr: '17μ',
      width: 100,
      spanCount: 1,
      serviceSummaries: [
        {serviceName: 'backend', spanCount: 1, maxSpanDurationStr: '17μ'}
      ],
      infoClass: 'trace-error-critical'
    };

    const rawResponse = [errorTrace];
    convertSuccessResponse(rawResponse, 'all', apiURL, true).should.deep.equal(
      {traces: [expectedTemplate], apiURL, rawResponse}
    );
  });

  it('should skip an empty result', () => {
    convertSuccessResponse([], 'all', apiURL, true).should.deep.equal(
      {traces: [], apiURL, rawResponse: []}
    );
  });

  it('should skip an invalid trace', () => {
    const missingTimestamp = [{
      traceId: '2',
      id: '2',
      duration: 1,
      localEndpoint: {serviceName: 'B'}
    }];

    const rawResponse = [missingTimestamp];
    convertSuccessResponse(rawResponse, 'all', apiURL, true).should.deep.equal(
      {traces: [], apiURL, rawResponse}
    );
  });

  // This tests that clock skew is considered when deciding the length of the trace
  it('should convert a skewed trace', () => {
    // TODO: This test only ensures the behavior of the existing code. For example, if we don't
    // clock skew correct, the result will be a different duration. We haven't verified the logic
    // is correct.. for example, the calculated duration here may be wrong.
    const expectedTemplate = {
      traceId: '1e223ff1f80f1c69',
      startTs: '08-02-2016T15:00:04.071+0000',
      timestamp: 1470150004071068,
      duration: 99.411,
      durationStr: '99.411ms',
      width: 100,
      spanCount: 4,
      serviceSummaries: [
        {serviceName: 'servicea', spanCount: 2, maxSpanDurationStr: '99.411ms'},
        {serviceName: 'serviceb', spanCount: 2, maxSpanDurationStr: '93.577ms'}
      ],
      infoClass: '',
      servicePercentage: 94
    };

    const rawResponse = [skewedTrace];
    convertSuccessResponse(rawResponse, 'serviceb', apiURL, true).should.deep.equal(
      {traces: [expectedTemplate], apiURL, rawResponse}
    );
  });
});

describe('convertToApiQuery', () => {
  const should = require('chai').should();
  it('should clear spanName all', () => {
    const parsed = convertToApiQuery(queryString.parse('?spanName=all&endTs=1459169770000'));

    should.not.exist(parsed.spanName);
  });

  it('should clear serviceName all', () => {
    const parsed = convertToApiQuery(queryString.parse('?serviceName=all&endTs=1459169770000'));

    should.not.exist(parsed.spanName);
  });

  it('should not require startTs', () => {
    const parsed = convertToApiQuery(queryString.parse('?lookback=custom&endTs=1459169770000'));

    parsed.endTs.should.equal('1459169770000');
    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });

  it('should replace startTs with lookback', () => {
    const parsed = convertToApiQuery(
      queryString.parse('?lookback=custom&startTs=1459169760000&endTs=1459169770000')
    );

    parsed.endTs.should.equal('1459169770000');
    parsed.lookback.should.equal('10000');
    should.not.exist(parsed.startTs);
  });

  it('should not add negative lookback', () => {
    const parsed = convertToApiQuery(
      queryString.parse('?lookback=custom&endTs=1459169760000&startTs=1459169770000')
    );

    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });
});

describe('convertDurationToMicrosecond', () => {
  it('should make "us" 1 times', () => {
    convertDurationToMicrosecond('12345us').should.equal('12345');
  });

  it('should make "μs" 1 times', () => {
    convertDurationToMicrosecond('12345μs').should.equal('12345');
  });

  it('should make "ms" 1000 times', () => {
    convertDurationToMicrosecond('12345ms').should.equal('12345000');
  });

  it('should make "s" 1000000 times', () => {
    convertDurationToMicrosecond('12345s').should.equal('12345000000');
  });

  it('should return as it is, otherwise', () => {
    convertDurationToMicrosecond('12345').should.equal('12345');
  });
});
