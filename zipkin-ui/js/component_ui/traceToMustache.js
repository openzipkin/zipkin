import {getTraceDuration, mkDurationStr} from './traceSummary';
import {SPAN_V1} from '../spanConverter';

function getErrorType(span) {
  if (span.tags.findIndex(b => b.key === 'error') !== -1) {
    return 'critical';
  } else if (span.annotations.findIndex(a => a.value === 'error') !== -1) { // TODO: indexOf!
    return 'transient';
  } else {
    return 'none';
  }
}

function incrementEntry(dict, key) {
  if (dict[key]) {
    dict[key]++; // eslint-disable-line no-param-reassign
  } else {
    dict[key] = 1; // eslint-disable-line no-param-reassign
  }
}

export function traceToMustache(root, logsUrl) {
  const spans = root.traverse();
  if (spans.length === 0) throw new Error('Trace was empty');
  const traceTimestamp = spans[0].timestamp;
  if (!traceTimestamp) throw new Error('Trace is missing a timestamp');

  // currently we need to pass one traversal in order to get the duration
  const traceDuration = getTraceDuration(spans);

  const modelview = {
    traceId: spans[0].traceId,
    depth: 0,
    spans: []
  };

  const queue = [];

  // since the input data could be headless, we first push onto the queue the root-most spans
  if (typeof(root.span) === 'undefined') { // synthetic root
    root.children.forEach(child => queue.push(child));
  } else {
    queue.push(root);
  }

  const serviceNameToCount = {};

  while (queue.length > 0) {
    let current = queue.shift();

    let span = SPAN_V1.convert(current.span);

    // This is more than a normal tree traversal, as we are merging any server spans that share the
    // same ID. When that's the case, we pull up any of their children as if they are our own.
    const childIds = [];
    current.children.forEach(child => {
      if (child.span.id === span.id) {
        span = SPAN_V1.merge(span, SPAN_V1.convert(child.span));
        child.children.forEach(grandChild => {
          queue.push(grandChild);
          childIds.push(grandChild.span.id);
        });
      } else {
        queue.push(child);
        childIds.push(child.span.id);
      }
    });

    // The mustache template expects one row per span ID. To get the correct depth class, we need to
    // count distinct span IDs above us.
    let spanRowDepth = 1;
    while (current.parent && current.parent.span) {
      if (current.parent.span.id !== current.span.id) spanRowDepth++;
      current = current.parent;
    }
    // If we are the deepest span, mark the trace accordingly
    if (spanRowDepth > modelview.depth) modelview.depth = spanRowDepth;

    // TODO: merge the remaining "v1" type logic here so we don't have the confusion of a
    // double transformation
    const spanStartTs = span.timestamp || traceTimestamp;
    const spanDuration = span.duration || 0;
    const uiSpan = {
      spanId: span.id,
      depth: (spanRowDepth + 1) * 5,
      depthClass: (spanRowDepth - 1) % 6,
      annotations: span.annotations.map((a) => ({
        ...a,
        left: spanDuration ? (a.timestamp - spanStartTs) / spanDuration * 100 : 0,
        relativeTime: mkDurationStr(a.timestamp - traceTimestamp),
        width: 8
      })),
      tags: span.tags,
      errorType: getErrorType(span)
    };

    // Optionally add fields instead of defaulting to empty string
    if (span.name) uiSpan.spanName = span.name;
    if (spanDuration) {
      const width = traceDuration ? spanDuration / traceDuration * 100 : 0;
      uiSpan.width = width < 0.1 ? 0.1 : width;
      uiSpan.left = parseFloat(spanStartTs - traceTimestamp) / parseFloat(traceDuration) * 100;
      uiSpan.duration = spanDuration; // used in zoom
      uiSpan.durationStr = mkDurationStr(spanDuration); // bubble over the span in trace view
    } else {
      uiSpan.left = 0;
      uiSpan.width = 0.1;
    }
    if (span.serviceName) uiSpan.serviceName = span.serviceName;
    if (span.serviceNames.length !== 0) uiSpan.serviceNames = span.serviceNames.join(',');
    if (span.parentId) uiSpan.parentId = span.parentId;
    if (childIds.length !== 0) uiSpan.children = childIds.join(','); // used for expand and collapse

    // NOTE: This will increment both the local and remote service name
    //
    // TODO: We should only do this if it is a leaf span and a client or producer. If we are at the
    // bottom of the tree, it can be helpful to count also against a remote uninstrumented service.
    span.serviceNames.forEach(serviceName => incrementEntry(serviceNameToCount, serviceName));

    modelview.spans.push(uiSpan);
  }

  modelview.serviceNameAndSpanCounts = Object.keys(serviceNameToCount).sort().map(serviceName =>
    ({serviceName, spanCount: serviceNameToCount[serviceName]})
  );

  // the zoom feature needs backups and timeMarkers regardless of if there is a trace duration
  modelview.spansBackup = modelview.spans;
  modelview.timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    .map((p, index) => ({index, time: mkDurationStr(traceDuration * p)}));
  modelview.timeMarkersBackup = modelview.timeMarkers;

  if (traceDuration) modelview.durationStr = mkDurationStr(traceDuration);
  if (logsUrl) modelview.logsUrl = logsUrl;

  return modelview;
}
