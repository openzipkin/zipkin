import { orderedConditionKeyList, nextInitialConditionKey } from './global-search';

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
