import {addStartEndTimestamps, getMaxDuration, mkDurationStr} from './traceSummary';
import {SPAN_V1} from '../spanConverter';

function incrementEntry(dict, key) {
  if (dict[key]) {
    dict[key]++; // eslint-disable-line no-param-reassign
  } else {
    dict[key] = 1; // eslint-disable-line no-param-reassign
  }
}

// We need to do an initial traversal in order to get the timestamp and duration of the trace,
// as that is used for positioning spans later.
function getTraceTimestampAndDuration(root) {
  const timestamps = [];
  root.traverse(span => addStartEndTimestamps(span, timestamps));
  return {
    traceTimestamp: timestamps[0] || 0,
    traceDuration: getMaxDuration(timestamps)
  };
}

export function traceToMustache(root, logsUrl) {
  const serviceNameToCount = {};
  const queue = root.queueRootMostSpans();
  const modelview = {
    traceId: queue[0].span.traceId,
    depth: 0,
    spans: []
  };

  const {traceTimestamp, traceDuration} = getTraceTimestampAndDuration(root);
  if (!traceTimestamp) throw new Error(`Trace ${modelview.traceId} is missing a timestamp`);

  while (queue.length > 0) {
    let current = queue.shift();

    // This is more than a normal tree traversal, as we are merging any server spans that share the
    // same ID. When that's the case, we pull up any of their children as if they are our own.
    const spansToMerge = [current.span];
    const childIds = [];
    current.children.forEach(child => {
      if (current.span.id === child.span.id) {
        spansToMerge.push(child.span);
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

    const span = SPAN_V1.merge(spansToMerge);
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
        // TODO: do we really want relative time of annotations to be relative to the trace?
        relativeTime: mkDurationStr(a.timestamp - traceTimestamp),
        width: 8
      })),
      tags: span.tags,
      serviceNames: span.serviceNames,
      childIds,
      errorType: span.errorType
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
    if (span.parentId) uiSpan.parentId = span.parentId;

    // NOTE: This will increment both the local and remote service name
    //
    // TODO: We should only do this if it is a leaf span and a client or producer. If we are at the
    // bottom of the tree, it can be helpful to count also against a remote uninstrumented service.
    uiSpan.serviceNames.forEach(serviceName => incrementEntry(serviceNameToCount, serviceName));

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
