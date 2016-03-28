import {convertToApiQuery} from '../../js/component_data/default';

describe('convertToApiQuery', () => {
  const should = require('chai').should();

  it('should not require startTs', () => {
    const parsed = convertToApiQuery('?endTs=1459169770000');

    parsed.endTs.should.equal('1459169770000');
    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });

  it('should replace startTs with lookback', () => {
    const parsed = convertToApiQuery('?startTs=1459169760000&endTs=1459169770000');

    parsed.endTs.should.equal('1459169770000');
    parsed.lookback.should.equal('10000');
    should.not.exist(parsed.startTs);
  });

  it('should not add negative lookback', () => {
    const parsed = convertToApiQuery('?endTs=1459169760000&startTs=1459169770000');

    should.not.exist(parsed.lookback);
    should.not.exist(parsed.startTs);
  });
});
