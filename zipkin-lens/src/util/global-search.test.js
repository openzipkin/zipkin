import queryString from 'query-string';

import {
  orderedConditionKeyList,
  nextInitialConditionKey,
  buildQueryParametersWithConditions,
  buildApiQueryParameters,
  extractConditionsFromQueryParameters,
} from './global-search';

describe('nextInitialConditionKey', () => {
  it('should return the first condition key when conditions is empty', () => {
    expect(nextInitialConditionKey([])).toEqual(orderedConditionKeyList[0]);
  });

  it('should return the unused condition key', () => {
    expect(nextInitialConditionKey([
      { key: orderedConditionKeyList[0] },
      { key: orderedConditionKeyList[1] },
      { key: orderedConditionKeyList[3] },
    ])).toEqual(orderedConditionKeyList[2]);
  });

  it('should return the last condition key when all condition keys are used', () => {
    const conditions = orderedConditionKeyList.map(conditionKey => ({ key: conditionKey }));
    expect(nextInitialConditionKey(conditions))
      .toEqual(orderedConditionKeyList[orderedConditionKeyList.length - 1]);
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
    });
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
