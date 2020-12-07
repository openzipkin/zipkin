/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import { getServiceName } from './span-row';

// In javascript, dict keys can't be objects
function keyString(parent, child) {
  return `${parent}░${child}`; // cassandra storage uses the same delimiter
}

function link(callCounts, errorCounts) {
  const result = [];
  Object.keys(callCounts).forEach((key) => {
    const parentChild = key.split('░');
    const nextLink = {
      parent: parentChild[0],
      child: parentChild[1],
      callCount: callCounts[key],
    };

    const errorCount = errorCounts[key] || 0;
    if (errorCount) nextLink.errorCount = errorCount;
    result.push(nextLink);
  });
  return result;
}

/*
 * A dependency link is an edge between two services. This parses a span tree into dependency links
 * used by Web UI. Ex. http://zipkin/dependency
 *
 * This implementation traverses the tree, and creates links for RPC and messaging spans. RPC links
 * are only between SERVER spans. One exception is at the bottom of the trace tree. CLIENT spans
 * that record their remoteEndpoint are included, as this accounts for uninstrumented services.
 * Spans with kind unset, but remoteEndpoint set are treated the same as client spans.
 */
export class DependencyLinker {
  constructor(params) {
    const { debug = false } = params;
    this._debug = debug;
    this._callCounts = {}; // parent_child -> callCount
    this._errorCounts = {}; // parent_child -> errorCount
  }

  _firstRemoteAncestor(node) {
    let ancestor = node.parent;
    while (ancestor) {
      const maybeRemote = ancestor.span;
      if (maybeRemote && maybeRemote.kind) {
        if (this._debug)
          console.log(`found remote ancestor ${JSON.stringify(maybeRemote)}`);
        return maybeRemote;
      }
      ancestor = ancestor.parent;
    }
    return undefined;
  }

  _addLink(parent, child, isError) {
    if (this.debug) {
      console.log(
        `incrementing ${isError ? 'error ' : ''}link ${parent} -> ${child}`,
      ); // eslint-disable-line prefer-template
    }
    const key = keyString(parent, child);
    this._callCounts[key] = (this._callCounts[key] || 0) + 1;
    if (!isError) return;
    this._errorCounts[key] = (this._errorCounts[key] || 0) + 1;
  }

  /** The input should be a root span node, not a list of spans. */
  putTrace(traceTree) {
    const debug = this._debug;
    if (debug) console.log('traversing trace tree, breadth-first');
    const queue = traceTree.queueRootMostSpans();
    while (queue.length > 0) {
      const current = queue.shift();
      current.children.forEach((n) => queue.push(n));

      const currentSpan = current.span;
      if (debug) console.log(`processing ${JSON.stringify(currentSpan)}`);

      let { kind } = currentSpan;
      // When processing links to a client span, we prefer the server's name. If we have no child
      // spans, we proceed to use the name the client chose.
      if (kind === 'CLIENT' && current.children.length > 0) {
        continue;
      }

      const serviceName = getServiceName(currentSpan.localEndpoint);
      const remoteServiceName = getServiceName(currentSpan.remoteEndpoint);
      if (!kind) {
        // Treat unknown type of span as a client span if we know both sides
        if (serviceName && remoteServiceName) {
          kind = 'CLIENT';
        } else {
          if (debug) console.log('non remote span; skipping');
          continue;
        }
      }

      let child;
      let parent;
      switch (kind) {
        case 'SERVER':
        case 'CONSUMER':
          child = serviceName;
          parent = remoteServiceName;
          if (current === traceTree) {
            // we are the root-most span.
            if (!parent) {
              if (debug)
                console.log('The client of the root span is unknown; skipping');
              continue;
            }
          }
          break;
        case 'CLIENT':
        case 'PRODUCER':
          parent = serviceName;
          child = remoteServiceName;
          break;
        default:
          if (debug) console.log('unknown kind; skipping');
          continue;
      }

      let isError = currentSpan.tags.error !== undefined;
      if (kind === 'PRODUCER' || kind === 'CONSUMER') {
        if (!parent || !child) {
          if (debug)
            console.log('cannot link messaging span to its broker; skipping');
        } else {
          this._addLink(parent, child, isError);
        }
        continue;
      }

      // Local spans may be between the current node and its remote parent
      const remoteAncestor = this._firstRemoteAncestor(current);
      let remoteAncestorName;
      if (remoteAncestor)
        remoteAncestorName = getServiceName(remoteAncestor.localEndpoint);
      if (remoteAncestor && remoteAncestorName) {
        // Some users accidentally put the remote service name on client annotations.
        // Check for this and backfill a link from the nearest remote to that service as necessary.
        if (
          kind === 'CLIENT' &&
          serviceName &&
          remoteAncestorName !== serviceName
        ) {
          if (debug) console.log('detected missing link to client span');
          this._addLink(remoteAncestorName, serviceName, false); // we don't know if it is an error
        }

        if (kind === 'SERVER' || !parent) parent = remoteAncestorName;

        // When an RPC is split between spans, we skip the child (server side). If our parent is a
        // client, we need to check it for errors.
        if (
          !isError &&
          remoteAncestor.kind === 'CLIENT' &&
          currentSpan.parentId &&
          currentSpan.parentId === remoteAncestor.id
        ) {
          isError = isError || remoteAncestor.tags.error !== undefined;
        }
      }

      if (!parent || !child) {
        if (debug) console.log('cannot find remote ancestor; skipping');
        continue;
      }

      this._addLink(parent, child, isError);
    }
  }

  link() {
    return link(this._callCounts, this._errorCounts);
  }
}
