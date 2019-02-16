import {compare} from '../component_data/spanCleaner';
import {addTimestamps, getMaxDuration, mkDurationStr} from './traceSummary';
import {newSpanRow} from './spanRow';

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
  root.traverse(span => addTimestamps(span, timestamps));
  return {
    timestamp: timestamps[0] || 0,
    duration: getMaxDuration(timestamps)
  };
}

function addLayoutDetails(
  spanRow, traceTimestamp, traceDuration, depth, childIds
) { /* eslint-disable no-param-reassign */
  spanRow.childIds = childIds;
  spanRow.depth = (depth + 1) * 5;
  spanRow.depthClass = (depth - 1) % 6;

  // Add the correct width and duration string for the span
  if (spanRow.duration) { // implies traceDuration, as trace duration is derived from spans
    const width = traceDuration ? spanRow.duration / traceDuration * 100 : 0;
    spanRow.width = width < 0.1 ? 0.1 : width;
    spanRow.durationStr = mkDurationStr(spanRow.duration); // bubble over the span in trace view
  } else {
    spanRow.width = 0.1;
  }

  if (traceDuration) {
    // position the span at the correct offset in the trace diagram.
    spanRow.left = ((spanRow.timestamp - traceTimestamp) / traceDuration * 100);

    // position each annotation at the offset in the trace diagram.
    spanRow.annotations.forEach(a => { /* eslint-disable no-param-reassign */
      // left offset here is from the span
      a.left = spanRow.duration ? ((a.timestamp - spanRow.timestamp) / spanRow.duration * 100) : 0;
      // relative time is for the trace itself
      a.relativeTime = mkDurationStr(a.timestamp - traceTimestamp);
      a.width = 8; // size of the dot
    });
  } else {
    spanRow.left = 0;
  }
}

function nodeByTimestamp(a, b) {
  return compare(a.span.timestamp, b.span.timestamp);
}

export function traceToMustache(root, logsUrl) {
  const serviceNameToCount = {};
  let queue = root.queueRootMostSpans();
  const modelview = {
    traceId: queue[0].span.traceId,
    depth: 0,
    spans: []
  };

  const {timestamp, duration} = getTraceTimestampAndDuration(root);
  if (!timestamp) throw new Error(`Trace ${modelview.traceId} is missing a timestamp`);

  while (queue.length > 0) {
    let current = queue.shift();

    // This is more than a normal tree traversal, as we are merging any server spans that share the
    // same ID. When that's the case, we pull up any of their children as if they are our own.
    const spansToMerge = [current.span];
    const children = [];
    current.children.forEach(child => {
      if (current.span.id === child.span.id) {
        spansToMerge.push(child.span);
        child.children.forEach(grandChild => children.push(grandChild));
      } else {
        children.push(child);
      }
    });

    // Pulling up children may affect our sort order. We re-sort to ensure rows are added in
    // timestamp order.
    children.sort(nodeByTimestamp);
    queue = children.concat(queue);
    const childIds = children.map(child => child.span.id);

    // The mustache template expects one row per span ID. To get the correct depth class, we need to
    // count distinct span IDs above us.
    let depth = 1;
    while (current.parent && current.parent.span) {
      if (current.parent.span.id !== current.span.id) depth++;
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
    spanRow.serviceNames.forEach(serviceName => incrementEntry(serviceNameToCount, serviceName));

    modelview.spans.push(spanRow);
  }

  modelview.serviceNameAndSpanCounts = Object.keys(serviceNameToCount).sort().map(serviceName =>
    ({serviceName, spanCount: serviceNameToCount[serviceName]})
  );

  // the zoom feature needs backups and timeMarkers regardless of if there is a trace duration
  modelview.spansBackup = modelview.spans;
  modelview.timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    .map((p, index) => ({index, time: mkDurationStr(duration * p)}));
  modelview.timeMarkersBackup = modelview.timeMarkers;

  if (duration) modelview.durationStr = mkDurationStr(duration);
  if (logsUrl) modelview.logsUrl = logsUrl;

  return modelview;
}
