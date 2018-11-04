// eslint-disable no-nested-ternary
import _ from 'lodash';
import moment from 'moment';

import {Constants} from './traceConstants';

function endpointsForSpan(span) {
  return _.union(
    (span.annotations || []).map(a => a.endpoint),
    (span.binaryAnnotations || []).map(a => a.endpoint)
  ).filter(h => h != null);
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
    return null;
  } else {
    const first = _.head(timestamps);
    const last = _.last(timestamps);
    return last - first;
  }
}

export function getServiceNames(span) {
  return _(endpointsForSpan(span))
      .map((ep) => ep.serviceName)
      .filter((name) => name != null && name !== '')
      .uniq().value();
}

function findServiceNameForBinaryAnnotation(span, key) {
  const binaryAnnotation = _(span.binaryAnnotations || []).find((ann) =>
            ann.key === key
            && ann.endpoint != null
            && ann.endpoint.serviceName != null
            && ann.endpoint.serviceName !== '');
  return binaryAnnotation ? binaryAnnotation.endpoint.serviceName : null;
}

function findServiceNameForAnnotation(span, values) {
  const annotation = _(span.annotations || []).find((ann) =>
            values.indexOf(ann.value) !== -1
            && ann.endpoint != null
            && ann.endpoint.serviceName != null
            && ann.endpoint.serviceName !== '');
  return annotation ? annotation.endpoint.serviceName : null;
}

export function getServiceName(span) {
  // Most authoritative is the label of the server's endpoint
  const serverAddressServiceName = findServiceNameForBinaryAnnotation(span, Constants.SERVER_ADDR);
  if (serverAddressServiceName) {
    return serverAddressServiceName;
  }

  // Next, the label of any server annotation, logged by an instrumented server
  const serverAnnotationServiceName = findServiceNameForAnnotation(span, Constants.CORE_SERVER);
  if (serverAnnotationServiceName) {
    return serverAnnotationServiceName;
  }

  // Next, the label of any messaging annotation, logged by an instrumented producer or consumer
  const messageAnnotationServiceName = findServiceNameForAnnotation(span, Constants.CORE_MESSAGE);
  if (messageAnnotationServiceName) {
    return messageAnnotationServiceName;
  }

  // Next is the label of the client's endpoint
  const clientAddressServiceName = findServiceNameForBinaryAnnotation(span, Constants.CLIENT_ADDR);
  if (clientAddressServiceName) {
    return clientAddressServiceName;
  }

  // Next is the label of any client annotation, logged by an instrumented client
  const clientAnnotationServiceName = findServiceNameForAnnotation(span, Constants.CORE_CLIENT);
  if (clientAnnotationServiceName) {
    return clientAnnotationServiceName;
  }

  // Next is the label of the broker's endpoint
  const brokerAddressServiceName = findServiceNameForBinaryAnnotation(span, Constants.MESSAGE_ADDR);
  if (brokerAddressServiceName) {
    return brokerAddressServiceName;
  }

  // Then is the label of the local component's endpoint
  const localServiceName = findServiceNameForBinaryAnnotation(span, Constants.LOCAL_COMPONENT);
  if (localServiceName) {
    return localServiceName;
  }

  // Finally, anything so that the service name isn't blank!
  const allServiceNames = getServiceNames(span);
  return allServiceNames.length === 0 ? null : allServiceNames[0];
}

export function getGroupedTimestamps(spans) {
  const spanTimestamps = _(spans).flatMap((span) => getServiceNames(span).map((serviceName) => ({
    serviceName,
    timestamp: span.timestamp,
    duration: span.duration
  }))).value();

  const grouped = _(spanTimestamps).groupBy((sts) => sts.serviceName).value();

  // wash out the redundant name. TODO: rewrite this whole method as it seems easier imperatively
  return _(grouped).mapValues((ntds) => ntds.map((ntd) => ({
    timestamp: ntd.timestamp,
    duration: ntd.duration
  }))).value();
}


// returns 'critical' if one of the spans has an ERROR binary annotation, else
// returns 'transient' if one of the spans has an ERROR annotation, else
// returns 'none'
export function getTraceErrorType(spans) {
  let traceType = 'none';
  for (let i = 0; i < spans.length; i++) {
    const span = spans[i];
    if (_(span.binaryAnnotations || []).findIndex(ann => ann.key === Constants.ERROR) !== -1) {
      return 'critical';
    } else if (traceType === 'none' &&
               _(span.annotations || []).findIndex(ann => ann.value === Constants.ERROR) !== -1) {
      traceType = 'transient';
    }
  }
  return traceType;
}

function endpointEquals(e1, e2) {
  return (e1.ipv4 === e2.ipv4 || e1.ipv6 === e2.ipv6)
    && e1.port === e2.port && e1.serviceName === e2.serviceName;
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
  const duration = traceDuration(trace) || 0;
  const groupedTimestamps = getGroupedTimestamps(trace);
  const endpoints = _(trace).flatMap(endpointsForSpan).uniqWith(endpointEquals).value();
  const errorType = getTraceErrorType(trace);
  const spanCount = trace.length;
  return {
    traceId,
    timestamp,
    duration,
    groupedTimestamps,
    endpoints,
    errorType,
    spanCount
  };
}

export function totalServiceTime(stamps, acc = 0) {
  // This is a recursive function that performs arithmetic on duration
  // If duration is undefined, it will infinitely recurse. Filter out that case
  const filtered = stamps.filter((s) => s.duration);
  if (filtered.length === 0) {
    return acc;
  } else {
    const ts = _(filtered).minBy((s) => s.timestamp);
    const [current, next] = _(filtered)
        .partition((t) =>
          t.timestamp >= ts.timestamp
          && t.timestamp + t.duration <= ts.timestamp + ts.duration)
        .value();
    const endTs = Math.max(...current.map((t) => t.timestamp + t.duration));
    return totalServiceTime(next, acc + (endTs - ts.timestamp));
  }
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
      const serviceTime = totalServiceTime(groupedTimestamps[serviceName]);
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
