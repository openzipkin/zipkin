import queryString from 'query-string';

import {
  nextInitialConditionKey,
  buildQueryParametersWithConditions,
  buildApiQueryParameters,
  extractConditionsFromQueryParameters,
  getConditionKeyListWithAvailability,
} from './global-search';

describe('nextInitialConditionKey', () => {
  it('should return the "serviceName" when conditions is empty', () => {
    expect(nextInitialConditionKey([], [])).toEqual('serviceName');
  });

  it('should return the unused condition key', () => {
    expect(nextInitialConditionKey([
      { key: 'serviceName' },
      { key: 'spanName' },
      { key: 'maxDuration' },
    ], [])).toEqual('minDuration');
  });

  it('should return "annotationQuery" when all condition keys are used', () => {
    expect(nextInitialConditionKey([
      { key: 'serviceName' },
      { key: 'spanName' },
      { key: 'minDuration' },
      { key: 'maxDuration' },
      { key: 'traceId' },
      { key: 'annotationQuery' },
    ], [])).toEqual('annotationQuery');
  });
});

describe('buildQueryParametersWithConditions', () => {
  it('should return right query parameters', () => {
    const queryParameters = buildQueryParametersWithConditions(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: '1h',
        endTs: 1547098357716,
      },
      15,
    );
    expect(queryString.parse(queryParameters)).toEqual({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      lookback: '1h',
      endTs: '1547098357716',
      limit: '15',
    });
  });

  it('should return right query parameters with custom lookback', () => {
    const queryParameters = buildQueryParametersWithConditions(
      [
        { key: 'serviceName', value: 'serviceA' },
        { key: 'spanName', value: 'spanA' },
        { key: 'minDuration', value: 10 },
        { key: 'maxDuration', value: 100 },
      ], {
        value: 'custom',
        endTs: 1547098357716,
        startTs: 1547098357701,
      },
      15,
    );
    expect(queryString.parse(queryParameters)).toEqual({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      lookback: 'custom',
      endTs: '1547098357716',
      startTs: '1547098357701',
      limit: '15',
    });
  });

  it('should return right query parameters with a annotationQuery', () => {
    const queryParameters = buildQueryParametersWithConditions(
      [
        { key: 'annotationQuery', value: 'key=value' },
      ], {
        value: '1h',
        endTs: 1547098357716,
      },
      15,
    );
    expect(queryString.parse(queryParameters)).toEqual({
      annotationQuery: 'key=value',
      lookback: '1h',
      endTs: '1547098357716',
      limit: '15',
    });
  });

  it('should return right query parameters with multiple annotationQueries', () => {
    const queryParameters = buildQueryParametersWithConditions(
      [
        { key: 'annotationQuery', value: 'key1=value1' },
        { key: 'annotationQuery', value: 'key2' }, // no value
        { key: 'annotationQuery', value: 'key3=value3' },
      ], {
        value: '1h',
        endTs: 1547098357716,
      },
      15,
    );
    expect(queryString.parse(queryParameters)).toEqual({
      annotationQuery: 'key1=value1 and key2 and key3=value3',
      lookback: '1h',
      endTs: '1547098357716',
      limit: '15',
    });
  });
});

describe('buildApiQueryParameters', () => {
  it('should return right query parameters', () => {
    const apiQueryParameters = buildApiQueryParameters({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      lookback: '1h',
      endTs: '1547098357716',
      limit: '15',
    });
    expect(apiQueryParameters).toEqual({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      endTs: '1547098357716',
      lookback: '3600000',
      limit: '15',
    });
  });

  it('should return right query parameters with custom lookback', () => {
    const apiQueryParameters = buildApiQueryParameters({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      lookback: 'custom',
      endTs: '1547098357716',
      startTs: '1547098357710', // lookback == 6
      limit: '15',
    });
    expect(apiQueryParameters).toEqual({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      endTs: '1547098357716',
      lookback: '6',
      limit: '15',
    });
  });
});

describe('extractConditionsFromQueryParameters', () => {
  it('should return right conditions', () => {
    const { conditions } = extractConditionsFromQueryParameters({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      annotationQuery: 'key1=value1 and key2 and key3=value3',
    }, []);
    expect(conditions.sort()).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
      { key: 'annotationQuery', value: 'key1=value1' },
      { key: 'annotationQuery', value: 'key2' },
      { key: 'annotationQuery', value: 'key3=value3' },
    ].sort());
  });

  it('should return right conditions with autocompleteTags', () => {
    const { conditions } = extractConditionsFromQueryParameters({
      serviceName: 'serviceA',
      spanName: 'spanA',
      minDuration: '10',
      maxDuration: '100',
      annotationQuery: 'key1=value1 and key2 and key3=value3',
      autocompleteTags: 'key4=value4 and key5=value5',
    }, []);
    expect(conditions.sort()).toEqual([
      { key: 'serviceName', value: 'serviceA' },
      { key: 'spanName', value: 'spanA' },
      { key: 'minDuration', value: 10 },
      { key: 'maxDuration', value: 100 },
      { key: 'annotationQuery', value: 'key1=value1' },
      { key: 'annotationQuery', value: 'key2' },
      { key: 'annotationQuery', value: 'key3=value3' },
      { key: 'key4', value: 'value4' },
      { key: 'key5', value: 'value5' },
    ].sort());
  });

  it('should return the right limit condition', () => {
    const { limitCondition } = extractConditionsFromQueryParameters({ limit: '15' });
    expect(limitCondition).toEqual(15);
  });

  it('should return the right lookback condition', () => {
    const { lookbackCondition } = extractConditionsFromQueryParameters({
      lookback: '1h',
      endTs: '1547098357716',
    });
    expect(lookbackCondition).toEqual({
      value: '1h',
      endTs: 1547098357716,
    });
  });

  it('should return the right custom lookback condition', () => {
    const { lookbackCondition } = extractConditionsFromQueryParameters({
      lookback: 'custom',
      endTs: '1547098357716',
      startTs: '1547098357710',
    });
    expect(lookbackCondition).toEqual({
      value: 'custom',
      endTs: 1547098357716,
      startTs: 1547098357710,
    });
  });
});

describe('getConditionKeyListWithAvailability', () => {
  it('should return the right availability', () => {
    const sorter = (a, b) => {
      const keyA = a.conditionKey.toUpperCase();
      const keyB = b.conditionKey.toUpperCase();
      if (keyA < keyB) {
        return -1;
      }
      if (keyA > keyB) {
        return 1;
      }
      return 0;
    };

    const result = getConditionKeyListWithAvailability(
      'serviceName',
      [
        { key: 'maxDuration' },
        { key: 'annotationQuery' },
        { key: 'environment' },
      ],
      ['instanceId', 'environment'],
    );
    expect(result.sort(sorter)).toEqual([
      { conditionKey: 'serviceName', isAvailable: true },
      { conditionKey: 'spanName', isAvailable: true },
      { conditionKey: 'minDuration', isAvailable: true },
      { conditionKey: 'maxDuration', isAvailable: false },
      { conditionKey: 'traceId', isAvailable: true },
      { conditionKey: 'annotationQuery', isAvailable: true }, // always true
      { conditionKey: 'instanceId', isAvailable: true },
      { conditionKey: 'environment', isAvailable: false },
    ].sort(sorter));
  });
});
