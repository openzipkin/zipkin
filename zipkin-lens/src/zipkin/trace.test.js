/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import {
  traceSummary,
  traceSummaries,
  mkDurationStr,
  detailedTraceSummary,
} from './trace';
import { SpanNode } from './span-node';
import { clean } from './span-cleaner';
import { treeCorrectedForClockSkew } from './clock-skew';
import yelpTrace from '../../testdata/yelp.json';

const frontend = {
  serviceName: 'frontend',
  ipv4: '172.17.0.13',
};

const backend = {
  serviceName: 'backend',
  ipv4: '172.17.0.9',
};

const httpTrace = [
  {
    traceId: 'bb1f0e21882325b8',
    parentId: 'bb1f0e21882325b8',
    id: 'c8c50ebd2abc179e',
    kind: 'CLIENT',
    name: 'get',
    timestamp: 1541138169297572,
    duration: 111121,
    localEndpoint: frontend,
    annotations: [
      { value: 'ws', timestamp: 1541138169337695 },
      { value: 'wr', timestamp: 1541138169368570 },
    ],
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
    },
  },
  {
    traceId: 'bb1f0e21882325b8',
    id: 'bb1f0e21882325b8',
    kind: 'SERVER',
    name: 'get /',
    timestamp: 1541138169255688,
    duration: 168731,
    localEndpoint: frontend,
    remoteEndpoint: {
      ipv4: '110.170.201.178',
      port: 63678,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/',
      'mvc.controller.class': 'Frontend',
      'mvc.controller.method': 'callBackend',
    },
  },
  {
    traceId: 'bb1f0e21882325b8',
    parentId: 'bb1f0e21882325b8',
    id: 'c8c50ebd2abc179e',
    kind: 'SERVER',
    name: 'get /api',
    timestamp: 1541138169377997, // this is actually skewed right, but we can't correct it
    duration: 26326,
    localEndpoint: backend,
    remoteEndpoint: {
      ipv4: '172.17.0.13',
      port: 63679,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
      'mvc.controller.class': 'Backend',
      'mvc.controller.method': 'printDate',
    },
    shared: true,
  },
];

const missingLocalEndpointTrace = [
  {
    traceId: '0b3ba16a811130ed',
    parentId: '0b3ba16a811130ed',
    id: '37c9d5cfc985592b',
    kind: 'SERVER',
    name: 'get',
    timestamp: 1587434790009238,
    duration: 10803,
    remoteEndpoint: {
      ipv4: '127.0.0.1',
      port: 64529,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
      'mvc.controller.class': 'Backend',
    },
    shared: true,
  },
  {
    traceId: '0b3ba16a811130ed',
    parentId: '0b3ba16a811130ed',
    id: '37c9d5cfc985592b',
    kind: 'CLIENT',
    name: 'get',
    timestamp: 1587434789956001,
    duration: 74362,
    tags: {
      'http.method': 'GET',
      'http.path': '/api',
    },
  },
  {
    traceId: '0b3ba16a811130ed',
    id: '0b3ba16a811130ed',
    kind: 'SERVER',
    name: 'get',
    timestamp: 1587434789934510,
    duration: 103410,
    remoteEndpoint: {
      ipv6: '::1',
      port: 64528,
    },
    tags: {
      'http.method': 'GET',
      'http.path': '/',
      'mvc.controller.class': 'Frontend',
    },
  },
];

// renders data into a tree for traceMustache
const cleanedHttpTrace = treeCorrectedForClockSkew(httpTrace);
const cleanedMissingLocalEndpointTrace = treeCorrectedForClockSkew(
  missingLocalEndpointTrace,
);

describe('traceSummary', () => {
  it('should classify durations local to the endpoint', () => {
    expect(traceSummary(cleanedHttpTrace).groupedTimestamps).toEqual({
      frontend: [
        { timestamp: 1541138169255688, duration: 168731 },
        { timestamp: 1541138169297572, duration: 111121 },
      ],
      backend: [{ timestamp: 1541138169377997, duration: 26326 }],
    });
  });

  // Ex netflix sometimes add annotations with no duration
  it('should backfill incomplete duration as zero instead of undefined', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        id: '2480ccca8df0fca5',
        kind: 'CLIENT',
        timestamp: 1541138169297572,
        duration: 111121,
        localEndpoint: frontend,
      }),
    );
    testTrace.addChild(
      new SpanNode(
        clean({
          traceId: '2480ccca8df0fca5',
          parentId: '2480ccca8df0fca5',
          id: 'bf396325699c84bf',
          timestamp: 1541138169377997,
          localEndpoint: backend,
        }),
      ),
    );

    expect(traceSummary(testTrace).groupedTimestamps).toEqual({
      frontend: [{ timestamp: 1541138169297572, duration: 111121 }],
      backend: [{ timestamp: 1541138169377997, duration: 0 }],
    });
  });

  it('should throw error on trace missing timestamp', () => {
    let error;
    try {
      traceSummary(
        new SpanNode(
          clean({
            traceId: '1e223ff1f80f1c69',
            id: 'bf396325699c84bf',
          }),
        ),
      );
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual(
      'Trace 1e223ff1f80f1c69 is missing a timestamp',
    );
  });

  it('calculates timestamp and duration', () => {
    const summary = traceSummary(cleanedHttpTrace);
    expect(summary.timestamp).toBe(cleanedHttpTrace.span.timestamp);
    expect(summary.duration).toBe(cleanedHttpTrace.span.duration);
  });

  it('should get span count', () => {
    const summary = traceSummary(cleanedHttpTrace);
    expect(summary.spanCount).toBe(httpTrace.length);
  });
});

describe('traceSummariesToMustache', () => {
  const summary = traceSummary(cleanedHttpTrace);

  it('should return empty list for empty list', () => {
    expect(traceSummaries(null, [])).toEqual([]);
  });

  it('should not change unit of timestamp or duration', () => {
    const model = traceSummaries(null, [summary]);
    expect(model[0].timestamp).toBe(summary.timestamp);
    expect(model[0].duration).toBe(summary.duration);
  });

  it('should render empty serviceSummaries when spans lack localEndpoint', () => {
    const model = traceSummaries(null, [
      traceSummary(cleanedMissingLocalEndpointTrace),
    ]);
    expect(model[0].serviceSummaries).toEqual([]);
  });

  it('should get service summaries, ordered descending by max span duration', () => {
    const model = traceSummaries(null, [summary]);
    expect(model[0].serviceSummaries).toEqual([
      {
        serviceName: 'frontend',
        spanCount: 2,
      },
      { serviceName: 'backend', spanCount: 1 },
    ]);
  });

  it('should pass on the trace id', () => {
    const model = traceSummaries('backend', [summary]);
    expect(model[0].traceId).toBe(summary.traceId);
  });

  it('should format start time', () => {
    const model = traceSummaries(null, [summary], true);
    expect(model[0].startTs).toBe('11-02-2018T05:56:09.255+0000');
  });

  it('should format duration', () => {
    const model = traceSummaries(null, [summary]);
    expect(model[0].durationStr).toBe('168.731ms');
  });

  it('should calculate the width in percent', () => {
    const start = 1;
    const summary1 = {
      traceId: 'cafebaby',
      timestamp: start,
      duration: 2000,
      groupedTimestamps: {
        backend: [{ timestamp: start + 1, duration: 2000 }],
      },
    };
    const summary2 = {
      traceId: 'cafedead',
      timestamp: start,
      duration: 20000,
      groupedTimestamps: {
        backend: [{ timestamp: start, duration: 20000 }],
      },
    };

    // Model is ordered by duration, and the width should be relative (percentage)
    const model = traceSummaries(null, [summary1, summary2]);
    expect(model[0].width).toBe(100);
    expect(model[1].width).toBe(10);
  });

  it('should pass on timestamp', () => {
    const model = traceSummaries(null, [summary]);
    expect(model[0].timestamp).toBe(summary.timestamp);
  });

  it('should get correct spanCount', () => {
    const testSummary = traceSummary(cleanedHttpTrace);
    const [model] = traceSummaries(null, [testSummary]);
    expect(model.spanCount).toBe(httpTrace.length);
  });

  it('should order traces by duration and tie-break using trace id', () => {
    const traceId1 = '9ed44141f679130b';
    const traceId2 = '6ff1c14161f7bde1';
    const traceId3 = '1234561234561234';
    const summary1 = traceSummary(
      new SpanNode(
        clean({
          traceId: traceId1,
          name: 'get',
          id: '6ff1c14161f7bde1',
          timestamp: 1457186441657000,
          duration: 4000,
        }),
      ),
    );
    const summary2 = traceSummary(
      new SpanNode(
        clean({
          traceId: traceId2,
          name: 'get',
          id: '9ed44141f679130b',
          timestamp: 1457186568026000,
          duration: 4000,
        }),
      ),
    );
    const summary3 = traceSummary(
      new SpanNode(
        clean({
          traceId: traceId3,
          name: 'get',
          id: '6677567324735',
          timestamp: 1457186568027000,
          duration: 3000,
        }),
      ),
    );

    const model = traceSummaries(null, [summary1, summary2, summary3]);
    expect(model[0].traceId).toBe(traceId2);
    expect(model[1].traceId).toBe(traceId1);
    expect(model[2].traceId).toBe(traceId3);
  });
});

describe('mkDurationStr', () => {
  it('should return empty string on zero duration', () => {
    expect(mkDurationStr(0)).toBe('');
  });

  it('should return empty string on undefined duration', () => {
    expect(mkDurationStr()).toBe('');
  });

  it('should format microseconds', () => {
    expect(mkDurationStr(3)).toBe('3μs');
  });

  it('should format ms', () => {
    expect(mkDurationStr(1500)).toBe('1.500ms');
  });

  it('should format exact ms', () => {
    expect(mkDurationStr(15000)).toBe('15ms');
  });

  it('should format seconds', () => {
    expect(mkDurationStr(2534999)).toBe('2.535s');
  });
});

const cleanedYelpTrace = treeCorrectedForClockSkew(yelpTrace);

describe('detailedTraceSummary', () => {
  it('should derive summary info', () => {
    const {
      traceId,
      durationStr,
      depth,
      serviceNameAndSpanCounts,
      rootSpan,
    } = detailedTraceSummary(cleanedHttpTrace);

    expect(traceId).toBe('bb1f0e21882325b8');
    expect(durationStr).toBe('168.731ms');
    expect(depth).toBe(2); // number of span rows (distinct span IDs)
    expect(serviceNameAndSpanCounts).toEqual([
      { serviceName: 'backend', spanCount: 1 },
      { serviceName: 'frontend', spanCount: 2 },
    ]);
    expect(rootSpan).toEqual({
      serviceName: 'frontend',
      spanName: 'get /',
    });
  });

  it('should position incomplete spans at the correct offset', () => {
    const { spans } = detailedTraceSummary(cleanedYelpTrace);

    // the absolute values are not important, just checks that only the root span is at offset 0
    expect(spans.map((s) => s.left)[0]).toEqual(0);
  });

  it('should derive summary info even when headless', () => {
    const headless = new SpanNode(); // headless as there's no root span

    // make a copy of the cleaned http trace as adding a child is a mutation
    treeCorrectedForClockSkew(httpTrace).children.forEach((child) =>
      headless.addChild(child),
    );

    const {
      traceId,
      durationStr,
      depth,
      serviceNameAndSpanCounts,
      rootSpan,
    } = detailedTraceSummary(headless);

    expect(traceId).toBe('bb1f0e21882325b8');
    expect(durationStr).toBe('111.121ms'); // client duration
    expect(depth).toBe(1); // number of span rows (distinct span IDs)
    expect(serviceNameAndSpanCounts).toEqual([
      { serviceName: 'backend', spanCount: 1 },
      { serviceName: 'frontend', spanCount: 1 },
    ]);
    expect(rootSpan).toEqual({
      serviceName: 'frontend',
      spanName: 'get',
    });
  });

  it('should show human-readable annotation name', () => {
    const {
      spans: [testSpan],
    } = detailedTraceSummary(cleanedHttpTrace);
    expect(testSpan.annotations[0].value).toBe('Server Start');
    expect(testSpan.annotations[1].value).toBe('Server Finish');
    expect(testSpan.tags[4].key).toBe('Client Address');
  });

  it('should tolerate spans without annotations', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        name: 'get',
        id: '2480ccca8df0fca5',
        timestamp: 1457186385375000,
        duration: 333000,
        localEndpoint: {
          serviceName: 'zipkin-query',
          ipv4: '127.0.0.1',
          port: 9411,
        },
        tags: { lc: 'component' },
      }),
    );
    const {
      spans: [testSpan],
    } = detailedTraceSummary(testTrace);
    expect(testSpan.tags[0].key).toBe('Local Component');
  });

  it('should not include empty Local Component annotations', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        name: 'get',
        id: '2480ccca8df0fca5',
        timestamp: 1457186385375000,
        duration: 333000,
        localEndpoint: {
          serviceName: 'zipkin-query',
          ipv4: '127.0.0.1',
          port: 9411,
        },
      }),
    );
    const {
      spans: [testSpan],
    } = detailedTraceSummary(testTrace);
    // skips empty Local Component, but still shows it as an address
    expect(testSpan.tags[0].key).toBe('Local Address');
  });

  it('should tolerate spans without tags', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        name: 'get',
        id: '2480ccca8df0fca5',
        kind: 'SERVER',
        timestamp: 1457186385375000,
        duration: 333000,
        localEndpoint: {
          serviceName: 'zipkin-query',
          ipv4: '127.0.0.1',
          port: 9411,
        },
      }),
    );
    const {
      spans: [testSpan],
    } = detailedTraceSummary(testTrace);
    expect(testSpan.annotations[0].value).toBe('Server Start');
    expect(testSpan.annotations[1].value).toBe('Server Finish');
  });

  // TODO: we should really only allocate remote endpoints when on an uninstrumented link
  it('should count spans for any endpoint', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        id: '2480ccca8df0fca5',
        kind: 'CLIENT',
        timestamp: 1,
        localEndpoint: frontend,
        remoteEndpoint: frontend,
      }),
    );
    testTrace.addChild(
      new SpanNode(
        clean({
          traceId: '2480ccca8df0fca5',
          parentId: '2480ccca8df0fca5',
          id: 'bf396325699c84bf',
          name: 'foo',
          timestamp: 2,
          localEndpoint: backend,
        }),
      ),
    );

    const { serviceNameAndSpanCounts } = detailedTraceSummary(testTrace);
    expect(serviceNameAndSpanCounts).toEqual([
      { serviceName: 'backend', spanCount: 1 },
      { serviceName: 'frontend', spanCount: 1 },
    ]);
  });

  it('should count spans with no timestamp or duration', () => {
    const testTrace = new SpanNode(
      clean({
        traceId: '2480ccca8df0fca5',
        id: '2480ccca8df0fca5',
        kind: 'CLIENT',
        timestamp: 1, // root always needs a timestamp
        localEndpoint: frontend,
        remoteEndpoint: frontend,
      }),
    );
    testTrace.addChild(
      new SpanNode(
        clean({
          traceId: '2480ccca8df0fca5',
          parentId: '2480ccca8df0fca5',
          id: 'bf396325699c84bf',
          name: 'foo',
          localEndpoint: backend,
        }),
      ),
    );

    const { serviceNameAndSpanCounts } = detailedTraceSummary(testTrace);
    expect(serviceNameAndSpanCounts).toEqual([
      { serviceName: 'backend', spanCount: 1 },
      { serviceName: 'frontend', spanCount: 1 },
    ]);
  });

  /*
   * The input data to the trace view is already sorted by timestamp. Span rows need to be added in
   * depth-first order to ensure they can be collapsed by parent.
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f 1
   *          \
   *           2
   */
  it('should order spans by timestamp, root first, not by cause', () => {
    // to prevent invalid trace errors, we need a timestamp on the root span.
    const a = new SpanNode(clean({ traceId: '1', id: 'a', timestamp: 1 }));
    const b = new SpanNode(clean({ traceId: '1', id: 'b', parentId: 'a' }));
    const c = new SpanNode(clean({ traceId: '1', id: 'c', parentId: 'a' }));
    const d = new SpanNode(clean({ traceId: '1', id: 'd', parentId: 'a' }));
    // root(a) has children b, c, d
    a.addChild(b);
    a.addChild(c);
    a.addChild(d);
    const e = new SpanNode(clean({ traceId: '1', id: 'e', parentId: 'b' }));
    const f = new SpanNode(clean({ traceId: '1', id: 'f', parentId: 'b' }));
    const g = new SpanNode(clean({ traceId: '1', id: '1', parentId: 'b' }));
    // child(b) has children e, f, g
    b.addChild(e);
    b.addChild(f);
    b.addChild(g);
    const h = new SpanNode(clean({ traceId: '1', id: '2', parentId: '1' }));
    // f has no children
    // child(g) has child h
    g.addChild(h);

    const { spans } = detailedTraceSummary(a);
    expect(spans.map((s) => s.spanId)).toEqual([
      '000000000000000a',
      '000000000000000b',
      '000000000000000e',
      '000000000000000f',
      '0000000000000001',
      '0000000000000002',
      '000000000000000c',
      '000000000000000d',
    ]);
  });

  it('should order rows by topologically, then timestamp when endpoints are inconsistent', () => {
    const traceWithEndpointProblems = [
      {
        traceId: 'a',
        parentId: '1',
        id: '5',
        name: 'get /header',
        timestamp: 359175,
        duration: 108123,
        localEndpoint: {
          serviceName: 'sad_pages',
          ipv4: '10.1.1.123',
          port: 31107,
        },
      },
      {
        traceId: 'a',
        parentId: '1',
        id: '4',
        name: 'get /footer',
        timestamp: 341172,
        duration: 16640,
        localEndpoint: {
          serviceName: 'sad_pages',
          ipv4: '10.1.1.123',
          port: 31107,
        },
      },
      {
        traceId: 'a',
        parentId: '1',
        id: '3',
        kind: 'CLIENT',
        name: 'get',
        timestamp: 30000,
        duration: 123000,
        localEndpoint: {
          serviceName: 'sad_pages',
          ipv4: '169.254.0.3',
          port: 43193,
        },
      },
      {
        traceId: 'a',
        parentId: '1',
        id: '2',
        kind: 'SERVER',
        name: 'get /token',
        timestamp: 12137,
        duration: 12785,
        localEndpoint: {
          serviceName: 'phantom',
          ipv4: '10.1.1.123',
          port: 32767,
        },
        shared: true,
      },
      {
        traceId: 'a',
        parentId: '1',
        id: '2',
        kind: 'CLIENT',
        name: 'get',
        timestamp: 11000,
        duration: 13000,
        localEndpoint: {
          serviceName: 'sad_pages',
          ipv4: '169.254.0.2',
          port: 49647,
        },
      },
      {
        traceId: 'a',
        id: '1',
        kind: 'SERVER',
        name: 'get /sad',
        timestamp: 4857,
        duration: 525280,
        localEndpoint: {
          serviceName: 'sad_pages',
          ipv4: '10.1.1.123',
          port: 31107,
        },
        shared: true,
      },
      {
        traceId: 'a',
        id: '1',
        kind: 'CLIENT',
        name: 'get',
        timestamp: 1,
        duration: 537000,
        localEndpoint: {
          serviceName: 'whelp-main',
          ipv4: '169.254.0.1',
          port: 43237,
        },
      },
    ];

    const { spans } = detailedTraceSummary(
      treeCorrectedForClockSkew(traceWithEndpointProblems),
    );
    expect(spans.map((s) => s.timestamp)).toEqual([
      1,
      11000,
      30000,
      341172,
      359175, // increasing order
    ]);
  });
});
