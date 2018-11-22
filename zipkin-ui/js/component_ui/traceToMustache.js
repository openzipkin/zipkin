import _ from 'lodash';
import {
  traceSummary,
  getServiceNameAndSpanCounts,
  mkDurationStr
} from './traceSummary';
import {SPAN_V1} from '../spanConverter';
import {Constants, ConstantNames} from './traceConstants';

export function getRootSpans(spans) {
  const ids = spans.map((s) => s.id);
  return spans.filter((s) => ids.indexOf(s.parentId) === -1);
}

function compareSpan(s1, s2) {
  return (s1.timestamp || 0) - (s2.timestamp || 0);
}

function endpointsForSpan(span) {
  return _.union(
    (span.annotations || []).map(a => a.endpoint),
    (span.binaryAnnotations || []).map(a => a.endpoint)
  ).filter(h => h != null);
}

function getServiceNames(span) {
  return _(endpointsForSpan(span))
      .map((ep) => ep.serviceName)
      .filter((name) => name != null && name !== '')
      .uniq().value();
}

function childrenToList(entry) {
  const deepChildren = _(entry.children || [])
    .sort((e1, e2) => compareSpan(e1.span, e2.span))
    .flatMap(childrenToList).value();
  return [entry.span, ...deepChildren];
}

function createSpanTreeEntry(span, trace, indexByParentId = null) {
  const idx = indexByParentId != null ? indexByParentId : _(trace)
        .filter((s) => s.parentId != null)
        .groupBy((s) => s.parentId)
        .value();

  return {
    span,
    children: (idx[span.id] || [])
      .map((s) => createSpanTreeEntry(s, trace, idx))
  };
}

function recursiveGetRootMostSpan(idSpan, prevSpan) {
  if (prevSpan.parentId && idSpan[prevSpan.parentId]) {
    return recursiveGetRootMostSpan(idSpan, idSpan[prevSpan.parentId]);
  } else {
    return prevSpan;
  }
}

function getRootMostSpan(spans) {
  const firstWithoutParent = _(spans).find((s) => !s.parentId);
  if (firstWithoutParent) {
    return firstWithoutParent;
  } else {
    const idToSpanMap = _(spans).groupBy((s) => s.id).mapValues(([s]) => s);
    return recursiveGetRootMostSpan(idToSpanMap, spans[0]);
  }
}

function treeDepths(entry, startDepth) {
  const initial = {};
  initial[entry.span.id] = startDepth;
  if (entry.children.length === 0) {
    return initial;
  }
  return _(entry.children || []).reduce((prevMap, child) => {
    const childDepths = treeDepths(child, startDepth + 1);
    const newCombined = {
      ...prevMap,
      ...childDepths
    };
    return newCombined;
  }, initial);
}


function toSpanDepths(spans) {
  const rootMost = getRootMostSpan(spans);
  const entry = createSpanTreeEntry(rootMost, spans);
  return treeDepths(entry, 1);
}

export function formatEndpoint({ipv4, ipv6, port, serviceName}) {
  if (ipv4 || ipv6) {
    const ip = ipv6 ? `[${ipv6}]` : ipv4; // arbitrarily prefer ipv6
    const portString = port ? `:${port}` : '';
    const serviceNameString = serviceName ? ` (${serviceName})` : '';
    return ip + portString + serviceNameString;
  } else {
    return serviceName || '';
  }
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

export function getServiceName(span) { // export for testing
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

export default function traceToMustache(v2Trace, logsUrl = undefined) {
  const trace = SPAN_V1.convertTrace(v2Trace);
  const t = traceSummary(v2Trace);
  const traceId = t.traceId;
  const duration = t.duration;
  const serviceNameAndSpanCounts = getServiceNameAndSpanCounts(t.groupedTimestamps);

  const services = serviceNameAndSpanCounts.length || 0;
  const groupByParentId = _(trace).groupBy((s) => s.parentId).value();

  const traceTimestamp = trace[0].timestamp || 0;
  const spanDepths = toSpanDepths(trace);

  const depth = Math.max(..._.values(spanDepths));

  const spans = _(getRootSpans(trace)).flatMap(
    (rootSpan) => childrenToList(createSpanTreeEntry(rootSpan, trace))).map((span) => {
      const spanStartTs = span.timestamp || traceTimestamp;
      const spanDepth = spanDepths[span.id] || 1;
      const width = (span.duration || 0) / duration * 100;
      let errorType = 'none';

      const binaryAnnotations = (span.binaryAnnotations || [])
      // empty "lc" tags are just a hack for "Local Address" which is processed below
      .filter((a) => a.key !== Constants.LOCAL_COMPONENT || a.value.length > 0)
      .map((a) => {
        if (a.key === Constants.ERROR) {
          errorType = 'critical';
        }
        const key = ConstantNames[a.key] || a.key;
        if (Constants.CORE_ADDRESS.indexOf(a.key) !== -1) {
          return {
            ...a,
            key,
            value: formatEndpoint(a.endpoint)
          };
        }
        return {
          ...a,
          key
        };
      });

      if (errorType !== 'critical') {
        if (_(span.annotations || []).findIndex(ann => ann.value === Constants.ERROR) !== -1) {
          errorType = 'transient';
        }
      }

      const localComponentAnnotation = _(span.binaryAnnotations || [])
          .find((s) => s.key === Constants.LOCAL_COMPONENT);
      if (localComponentAnnotation && localComponentAnnotation.endpoint) {
        binaryAnnotations.push({
          ...localComponentAnnotation,
          key: 'Local Address',
          value: formatEndpoint(localComponentAnnotation.endpoint)
        });
      }

      return {
        spanId: span.id,
        parentId: span.parentId || null,
        spanName: span.name,
        serviceNames: getServiceNames(span).join(','),
        serviceName: getServiceName(span) || '',
        duration: span.duration,
        durationStr: mkDurationStr(span.duration),
        left: parseFloat(spanStartTs - traceTimestamp) / parseFloat(duration) * 100,
        width: width < 0.1 ? 0.1 : width,
        depth: (spanDepth + 1) * 5,
        depthClass: (spanDepth - 1) % 6,
        children: (groupByParentId[span.id] || []).map((s) => s.id).join(','),
        annotations: (span.annotations || []).map((a) => ({
          isCore: Constants.CORE_ANNOTATIONS.indexOf(a.value) !== -1,
          left: (a.timestamp - spanStartTs) / span.duration * 100,
          endpoint: a.endpoint ? formatEndpoint(a.endpoint) : null,
          value: ConstantNames[a.value] || a.value,
          timestamp: a.timestamp,
          relativeTime: mkDurationStr(a.timestamp - traceTimestamp),
          width: 8
        })),
        binaryAnnotations,
        errorType
      };
    }
  ).value();

  const spanCount = spans.length;
  const timeMarkers = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
      .map((p, index) => ({index, time: mkDurationStr(duration * p)}));
  const timeMarkersBackup = timeMarkers;
  const spansBackup = spans;

  return {
    traceId,
    duration: mkDurationStr(duration),
    services,
    depth,
    spanCount,
    serviceNameAndSpanCounts,
    timeMarkers,
    timeMarkersBackup,
    spans,
    spansBackup,
    logsUrl
  };
}
