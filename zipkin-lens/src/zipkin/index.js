import { correctForClockSkew } from './clock-skew';
import { convert, mergeById } from './span-converter';
import detailedTraceSummary from './detailed-trace';

export { correctForClockSkew } from './clock-skew';
export { traceSummary, traceSummaries } from './search-trace';
export { convert, mergeById } from './span-converter';
export { default as detailedTraceSummary } from './detailed-trace';

export const getDetailedTraceSummary = (rawTrace) => {
  const v1Trace = rawTrace.map(convert);
  const mergedTrace = mergeById(v1Trace);
  const clockSkewCorrectedTrace = correctForClockSkew(mergedTrace);
  return detailedTraceSummary(clockSkewCorrectedTrace);
};
