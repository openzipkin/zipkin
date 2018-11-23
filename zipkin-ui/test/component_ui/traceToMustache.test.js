import traceToMustache,
  {
    getRootSpans,
    getServiceName,
    formatEndpoint
  } from '../../js/component_ui/traceToMustache';
const {clean, mergeV2ById} = require('../../js/spanCleaner');
import {SPAN_V1} from '../../js/spanConverter';
import {httpTrace} from './traceTestHelpers';

// cleans the data as traceSummary expects data to be normalized
const cleanedHttpTrace = mergeV2ById(httpTrace);

describe('traceToMustache', () => {
  it('should format duration', () => {
    const modelview = traceToMustache(cleanedHttpTrace);
    modelview.duration.should.equal('168.731ms');
  });

  it('should show the number of services', () => {
    const {services} = traceToMustache(cleanedHttpTrace);
    // TODO: correct: the span count is by ID when it should be by distinct span
    services.should.equal(2);
  });

  it('should show logsUrl', () => {
    const {logsUrl} = traceToMustache(cleanedHttpTrace, 'http/url.com');
    logsUrl.should.equal('http/url.com');
  });

  it('should show service name and span counts', () => {
    const {serviceNameAndSpanCounts} = traceToMustache(cleanedHttpTrace);
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 2}
    ]);
  });

  it('should show human-readable annotation name', () => {
    const {spans: [testSpan]} = traceToMustache(cleanedHttpTrace);
    testSpan.annotations[0].value.should.equal('Server Receive');
    testSpan.annotations[1].value.should.equal('Server Send');
    testSpan.binaryAnnotations[4].key.should.equal('Client Address');
  });

  it('should tolerate spans without annotations', () => {
    const testTrace = [clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
      tags: {lc: 'component'}
    })];
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.binaryAnnotations[0].key.should.equal('Local Component');
  });

  it('should not include empty Local Component annotations', () => {
    const testTrace = [clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411}
    })];
    const {spans: [testSpan]} = traceToMustache(testTrace);
    // skips empty Local Component, but still shows it as an address
    testSpan.binaryAnnotations[0].key.should.equal('Local Address');
  });

  it('should tolerate spans without tags', () => {
    const testTrace = [clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      kind: 'SERVER',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
    })];
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.annotations[0].value.should.equal('Server Receive');
    testSpan.annotations[1].value.should.equal('Server Send');
  });
});

describe('get root spans', () => {
  it('should find root spans in a trace', () => {
    const testTrace = [{
      parentId: null, // root span (no parent)
      id: 1
    }, {
      parentId: 1,
      id: 2
    }, {
      parentId: 3, // root span (no parent with this id)
      id: 4
    }, {
      parentId: 4,
      id: 5
    }];

    const rootSpans = getRootSpans(testTrace);
    rootSpans.should.eql([{
      parentId: null,
      id: 1
    }, {
      parentId: 3,
      id: 4
    }]);
  });
});

describe('formatEndpoint', () => {
  it('should format ip and port', () => {
    formatEndpoint({ipv4: '150.151.152.153', port: 5000}).should.equal('150.151.152.153:5000');
  });

  it('should not use port when missing or zero', () => {
    formatEndpoint({ipv4: '150.151.152.153'}).should.equal('150.151.152.153');
    formatEndpoint({ipv4: '150.151.152.153', port: 0}).should.equal('150.151.152.153');
  });

  it('should put service name in parenthesis', () => {
    formatEndpoint({ipv4: '150.151.152.153', port: 9042, serviceName: 'cassandra'}).should.equal(
      '150.151.152.153:9042 (cassandra)'
    );
    formatEndpoint({ipv4: '150.151.152.153', serviceName: 'cassandra'}).should.equal(
      '150.151.152.153 (cassandra)'
    );
  });

  it('should not show empty service name', () => {
    formatEndpoint({ipv4: '150.151.152.153', port: 9042, serviceName: ''}).should.equal(
      '150.151.152.153:9042'
    );
    formatEndpoint({ipv4: '150.151.152.153', serviceName: ''}).should.equal(
      '150.151.152.153'
    );
  });

  it('should show service name missing IP', () => {
    formatEndpoint({serviceName: 'rabbit'}).should.equal(
      'rabbit'
    );
  });

  it('should not crash on no data', () => {
    formatEndpoint({}).should.equal('');
  });

  it('should put ipv6 in brackets', () => {
    formatEndpoint({ipv6: '2001:db8::c001', port: 9042, serviceName: 'cassandra'}).should.equal(
      '[2001:db8::c001]:9042 (cassandra)'
    );

    formatEndpoint({ipv6: '2001:db8::c001', port: 9042}).should.equal(
      '[2001:db8::c001]:9042'
    );

    formatEndpoint({ipv6: '2001:db8::c001'}).should.equal(
      '[2001:db8::c001]'
    );
  });
});

describe('get service name of a span', () => {
  it('should get service name from server addr', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'CLIENT',
      remoteEndpoint: {serviceName: 'user-service'}
    });
    getServiceName(testSpan).should.equal('user-service');
  });

  it('should get service name from broker addr', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'PRODUCER',
      remoteEndpoint: {serviceName: 'kafka'}
    });
    getServiceName(testSpan).should.equal('kafka');
  });

  it('should get service name from some server annotation', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'SERVER',
      timestamp: 1457186385375000,
      localEndpoint: {serviceName: 'test-service'}
    });
    getServiceName(testSpan).should.equal('test-service');
  });

  it('should get service name from producer annotation', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'PRODUCER',
      timestamp: 1457186385375000,
      localEndpoint: {serviceName: 'test-service'}
    });
    getServiceName(testSpan).should.equal('test-service');
  });

  it('should get service name from consumer annotation', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'CONSUMER',
      timestamp: 1457186385375000,
      localEndpoint: {serviceName: 'test-service'}
    });
    getServiceName(testSpan).should.equal('test-service');
  });

  it('should get service name from client addr', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'SERVER',
      remoteEndpoint: {serviceName: 'my-service'}
    });
    getServiceName(testSpan).should.equal('my-service');
  });

  it('should get service name from client annotation', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'CLIENT',
      timestamp: 1457186385375000,
      localEndpoint: {serviceName: 'abc-service'}
    });
    getServiceName(testSpan).should.equal('abc-service');
  });

  it('should get service name from local component annotation', () => {
    const testSpan = SPAN_V1.convert({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      localEndpoint: {serviceName: 'localservice'}
    });
    getServiceName(testSpan).should.equal('localservice');
  });

  it('should handle no annotations', () => {
    expect(getServiceName({})).to.equal(null);
  });
});

