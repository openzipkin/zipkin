// eslint-disable no-nested-ternary
import _ from 'lodash';
import moment from 'moment';

function endpointsForSpan(span) {
  const result = [];
  if (span.localEndpoint) result.push(span.localEndpoint);
  if (span.remoteEndpoint) result.push(span.remoteEndpoint);
  return result;
}

// What's the total duration of the spans in this trace?
export function traceDuration(spans) {
  function makeList({timestamp, duration}) {
    if (!timestamp) {
      return [];
    } else if (!duration) {
      return [timestamp];
    } else {
      return [timestamp, timestamp + duration];
    }
  }

  // turns (timestamp, timestamp + duration) into an ordered list
  const timestamps = _(spans).flatMap(makeList).sort().value();

  if (timestamps.length < 2) {
    return 0;
  } else {
    const first = _.head(timestamps);
    const last = _.last(timestamps);
    return last - first;
  }
}

function getServiceNames(span) {
  return _(endpointsForSpan(span))
      .map((ep) => ep.serviceName)
      .filter((name) => name != null && name !== '')
      .uniq().value();
}

export function getGroupedTimestamps(spans) {
  const spanTimestamps = _(spans).flatMap((span) => getServiceNames(span).map((serviceName) => ({
    serviceName,
    timestamp: span.timestamp, // only used by totalDuration
    duration: span.duration
  }))).value();

  const grouped = _(spanTimestamps).groupBy((sts) => sts.serviceName).value();

  // wash out the redundant name. TODO: rewrite this whole method as it seems easier imperatively
  return _(grouped).mapValues((ntds) => ntds.map((ntd) => ({
    timestamp: ntd.timestamp,
    duration: ntd.duration || 0
  }))).value();
}

// returns 'critical' if one of the spans has an error tag, else
// returns 'transient' if one of the spans has an ERROR annotation, else
// returns 'none'
export function getTraceErrorType(spans) {
  let traceType = 'none';
  for (let i = 0; i < spans.length; i++) {
    const span = spans[i];
    if (span.tags.error !== undefined) { // empty error tag is ok
      return 'critical';
    } else if (traceType === 'none' &&
               _(span.annotations).findIndex(ann => ann.value === 'error') !== -1) {
      traceType = 'transient';
    }
  }
  return traceType;
}

// Returns null on empty or when missing a timestamp
export function traceSummary(trace = []) {
  if (trace.length === 0) {
    throw new Error('Trace was empty');
  }
  if (!trace[0].timestamp) {
    throw new Error('Trace is missing a timestamp');
  }

  const traceId = trace[0].traceId;
  const timestamp = trace[0].timestamp;
  const duration = traceDuration(trace);
  const groupedTimestamps = getGroupedTimestamps(trace);
  const errorType = getTraceErrorType(trace);
  const spanCount = trace.length;
  return {
    traceId,
    timestamp,
    duration,
    groupedTimestamps,
    errorType,
    spanCount
  };
}

// This returns a total duration by merging all overlapping intervals found in the the input.
//
// This is used to create servicePercentage for index.mustache when a service is selected
export function totalDuration(timestampAndDurations) {
  const filtered = _(timestampAndDurations)
    .filter((s) => s.duration) // filter out anything we can't make an interval out of
    .sortBy('timestamp').value(); // to merge intervals, we need the input sorted

  if (filtered.length === 0) {
    return 0;
  }
  if (filtered.length === 1) {
    return filtered[0].duration;
  }

  let result = filtered[0].duration;
  let currentIntervalEnd = filtered[0].timestamp + filtered[0].duration;

  for (let i = 1; i < filtered.length; i++) {
    const next = filtered[i];
    const nextIntervalEnd = next.timestamp + next.duration;

    if (nextIntervalEnd <= currentIntervalEnd) { // we are still in the interval
      continue;
    } else if (next.timestamp <= currentIntervalEnd) { // we extending the interval
      result += nextIntervalEnd - currentIntervalEnd;
      currentIntervalEnd = nextIntervalEnd;
    } else { // this is a new interval
      result += next.duration;
      currentIntervalEnd = nextIntervalEnd;
    }
  }

  return result;
}

function formatDate(timestamp, utc) {
  let m = moment(timestamp / 1000);
  if (utc) {
    m = m.utc();
  }
  return m.format('MM-DD-YYYYTHH:mm:ss.SSSZZ');
}

export function mkDurationStr(duration) {
  if (duration === 0 || typeof duration === 'undefined') {
    return '';
  } else if (duration < 1000) {
    return `${duration.toFixed(0)}μ`;
  } else if (duration < 1000000) {
    if (duration % 1000 === 0) { // Sometimes spans are in milliseconds resolution
      return `${(duration / 1000).toFixed(0)}ms`;
    }
    return `${(duration / 1000).toFixed(3)}ms`;
  } else {
    return `${(duration / 1000000).toFixed(3)}s`;
  }
}

export function getServiceNameAndSpanCounts(groupedTimestamps) {
  return _(groupedTimestamps).toPairs().map(([serviceName, sts]) => ({
    serviceName,
    spanCount: sts.length
  })).sortBy('serviceName').value();
}

// maxSpanDurationStr is only used in index.mustache
export function getServiceSummaries(groupedTimestamps) {
  return _(groupedTimestamps).toPairs()
    .map(([serviceName, sts]) => ({
      serviceName,
      spanCount: sts.length,
      maxSpanDuration: Math.max(...sts.map(t => t.duration))
    }))
    .orderBy(['maxSpanDuration', 'serviceName'], ['desc', 'asc'])
    .map(summary => ({
      serviceName: summary.serviceName,
      spanCount: summary.spanCount,
      maxSpanDurationStr: mkDurationStr(summary.maxSpanDuration)
    })).value();
}

export function traceSummariesToMustache(serviceName = null, traceSummaries, utc = false) {
  const maxDuration = Math.max(...traceSummaries.map((s) => s.duration));

  return traceSummaries.map((t) => {
    const timestamp = t.timestamp;
    const duration = t.duration;
    const groupedTimestamps = t.groupedTimestamps;

    const res = {
      traceId: t.traceId,
      startTs: formatDate(timestamp, utc),
      timestamp,
      duration: duration / 1000,
      durationStr: mkDurationStr(duration),
      width: parseInt(parseFloat(duration) / parseFloat(maxDuration) * 100, 10),
      spanCount: t.spanCount,
      serviceSummaries: getServiceSummaries(groupedTimestamps),
      infoClass: t.errorType === 'none' ? '' : `trace-error-${t.errorType}`
    };

    // Only add a service percentage when there is a duration for it
    if (serviceName && groupedTimestamps[serviceName]) {
      const serviceTime = totalDuration(groupedTimestamps[serviceName]);
      res.servicePercentage = parseInt(parseFloat(serviceTime) / parseFloat(duration) * 100, 10);
    }

    return res;
  }).sort((t1, t2) => {
    const durationComparison = t2.duration - t1.duration;
    if (durationComparison === 0) {
      return t1.traceId.localeCompare(t2.traceId);
    } else {
      return durationComparison;
    }
  });
}
