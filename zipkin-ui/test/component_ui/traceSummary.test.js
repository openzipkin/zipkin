import {
  traceSummary,
  getGroupedTimestamps,
  getTraceErrorType,
  traceSummariesToMustache,
  mkDurationStr,
  totalDuration,
  traceDuration
} from '../../js/component_ui/traceSummary';
const {clean, mergeV2ById} = require('../../js/spanCleaner');
import {httpTrace, frontend, backend} from '../component_ui/traceTestHelpers';

chai.config.truncateThreshold = 0;

// cleans the data as traceSummary expects data to be normalized
const cleanedHttpTrace = mergeV2ById(httpTrace);

describe('getGroupedTimestamps', () => {
  it('should classify durations local to the endpoint', () => {
    getGroupedTimestamps(cleanedHttpTrace).should.eql(
      {
        frontend: [
          {timestamp: 1541138169255688, duration: 168731},
          {timestamp: 1541138169297572, duration: 111121}
        ],
        backend: [
          {timestamp: 1541138169377997, duration: 26326}
        ]
      }
    );
  });

  // Ex netflix sometimes add annotations with no duration
  it('should backfill incomplete duration as zero instead of undefined', () => {
    const testTrace = [
      {
        traceId: '2480ccca8df0fca5',
        id: '2480ccca8df0fca5',
        kind: 'CLIENT',
        timestamp: 1541138169297572,
        duration: 111121,
        localEndpoint: frontend
      },
      {
        traceId: '2480ccca8df0fca5',
        parentId: '2480ccca8df0fca5',
        id: 'bf396325699c84bf',
        timestamp: 1541138169377997,
        localEndpoint: backend,
      }
    ];

    getGroupedTimestamps(testTrace).should.eql(
      {
        frontend: [
          {timestamp: 1541138169297572, duration: 111121}
        ],
        backend: [
          {timestamp: 1541138169377997, duration: 0}
        ]
      }
    );
  });
});

describe('traceSummary', () => {
  it('should throw error on empty trace', () => {
    let error;
    try {
      traceSummary([]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('Trace was empty');
  });

  it('should throw error on trace missing timestamp', () => {
    let error;
    try {
      traceSummary([{
        traceId: '1e223ff1f80f1c69',
        id: 'bf396325699c84bf'
      }]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('Trace is missing a timestamp');
  });

  it('calculates timestamp and duration', () => {
    const summary = traceSummary(cleanedHttpTrace);
    const rootSpan = cleanedHttpTrace.find(s => s.traceId === s.id);
    summary.timestamp.should.equal(rootSpan.timestamp);
    summary.duration.should.equal(rootSpan.duration);
  });

  it('should get span count', () => {
    const summary = traceSummary(cleanedHttpTrace);
    summary.spanCount.should.equal(cleanedHttpTrace.length);
  });
});

describe('getTraceErrorType', () => {
  it('should return none if annotations and tags are empty', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [],
      tags: {}
    }];
    expect(getTraceErrorType(spans)).to.equal('none');
  });

  it('should return none if ann=noError and tag=noError', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{timestamp: 1, value: 'not'}],
      tags: {not: 'error'}
    }];
    expect(getTraceErrorType(spans)).to.equal('none');
  });

  it('should return none if second span has ann=noError and tag=noError', () => {
    const spans = [
      {
        traceId: '1e223ff1f80f1c69',
        id: '1e223ff1f80f1c69',
        annotations: [],
        tags: {}
      },
      {
        traceId: '1e223ff1f80f1c69',
        parentId: '1e223ff1f80f1c69',
        id: 'bf396325699c84bf',
        annotations: [{timestamp: 1, value: 'not'}],
        tags: {not: 'error'}
      }
    ];
    expect(getTraceErrorType(spans)).to.equal('none');
  });

  it('should return critical if ann empty and tag=error', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [],
      tags: {error: ''}
    }];
    expect(getTraceErrorType(spans)).to.equal('critical');
  });

  it('should return critical if ann=noError and tag=error', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{timestamp: 1, value: 'not'}],
      tags: {error: ''}
    }];
    expect(getTraceErrorType(spans)).to.equal('critical');
  });

  it('should return critical if ann=error and tag=error', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{timestamp: 1, value: 'error'}],
      tags: {error: ''}
    }];
    expect(getTraceErrorType(spans)).to.equal('critical');
  });

  it('should return critical if span1 has ann=error and span2 has tag=error', () => {
    const spans = [
      {
        traceId: '1e223ff1f80f1c69',
        id: '1e223ff1f80f1c69',
        annotations: [{timestamp: 1, value: 'error'}],
        tags: {}
      },
      {
        traceId: '1e223ff1f80f1c69',
        parentId: '1e223ff1f80f1c69',
        id: 'bf396325699c84bf',
        annotations: [],
        tags: {error: ''}
      }
    ];
    expect(getTraceErrorType(spans)).to.equal('critical');
  });

  it('should return transient if ann=error and tag noError', () => {
    const spans = [{
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{timestamp: 1, value: 'error'}],
      tags: {not: 'error'}
    }];
    expect(getTraceErrorType(spans)).to.equal('transient');
  });
});

describe('traceSummariesToMustache', () => {
  const summary = traceSummary(cleanedHttpTrace);

  it('should return empty list for empty list', () => {
    traceSummariesToMustache(null, []).should.eql([]);
  });

  it('should convert duration from micros to millis', () => {
    const model = traceSummariesToMustache(null, [{timestamp: 1, duration: 20000}]);
    model[0].duration.should.equal(20);
  });

  it('should get service summaries, ordered descending by max span duration', () => {
    const model = traceSummariesToMustache(null, [summary]);
    model[0].serviceSummaries.should.eql([
      {serviceName: 'frontend', spanCount: 2, maxSpanDurationStr: '168.731ms'},
      {serviceName: 'backend', spanCount: 1, maxSpanDurationStr: '26.326ms'}
    ]);
  });

  it('should pass on the trace id', () => {
    const model = traceSummariesToMustache('backend', [summary]);
    model[0].traceId.should.equal(summary.traceId);
  });

  it('should get service percentage', () => {
    const model = traceSummariesToMustache('backend', [summary]);
    model[0].servicePercentage.should.equal(15);
  });

  it('should format start time', () => {
    const model = traceSummariesToMustache(null, [summary], true);
    model[0].startTs.should.equal('11-02-2018T05:56:09.255+0000');
  });

  it('should format duration', () => {
    const model = traceSummariesToMustache(null, [summary]);
    model[0].durationStr.should.equal('168.731ms');
  });

  it('should calculate the width in percent', () => {
    const start = 1;
    const summary1 = {
      traceId: 'cafebaby',
      timestamp: start,
      duration: 2000,
      groupedTimestamps: {
        backend: [{timestamp: start + 1, duration: 2000}]
      }
    };
    const summary2 = {
      traceId: 'cafedead',
      timestamp: start,
      duration: 20000,
      groupedTimestamps: {
        backend: [{timestamp: start, duration: 20000}]
      }
    };

    // Model is ordered by duration, and the width should be relative (percentage)
    const model = traceSummariesToMustache(null, [summary1, summary2]);
    model[0].width.should.equal(100);
    model[1].width.should.equal(10);
  });

  it('should pass on timestamp', () => {
    const model = traceSummariesToMustache(null, [summary]);
    model[0].timestamp.should.equal(summary.timestamp);
  });

  it('should get correct spanCount', () => {
    const testSummary = traceSummary(cleanedHttpTrace);
    const model = traceSummariesToMustache(null, [testSummary])[0];
    model.spanCount.should.equal(cleanedHttpTrace.length);
  });

  it('should order traces by duration and tie-break using trace id', () => {
    const traceId1 = '9ed44141f679130b';
    const traceId2 = '6ff1c14161f7bde1';
    const traceId3 = '1234561234561234';
    const summary1 = traceSummary([clean({
      traceId: traceId1,
      name: 'get',
      id: '6ff1c14161f7bde1',
      timestamp: 1457186441657000,
      duration: 4000})]);
    const summary2 = traceSummary([clean({
      traceId: traceId2,
      name: 'get',
      id: '9ed44141f679130b',
      timestamp: 1457186568026000,
      duration: 4000
    })]);
    const summary3 = traceSummary([clean({
      traceId: traceId3,
      name: 'get',
      id: '6677567324735',
      timestamp: 1457186568027000,
      duration: 3000
    })]);

    const model = traceSummariesToMustache(null, [summary1, summary2, summary3]);
    model[0].traceId.should.equal(traceId2);
    model[1].traceId.should.equal(traceId1);
    model[2].traceId.should.equal(traceId3);
  });
});

describe('mkDurationStr', () => {
  it('should return empty string on zero duration', () => {
    mkDurationStr(0).should.equal('');
  });

  it('should return empty string on undefined duration', () => {
    mkDurationStr().should.equal('');
  });

  it('should format microseconds', () => {
    mkDurationStr(3).should.equal('3μ');
  });

  it('should format ms', () => {
    mkDurationStr(1500).should.equal('1.500ms');
  });

  it('should format exact ms', () => {
    mkDurationStr(15000).should.equal('15ms');
  });

  it('should format seconds', () => {
    mkDurationStr(2534999).should.equal('2.535s');
  });
});

describe('totalDuration', () => {
  it('should return zero on empty input', () => {
    totalDuration([]).should.equal(0);
  });

  it('should return only duration when single input', () => {
    totalDuration([{timestamp: 10, duration: 200}]).should.equal(200);
  });

  it('should return root span duration when no children complete after root', () => {
    const rootLongest = [
      {timestamp: 1, duration: 300},
      {timestamp: 10, duration: 200},
      {timestamp: 20, duration: 210}
    ];
    totalDuration(rootLongest).should.equal(300);
  });

  it('should return the total time in a service and not the time not in service', () => {
    const asyncTrace = [
      {timestamp: 1, duration: 300},
      {timestamp: 11, duration: 200}, // enclosed by above
      {timestamp: 390, duration: 20},
      {timestamp: 400, duration: 30}, // overlaps with above
    ];
    totalDuration(asyncTrace).should.equal(300 + ((400 + 30) - 390));
  });

  it('should ignore input missing duration', () => {
    const rootLongest = [
      {timestamp: 1, duration: 300},
      {timestamp: 10}, // incomplete span
      {timestamp: 20, duration: 210}
    ];
    totalDuration(rootLongest).should.equal(300);
  });
});

describe('traceDuration', () => {
  it('should return zero on empty input', () => {
    traceDuration([]).should.equal(0);
  });

  it('should return only duration when single input', () => {
    traceDuration([{timestamp: 10, duration: 200}]).should.equal(200);
  });

  it('should return root span duration when no children complete after root', () => {
    const rootLongest = [
      {timestamp: 1, duration: 300},
      {timestamp: 10, duration: 200},
      {timestamp: 20, duration: 210}
    ];
    traceDuration(rootLongest).should.equal(300);
  });

  it('should return the distance from the earliest event to the end of the last', () => {
    // In a messaging or async trace, the child span can start well after the first completes
    const asyncTrace = [
      {timestamp: 1, duration: 300},
      {timestamp: 11, duration: 200},
      {timestamp: 390, duration: 20},
      {timestamp: 400, duration: 30},
    ];
    traceDuration(asyncTrace).should.equal(400 + 30 - 1);
  });

  it('should ignore input missing duration', () => {
    const rootLongest = [
      {timestamp: 1, duration: 300},
      {timestamp: 10}, // incomplete span
      {timestamp: 20, duration: 210}
    ];
    traceDuration(rootLongest).should.equal(300);
  });
});
