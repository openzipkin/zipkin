'use strict';

define(
  [
    'flightjs',
    'd3',
    '../../libs/dagre-d3/js/dagre-d3'
  ],

  function (flight, d3) {
    var dagre = window.dagreD3;
    // window.dagre = dagre;

    return flight.component(dependencyGraph);

    function dependencyGraph() {
      this.after('initialize', function afterInitialize(container, options) {
        this.on(document, 'dependencyDataReceived', function onDependencyDataReceived() {
          // drop the event, keep the links
          var links = Array.prototype.slice.call(arguments, 1);
          var _this = this;
          var rootSvg = container.querySelector('svg');
          rootSvg.textContent = null;
          var svg = d3.select('svg'),
          svgGroup = svg.append('g');

          function arrayUnique(array) {
            return array.filter(function (val, i, arr) {
              return (i <= arr.indexOf(val));
            });
          }

          function flatten(arrayOfArrays) {
            if (arrayOfArrays.length == 0) {
              return [];
            } else {
              return arrayOfArrays.reduce(function (a, b) {
                return a.concat(b);
              });
            }
          }

          function getIncidentEdgeElements(nodeName) {
            var selectedElements = rootSvg.querySelectorAll("[data-from='" + nodeName + "'],[data-to='" + nodeName + "']");
            return Array.prototype.slice.call(selectedElements);
          }

          function getIncidentNodeElements(from, to) {
            return [
              rootSvg.querySelector("[data-node='" + from + "']"),
              rootSvg.querySelector("[data-node='" + to + "']")
            ];
          }

          function getAdjacentNodeElements(centerNode) {
            var edges = g.incidentEdges(centerNode);
            var nodes = flatten(edges.map(function (edge) {
              return g.incidentNodes(edge);
            }));
            var otherNodes = arrayUnique(nodes.filter(function (node) {
              return node != centerNode;
            }));
            var elements = otherNodes.map(function (name) {
              return rootSvg.querySelector("[data-node='" + name + "']");
            });
            return elements;
          }

          function scale(i, startRange, endRange, minResult, maxResult) {
            return minResult + (i - startRange) * (maxResult - minResult) / (endRange - startRange);
          }

          function arrowWidth(callCount) {
            var lg = Math.log(callCount);
            return scale(lg, minLg, maxLg, 0.3, 3);
          }

          // Find min/max number of calls for all dependency links
          // to render different arrow widths depending on number of calls
          var minCallCount = 0;
          var maxCallCount = 0;
          links.filter(function (link) {
            return link.parent != link.child;
          }).forEach(function (link) {
            var numCalls = link.callCount;
            if (minCallCount == 0 || numCalls < minCallCount) {
              minCallCount = numCalls;
            }
            if (numCalls > maxCallCount) {
              maxCallCount = numCalls;
            }
          });
          var minLg = Math.log(minCallCount);
          var maxLg = Math.log(maxCallCount);


          // Get the names of all nodes in the graph
          var parentNames = links.map(function (link) {
            return link.parent;
          });
          var childNames = links.map(function (link) {
            return link.child;
          });
          var allNames = arrayUnique(parentNames.concat(childNames));

          var g = new dagre.Digraph();
          var renderer = new dagre.Renderer();

          // Add nodes/service names to the graph
          allNames.forEach(function (name) {
            g.addNode(name, {label: name});
          });

          // Add edges/dependency links to the graph
          links.filter(function (link) {
            return link.parent != link.child;
          }).forEach(function (link) {
            g.addEdge(link.parent + '->' + link.child, link.parent, link.child, {
              from: link.parent,
              to: link.child,
              callCount: link.callCount
            });
          });

          var layout = dagre.layout()
            .nodeSep(30)
            .rankSep(200)
            .rankDir("LR"); // LR = left-to-right, TB = top-to-bottom.

          // Override drawNodes and drawEdgePaths, so we can add
          // hover functionality on top of Dagre.
          var innerDrawNodes = renderer.drawNodes();
          var innerDrawEdgePaths = renderer.drawEdgePaths();

          renderer.drawNodes(function (g, svg) {
            var svgNodes = innerDrawNodes(g, svg);
            // Add mouse hover/click handlers
            svgNodes.attr('data-node', function (d) {
              return d;
            })
              .each(function (d) {
                var $this = $(this);
                var el = $this[0];
                var rect = el.querySelector('rect');

                $this.click(function () {
                  _this.trigger('showServiceDataModal', {
                    serviceName: d
                  });
                });

                $this.hover(function () {
                  el.classList.add('hover');
                  rootSvg.classList.add('dark');
                  getIncidentEdgeElements(d).forEach(function (el) {
                    el.classList.add('hover-edge');
                  });
                  getAdjacentNodeElements(d).forEach(function (el) {
                    el.classList.add('hover-light');
                  });
                }, function () {
                  el.classList.remove('hover');
                  rootSvg.classList.remove('dark');
                  getIncidentEdgeElements(d).forEach(function (el) {
                    el.classList.remove('hover-edge');
                  });
                  getAdjacentNodeElements(d).forEach(function (el) {
                    el.classList.remove('hover-light');
                  });
                });
              });
            return svgNodes;
          });

          renderer.drawEdgePaths(function (g, svg) {
            var svgNodes = innerDrawEdgePaths(g, svg);
            svgNodes.each(function (edge) {
              // Add mouse hover handlers
              var el = this;
              var $el = $(el);

              var callCount = g.edge(edge).callCount;
              var arrowWidthPx = arrowWidth(callCount) + 'px';
              $el.css('stroke-width', arrowWidthPx);

              $el.hover(function () {
                rootSvg.classList.add('dark');
                var nodes = getIncidentNodeElements(el.getAttribute('data-from'), el.getAttribute('data-to'));
                nodes.forEach(function (el) {
                  el.classList.add('hover');
                });
                el.classList.add('hover-edge');
              }, function () {
                rootSvg.classList.remove('dark');
                var nodes = getIncidentNodeElements(el.getAttribute('data-from'), el.getAttribute('data-to'));
                nodes.forEach(function (el) {
                  el.classList.remove('hover');
                });
                el.classList.remove('hover-edge');
              });
            });

            svgNodes.attr('data-from', function (d) {
              return g.edge(d).from;
            });
            svgNodes.attr('data-to', function (d) {
              return g.edge(d).to;
            });
            return svgNodes;
          });

          renderer
            .layout(layout)
            .run(g, svgGroup);

        });
      });
    }
  }
);
