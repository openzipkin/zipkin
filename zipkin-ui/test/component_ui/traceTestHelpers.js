export function endpoint(ipv4, port, serviceName) {
  return {ipv4, port, serviceName};
}

export function annotation(timestamp, value, ep) {
  return {timestamp, value, endpoint: ep};
}

export function span(traceId,
                     name,
                     id,
                     parentId = null,
                     timestamp = null,
                     duration = null,
                     annotations = [],
                     binaryAnnotations = [],
                     debug = false) {
  return {
    traceId,
    name,
    id,
    parentId,
    timestamp,
    duration,
    annotations,
    binaryAnnotations,
    debug
  };
}

export function traceDetailSpan() {
  return {
    openParents: 0,
    openChildren: 0,
    shown: false,
    show() {this.shown = true;}
  };
}

export function spanToShow(id) {
  const shownText = [];
  const showClasses = [];
  return {
    id,
    inFilters: 0,
    shownText,
    expanded: false,
    $expander: {text: t => shownText.push(t)},
    showClasses,
    show() {
      return {addClass: c => showClasses.push(c)};
    }
  };
}
