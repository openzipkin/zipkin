import {convertToApiQuery, rawTraceToSummary} from '../../js/component_data/default';
import queryString from 'query-string';

describe('rawTraceToSummary', () => {
  it('should create trace summary from skewed trace', () => {
    // from ../tracedata/skew.json as we can't figure out how to read file with headless chrome env
    const skewedTrace = [
      {
        traceId: '1e223ff1f80f1c69',
        parentId: '74280ae0c10d8062',
        id: '43210ae0c10d1234',
        name: 'async',
        timestamp: 1470150004008762,
        duration: 65000,
        localEndpoint: {
          serviceName: 'serviceb',
          ipv4: '192.0.0.0'
        }
      },
      {
        traceId: '1e223ff1f80f1c69',
        parentId: 'bf396325699c84bf',
        id: '74280ae0c10d8062',
        kind: 'SERVER',
        name: 'post',
        timestamp: 1470150004008761,
        duration: 93577,
        localEndpoint: {
          serviceName: 'serviceb',
          ipv4: '192.0.0.0'
        },
        shared: true
      },
      {
        traceId: '1e223ff1f80f1c69',
        id: 'bf396325699c84bf',
        kind: 'SERVER',
        name: 'get',
        timestamp: 1470150004071068,
        duration: 99411,
        localEndpoint: {
          serviceName: 'servicea',
          ipv4: '127.0.0.0'
        },
        shared: true
      },
      {
        traceId: '1e223ff1f80f1c69',
        parentId: 'bf396325699c84bf',
        id: '74280ae0c10d8062',
        kind: 'CLIENT',
        name: 'post',
        timestamp: 1470150004074202,
        duration: 94539,
        localEndpoint: {
          serviceName: 'servicea',
          ipv4: '127.0.0.0'
        }
      }
    ];

    // TODO: This test only ensures the behavior of the existing code. For example, if we don't
    // clock skew correct, the result will be a different duration. We haven't verified the logic
    // is correct.. for example, the calculated duration here may be wrong.
    const expectedTraceSummary = {
      traceId: '1e223ff1f80f1c69',
      timestamp: 1470150004071068,
      duration: 99411,
      groupedTimestamps: {
        servicea: [
          {
            timestamp: 1470150004071068,
            duration: 99411
          },
          {
            timestamp: 1470150004074202,
            duration: 94539
          }
        ],
        serviceb: [
          {
            timestamp: 1470150004074202,
            duration: 94539
          },
          {
            timestamp: 1470150004074684,
            duration: 65000
          }
        ]
      },
      endpoints: [
        {
          serviceName: 'servicea',
          ipv4: '127.0.0.0'
        },
        {
          serviceName: 'serviceb',
          ipv4: '192.0.0.0'
        }
      ],
      errorType: 'none',
      totalSpans: 3
    };

    rawTraceToSummary(skewedTrace).should.deep.equal(expectedTraceSummary);
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
