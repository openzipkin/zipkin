import { getGraphHeight, getGraphLineHeight } from './util';

describe('getGraphHeight', () => {
  it('should return proper value', () => {
    expect(getGraphHeight(-1)).toEqual(0);
    expect(getGraphHeight(0)).toEqual(0);
    expect(getGraphHeight(1)).toEqual(0);
    expect(getGraphHeight(2)).toEqual(2 * 5);
    expect(getGraphHeight(14)).toEqual(14 * 5);
    expect(getGraphHeight(15)).toEqual(75);
    expect(getGraphHeight(16)).toEqual(75);
    expect(getGraphHeight(100)).toEqual(75);
  });
});

describe('getGraphLineHeight', () => {
  it('should return proper value', () => {
    expect(getGraphLineHeight(-1)).toEqual(0);
    expect(getGraphLineHeight(0)).toEqual(0);
    expect(getGraphLineHeight(1)).toEqual(0);
    expect(getGraphLineHeight(2)).toEqual(5);
    expect(getGraphLineHeight(14)).toEqual(5);
    expect(getGraphLineHeight(15)).toEqual(5);
    expect(getGraphLineHeight(16)).toEqual(75 / 16);
    expect(getGraphLineHeight(20)).toEqual(75 / 20);
    expect(getGraphLineHeight(100)).toEqual(1);
  });
});
