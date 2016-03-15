import {component} from 'flightjs';
import d3 from 'd3';
import $ from 'jquery';
import dagreD3 from '../../libs/dagre-d3/js/dagre-d3'; // eslint-disable-line no-unused-vars

const dagre = window.dagreD3;

export default component(function dependencyGraph() {
  this.after('initialize', function afterInitialize(container) {
    this.on(document, 'dependencyDataReceived', function onDependencyDataReceived(ev, ...links) {
      const _this = this;
      const rootSvg = container.querySelector('svg');
      rootSvg.textContent = null;
      const svg = d3.select('svg');
      const svgGroup = svg.append('g');
      const g = new dagre.Digraph();
      const renderer = new dagre.Renderer();

      function arrayUnique(array) {
        return array.filter((val, i, arr) => i <= arr.indexOf(val));
      }

      function flatten(arrayOfArrays) {
        if (arrayOfArrays.length === 0) {
          return [];
        } else {
          return arrayOfArrays.reduce((a, b) => a.concat(b));
        }
      }

      function getIncidentEdgeElements(nodeName) {
        const selectedElements = rootSvg
            .querySelectorAll(`[data-from='${nodeName}'],[data-to='${nodeName}']`);
        return [...selectedElements];
      }

      function getIncidentNodeElements(from, to) {
        return [
          rootSvg.querySelector(`[data-node='${from}']`),
          rootSvg.querySelector(`[data-node='${to}']`)
        ];
      }

      function getAdjacentNodeElements(centerNode) {
        const edges = g.incidentEdges(centerNode);
        const nodes = flatten(edges.map(edge => g.incidentNodes(edge)));
        const otherNodes = arrayUnique(nodes.filter(node => node !== centerNode));
        const elements = otherNodes.map(name => rootSvg.querySelector(`[data-node='${name}']`));
        return elements;
      }

      function scale(i, startRange, endRange, minResult, maxResult) {
        return minResult + (i - startRange) * (maxResult - minResult) / (endRange - startRange);
      }

      // Find min/max number of calls for all dependency links
      // to render different arrow widths depending on number of calls
      let minCallCount = 0;
      let maxCallCount = 0;
      links.filter(link => link.parent !== link.child).forEach(link => {
        const numCalls = link.callCount;
        if (minCallCount === 0 || numCalls < minCallCount) {
          minCallCount = numCalls;
        }
        if (numCalls > maxCallCount) {
          maxCallCount = numCalls;
        }
      });
      const minLg = Math.log(minCallCount);
      const maxLg = Math.log(maxCallCount);

      function arrowWidth(callCount) {
        const lg = Math.log(callCount);
        return scale(lg, minLg, maxLg, 0.3, 3);
      }

      // Get the names of all nodes in the graph
      const parentNames = links.map(link => link.parent);
      const childNames = links.map(link => link.child);
      const allNames = arrayUnique(parentNames.concat(childNames));

      // Add nodes/service names to the graph
      allNames.forEach(name => {
        g.addNode(name, {label: name});
      });

      // Add edges/dependency links to the graph
      links.filter(link => link.parent !== link.child).forEach(({parent, child, callCount}) => {
        g.addEdge(`${parent}->${child}`, parent, child, {
          from: parent,
          to: child,
          callCount
        });
      });

      const layout = dagre.layout()
        .nodeSep(30)
        .rankSep(200)
        .rankDir('LR'); // LR = left-to-right, TB = top-to-bottom.

      // Override drawNodes and drawEdgePaths, so we can add
      // hover functionality on top of Dagre.
      const innerDrawNodes = renderer.drawNodes();
      const innerDrawEdgePaths = renderer.drawEdgePaths();

      renderer.drawNodes((gInner, svgInner) => {
        const svgNodes = innerDrawNodes(gInner, svgInner);
        // Add mouse hover/click handlers
        svgNodes.attr('data-node', d => d)
          .each(function(d) {
            const $this = $(this);
            const nodeEl = $this[0];

            $this.click(() => {
              _this.trigger('showServiceDataModal', {
                serviceName: d
              });
            });

            $this.hover(() => {
              nodeEl.classList.add('hover');
              rootSvg.classList.add('dark');
              getIncidentEdgeElements(d).forEach(el => {
                el.classList.add('hover-edge');
              });
              getAdjacentNodeElements(d).forEach(el => {
                el.classList.add('hover-light');
              });
            }, () => {
              nodeEl.classList.remove('hover');
              rootSvg.classList.remove('dark');
              getIncidentEdgeElements(d).forEach(e => {
                e.classList.remove('hover-edge');
              });
              getAdjacentNodeElements(d).forEach(e => {
                e.classList.remove('hover-light');
              });
            });
          });
        return svgNodes;
      });

      renderer.drawEdgePaths((gInner, svgInner) => {
        const svgNodes = innerDrawEdgePaths(gInner, svgInner);
        svgNodes.each(function(edge) {
          // Add mouse hover handlers
          const edgeEl = this;
          const $el = $(edgeEl);

          const callCount = gInner.edge(edge).callCount;
          const arrowWidthPx = `${arrowWidth(callCount)}px`;
          $el.css('stroke-width', arrowWidthPx);

          $el.hover(() => {
            rootSvg.classList.add('dark');
            const nodes = getIncidentNodeElements(
                edgeEl.getAttribute('data-from'),
                edgeEl.getAttribute('data-to'));
            nodes.forEach(el => { el.classList.add('hover'); });
            edgeEl.classList.add('hover-edge');
          }, () => {
            rootSvg.classList.remove('dark');
            const nodes = getIncidentNodeElements(
                edgeEl.getAttribute('data-from'),
                edgeEl.getAttribute('data-to'));
            nodes.forEach(el => {
              el.classList.remove('hover');
            });
            edgeEl.classList.remove('hover-edge');
          });
        });

        svgNodes.attr('data-from', d => gInner.edge(d).from);
        svgNodes.attr('data-to', d => gInner.edge(d).to);
        return svgNodes;
      });

      renderer
        .layout(layout)
        .run(g, svgGroup);
    });
  });
});
