/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import orderBy from 'lodash/orderBy';
import moment from 'moment';
import { compare } from './span-cleaner';
import { getErrorType, newSpanRow, getServiceName } from './span-row';

// To ensure data doesn't scroll off the screen, we need all timestamps, not just
// client/server ones.
export function addTimestamps(span, timestamps) {
  if (!span.timestamp) return;
  timestamps.push(span.timestamp);
  if (!span.duration) return;
  timestamps.push(span.timestamp + span.duration);
}

export function getMaxDuration(timestamps) {
  if (timestamps.length > 1) {
    timestamps.sort();
    return timestamps[timestamps.length - 1] - timestamps[0];
  }
  return 0;
}

function pushEntry(dict, key, value) {
  if (dict[key]) {
    dict[key].push(value);
  } else {
    dict[key] = [value]; // eslint-disable-line no-param-reassign
  }
}

function addServiceNameTimestampDuration(span, groupedTimestamps) {
  const value = {
    timestamp: span.timestamp || 0, // only used by totalDuration
    duration: span.duration || 0,
  };

  if (span.localEndpoint && span.localEndpoint.serviceName) {
    pushEntry(groupedTimestamps, span.localEndpoint.serviceName, value);
  }
  // TODO: only do this if it is a leaf span and a client or producer.
  // If we are at the bottom of the tree, it can be helpful to count also against a remote
  // uninstrumented service
  if (span.remoteEndpoint && span.remoteEndpoint.serviceName) {
    pushEntry(groupedTimestamps, span.remoteEndpoint.serviceName, value);
  }
}

function nodeByTimestamp(a, b) {
  return compare(a.span.timestamp, b.span.timestamp);
}

// Returns null on empty or when missing a timestamp
export function traceSummary(root) {
  const timestamps = [];
  const groupedTimestamps = {};

  let traceId;
  let spanCount = 0;
  let errorType = 'none';

  root.traverse((span) => {
    spanCount += 1;
    traceId = span.traceId;
    errorType = getErrorType(span, errorType);
    addTimestamps(span, timestamps);
    addServiceNameTimestampDuration(span, groupedTimestamps);
  });

  if (timestamps.length === 0)
    throw new Error(`Trace ${traceId} is missing a timestamp`);

  // If the first element does not exist, Error will be thrown.
  // So we don't have to check rootSpan exisitence.
  const [rootSpan] = root.queueRootMostSpans();
  const rootServiceName =
    getServiceName(rootSpan._span.localEndpoint) ||
    getServiceName(rootSpan._span.remoteEndpoint) ||
    'unknown';
  const rootSpanName = rootSpan._span.name || 'unknown';

  return {
    traceId,
    timestamp: timestamps[0],
    duration: getMaxDuration(timestamps),
    groupedTimestamps,
    errorType,
    spanCount,
    root: {
      serviceName: rootServiceName,
      spanName: rootSpanName,
    },
  };
}

function formatDate(timestamp, utc) {
  let m = moment(timestamp / 1000);
  if (utc) {
    m = m.utc();
  }
  return m.format('MM-DD-YYYYTHH:mm:ss.SSSZZ');
}

export function mkDurationStr(duration) {
  if (duration === 0) {
    return '0ms';
  }
  if (typeof duration === 'undefined') {
    return '';
  }
  if (duration < 1000) {
    return `${duration.toFixed(0)}μs`;
  }
  if (duration < 1000000) {
    if (duration % 1000 === 0) {
      // Sometimes spans are in milliseconds resolution
      return `${(duration / 1000).toFixed(0)}ms`;
    }
    return `${(duration / 1000).toFixed(3)}ms`;
  }
  return `${(duration / 1000000).toFixed(3)}s`;
}

// Returns a list of {:serviceName, :spanCount} ordered descending by trace duration
export function getServiceSummaries(groupedTimestamps) {
  const services = Object.entries(groupedTimestamps).map(
    ([serviceName, sts]) => ({
      serviceName,
      spanCount: sts.length,
      maxSpanDuration: Math.max(...sts.map((t) => t.duration)),
    }),
  );
  return orderBy(
    services,
    ['maxSpanDuration', 'serviceName'],
    ['desc', 'asc'],
  ).map((summary) => ({
    serviceName: summary.serviceName,
    spanCount: summary.spanCount,
  }));
}

export function traceSummaries(serviceName, summaries, utc = false) {
  const maxDuration = Math.max(...summaries.map((s) => s.duration));

  return summaries
    .map((t) => {
      const { timestamp } = t;

      const res = {
        traceId: t.traceId, // used to navigate to trace screen
        timestamp, // used only for client-side sort
        startTs: formatDate(timestamp, utc),
        spanCount: t.spanCount,
        root: t.root,
      };

      const duration = t.duration || 0;
      if (duration) {
        // used to show the relative duration this trace was compared to others
        res.width = parseInt(
          (parseFloat(duration) / parseFloat(maxDuration)) * 100,
          10,
        );
        res.duration = duration; // Used in summary view and client-side sort
        res.durationStr = mkDurationStr(duration);
      }

      // groupedTimestamps is keyed by service name, if there are no service names in the trace,
      // don't try to add data dependent on service names.
      if (Object.keys(t.groupedTimestamps).length !== 0) {
        res.serviceSummaries = getServiceSummaries(t.groupedTimestamps);
      } else {
        res.serviceSummaries = [];
      }

      if (t.errorType !== 'none') res.infoClass = `trace-error-${t.errorType}`;
      return res;
    })
    .sort((t1, t2) => {
      const durationComparison = t2.duration - t1.duration;
      if (durationComparison === 0) {
        return t1.traceId.localeCompare(t2.traceId);
      }
      return durationComparison;
    });
}

function incrementEntry(dict, key) {
  if (dict[key]) {
    dict[key] += 1; // eslint-disable-line no-param-reassign
  } else {
    dict[key] = 1; // eslint-disable-line no-param-reassign
  }
}

// We need to do an initial traversal in order to get the timestamp and duration of the trace,
// as that is used for positioning spans later.
function getTraceTimestampAndDuration(root) {
  const timestamps = [];
  root.traverse((span) => addTimestamps(span, timestamps));
  return {
    timestamp: timestamps[0] || 0,
    duration: getMaxDuration(timestamps),
  };
}

function addLayoutDetails(
  spanRow,
  traceTimestamp,
  traceDuration,
  depth,
  childIds,
) {
  /* eslint-disable no-param-reassign */
  spanRow.childIds = childIds;
  spanRow.depth = depth + 1;
  spanRow.depthClass = (depth - 1) % 6;

  // Add the correct width and duration string for the span
  if (spanRow.duration) {
    // implies traceDuration, as trace duration is derived from spans
    const width = traceDuration ? (spanRow.duration / traceDuration) * 100 : 0;
    spanRow.width = width < 0.1 ? 0.1 : width;
    spanRow.durationStr = mkDurationStr(spanRow.duration); // bubble over the span in trace view
  } else {
    spanRow.width = 0.1;
    spanRow.durationStr = '';
  }

  if (traceDuration) {
    // position the span at the correct offset in the trace diagram.
    spanRow.left = ((spanRow.timestamp - traceTimestamp) / traceDuration) * 100;

    // position each annotation at the offset in the trace diagram.
    spanRow.annotations.forEach((a) => {
      /* eslint-disable no-param-reassign */
      // left offset here is from the span
      a.left = spanRow.duration
        ? ((a.timestamp - spanRow.timestamp) / spanRow.duration) * 100
        : 0;
      // relative time is for the trace itself
      a.relativeTime = mkDurationStr(a.timestamp - traceTimestamp);
      a.width = 8; // size of the dot
    });
  } else {
    spanRow.left = 0;
  }
}

// TODO: Revisit naming
export function detailedTraceSummary(root) {
  const serviceNameToCount = {};
  let queue = root.queueRootMostSpans();
  const modelview = {
    traceId: queue[0].span.traceId,
    depth: 0,
    spans: [],
  };

  const { timestamp, duration } = getTraceTimestampAndDuration(root);
  if (!timestamp)
    throw new Error(`Trace ${modelview.traceId} is missing a timestamp`);

  while (queue.length > 0) {
    let current = queue.shift();

    // This is more than a normal tree traversal, as we are merging any server spans that share the
    // same ID. When that's the case, we pull up any of their children as if they are our own.
    const spansToMerge = [current.span];
    const children = [];
    current.children.forEach((child) => {
      if (current.span.id === child.span.id) {
        spansToMerge.push(child.span);
        child.children.forEach((grandChild) => children.push(grandChild));
      } else {
        children.push(child);
      }
    });

    // Pulling up children may affect our sort order. We re-sort to ensure rows are added in
    // timestamp order.
    children.sort(nodeByTimestamp);
    queue = children.concat(queue);
    const childIds = children.map((child) => child.span.id);

    // The mustache template expects one row per span ID. To get the correct depth class, we need to
    // count distinct span IDs above us.
    let depth = 1;
    while (current.parent && current.parent.span) {
      if (current.parent.span.id !== current.span.id) depth += 1;
      current = current.parent;
    }
    // If we are the deepest span, mark the trace accordingly
    if (depth > modelview.depth) modelview.depth = depth;

    const isLeafSpan = children.length === 0;
    const spanRow = newSpanRow(spansToMerge, isLeafSpan);

    addLayoutDetails(spanRow, timestamp, duration, depth, childIds);
    // NOTE: This will increment both the local and remote service name
    //
    // TODO: We should only do this if it is a leaf span and a client or producer. If we are at the
    // bottom of the tree, it can be helpful to count also against a remote uninstrumented service.
    spanRow.serviceNames.forEach((serviceName) =>
      incrementEntry(serviceNameToCount, serviceName),
    );

    modelview.spans.push(spanRow);
  }

  modelview.rootSpan = {};
  // If the first element does not exist, Error will be thrown.
  // So we don't have to check rootSpan existence.
  const [rootSpan] = root.queueRootMostSpans();
  modelview.rootSpan.serviceName =
    getServiceName(rootSpan._span.localEndpoint) ||
    getServiceName(rootSpan._span.remoteEndpoint) ||
    'unknown';
  modelview.rootSpan.spanName = rootSpan._span.name || 'unknown';

  modelview.serviceNameAndSpanCounts = Object.keys(serviceNameToCount)
    .sort()
    .map((serviceName) => ({
      serviceName,
      spanCount: serviceNameToCount[serviceName],
    }));

  // the zoom feature needs backups and timeMarkers regardless of if there is a trace duration
  modelview.spansBackup = modelview.spans;
  modelview.timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0].map((p, index) => ({
    index,
    time: mkDurationStr(duration * p),
  }));
  modelview.timeMarkersBackup = modelview.timeMarkers;

  modelview.duration = duration;
  modelview.durationStr = mkDurationStr(duration);

  return modelview;
}
