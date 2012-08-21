/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*global d3:false */

//= require d3-2.9.1

var Zipkin = Zipkin || {};

/*
  Dependency graph visualization built from Zipkin.TraceTree
  Some elements borrowed from: http://bl.ocks.org/1153292
 */
Zipkin.GlobalDependencies = (function() {

  /* Constants */
  /** Chart **/
  var WIDTH        = 6000
    , HEIGHT       = 3000
    , BORDER       = 5
    , LEFT_GUTTER  = 100
    , RIGHT_GUTTER = 150
    ;
  /** d3 Forces **/
  var FORCE_CHARGE        = -3000
    , FORCE_LINK_DISTANCE = 60
    , FORCE_GRAVITY       = 0.07
    ;

  var GlobalDependencies = function(data, options) {
    this.width         = options.width       || WIDTH;
    this.height        = options.height      || HEIGHT;
    this.border        = options.border      || BORDER;
    this.leftGutter    = options.leftGutter  || LEFT_GUTTER;
    this.rightGutter   = options.rightGutter || RIGHT_GUTTER;

    this.data          = data;
    this.currentTarget = null;

    this.chart         = this.render();
    this.linkedByIndex = {};

    var that = this;
    $.each(that.data.links, function(index, d) {
        that.linkedByIndex[d.source.name + "," + d.target.name] = d.count;
    });
  };

  var circleSelector = function(name) {
    return $("circle[id='circle-id-" + name + "']");
  };

  var textSelector = function(name) {
    return $("text[id='text-id-" + name + "']");
  };

  var lineSelector = function(sName, tName) {
    return $("line[id='line-id-" + sName + "-" + tName + "']");
  };

  var neighboring = function(a, b) {
    return globalDependencies.linkedByIndex[a + "," + b];
  };

  var hoverRadius = function(name) {
      newRad = Math.min(Math.max(6, 3 * circleSelector(name).attr("r")), 180);
    circleSelector(name).attr("r", function(d) {return newRad; });
  }

  var blurRadius = function(name) {
    var newRad = Math.max(1, circleSelector(name).attr("r") / 3);
    circleSelector(name).attr("r", function(d) {return newRad; });
  }


  var hoverEvent = function(d) {
    d.selected = true;
    this.currentTarget = d;
    hoverRadius(d.name);
//    textSelector(d.name)
//      .attr("data-content", function(e) {
//        return "Calls: " + d.count;
//      })

//      .popover({
//        placement: function() {
//          if (d.x < this.leftGutter) {
//            return "right";
//          } else {
//            return "top";
//          }
//        },
//        trigger: "manual"
//      })
//      .popover('show');

      $.each(globalDependencies.data.links, function(index, link) {
        if (link.source.name == d.name) {
          hoverRadius(link.target.name);
//        circleSelector(link.target.name)
//          .attr("data-content", function(e) {
//            return "Calls from " + d.name + " : " + neighboring(d.name, link.target.name);
//          })
//          .popover({
//            placement: function() {
//              if (d.x < this.leftGutter) {
//                return "right";
//              } else {
//                return "top";
//              }
//            },
//            trigger: "manual"
//          })
//          .popover('show');
        } else if (link.target.name == d.name) {
          hoverRadius(link.source.name);
//          circleSelector(link.source.name)
//            .attr("data-content", function(e) {
//              return "Calls to " + d.name + " : " + neighboring(link.source.name, d.name);
//            })
//            .popover({
//              placement: function() {
//                if (d.x < this.leftGutter) {
//                  return "right";
//                } else {
//                  return "top";
//                }
//              },
//              trigger: "manual"
//            })
//            .popover('show');
          }
      });

    this.redraw();
  };

  var blurEvent = function(d) {
    d.selected = false;
    this.currentTarget = null;

    blurRadius(d.name)
//    circleSelector(d.name)
//        .popover('hide');

    $.each(globalDependencies.data.links, function(index, link) {
      if (link.source.name == d.name) {
            blurRadius(link.target.name);
//        circleSelector(link.target.name).popover('hide');
      } else if (link.target.name == d.name) {
          blurRadius(link.source.name);
//        circleSelector(link.source.name).popover('hide');
      }
    });

    this.redraw();


  };

  GlobalDependencies.prototype.resize = function(width) {
    this.width = width;
    this.chart.attr("width", this.width);
  };

  GlobalDependencies.prototype.redraw = function() {
    var that = this;
    var svg = d3.select("#global-dependency");
  };

  GlobalDependencies.prototype.render = function() {
    var that = this
      , width = this.width
      , height = this.height
      , border = this.border
      ;

    var nodes = {};
    var totalCalls = 0;
    var maxDepth = 0;

    $.each(this.data.links, function(i, l) {
      totalCalls += l.count;

      if (!nodes.hasOwnProperty(l.source)) {
        nodes[l.source] = {name: l.source, depth: 0, count: l.count};
      } else {
        nodes[l.source].count += l.count;
      }
      l.source = nodes[l.source];

      if (nodes.hasOwnProperty(l.target)) {
        nodes[l.target].count += l.count;
        nodes[l.target].depth = Math.max(nodes[l.target].depth, l.depth);
      } else {
        nodes[l.target] = {name: l.target, depth: l.depth, count: l.count};
      }

      l.target = nodes[l.target];
    });

    $.each(nodes, function(i, n) {
      if (n.name === that.data.root) {
        n.fixed = true;
        n.x = 120;
        n.y = height / 2;
      }

      if (n.depth > maxDepth) {
        maxDepth = n.depth;
      }
    });

    var calculateRadius = function(value) {
      return Math.max((value / totalCalls) * 60, 3);
    };

    $.each(nodes, function(i, n) {
      n.r = calculateRadius(n.count);
    });

    var project = function(d) {
      var sx = d.source.x
        , sy = d.source.y
        , tx = d.target.x
        , ty = d.target.y
        ;

      var vecX = tx - sx
        , vecY = ty - sy
        , len = Math.sqrt(Math.pow(vecX, 2) + Math.pow(vecY, 2)) || 1
        , newLen = len - calculateRadius(d.target.count)
        ;

      return {
        x: sx + (vecX * newLen / len),
        y: sy + (vecY * newLen / len)
      };
    };

    var projectX = function(d) { return project(d).x; };
    var projectY = function(d) { return project(d).y; };

    var tick = function(e) {
      circle
            .attr("cx", function(d) {
          d.x = d.depth * ((that.width - (that.leftGutter + that.rightGutter)) / maxDepth) + that.leftGutter;
          return d.x;
        })
        .attr("cy", function(d) { return d.y = Math.max(d.r + border, Math.min(height - d.r - border, d.y)); });

      line
        .attr("id", function(d) {return "line-id-" + d.source.name + "-" + d.target.name})
        .attr("x1", function(d) { return d.source.x; })
        .attr("y1", function(d) { return d.source.y; })
        .attr("x2", function(d) { return projectX(d); })
        .attr("y2", function(d) { return projectY(d); })
        .style("stroke-width", function(d) {return Math.max(globalDependencies.linkedByIndex[d.source.name + "," + d.target.name] / totalCalls * 10, 1); });

      text.attr("transform", function(d) {
        return "translate(" + d.x + "," + d.y + ")";
      });
    };

    var force = d3.layout.force()
      .charge(FORCE_CHARGE)
      .linkDistance(FORCE_LINK_DISTANCE)
      .size([width, height])
      .nodes(d3.values(nodes))
      .links(this.data.links)
      .on("tick", tick)
      .gravity(FORCE_GRAVITY)
      .start();

    var svg = d3.select("#global-dependency").append("svg")
      .attr("width", width)
      .attr("height", height);

    /* Arrow markers on links */
    svg.append("svg:defs").selectAll("marker")
        .data(["directed"])
      .enter().append("svg:marker")
        .attr("id", String)
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 10)
        .attr("refY", 0)
        .attr("markerWidth", 6)
        .attr("markerHeight", 6)
        .attr("orient", "auto")
      .append("svg:path")
        .attr("d", "M 0,-5 L 10,0 L 0,5 z");

    var line = svg.append("svg:g").selectAll("line.link")
        .data(force.links())
      .enter().append("svg:line")
        .attr("class", function(d) { return "link directed"; })
        .style("stroke", "grey");

    var circle = svg.append("svg:g").selectAll("circle")
        .data(force.nodes())
      .enter().append("svg:circle")
        .attr("id", function(d) { return "circle-id-" + d.name; })
        .attr("r", function(d) { return d.r; })
        .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
        .on("mouseout", Zipkin.Util.bind(this, blurEvent))
        .attr("rel", "popover")
        .attr("data-original-title", function(d) { return d.name; })
        .call(force.drag);

    var text = svg.append("svg:g").selectAll("text")
        .data(force.nodes())
      .enter().append("svg:text")
      .attr("id", function(d) {"text-id-" + d.name; });

    /* Service name text shadow for better readability */
    text.append("svg:text")
      .attr("class", "text-label")
      .attr("class", "shadow")
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .attr("x", 8)
      .attr("y", ".31em")
      .text(function(d) { return d.name; });

    /* Service name */
    text.append("svg:text")
      .attr("class", "text-label")
      .attr("id", function(d) {"text-id-" + d.name; })
      .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
      .on("mouseout", Zipkin.Util.bind(this, blurEvent))
      .attr("x", 8)
      .attr("y", ".31em")
      .text(function(d) { return d.name; });

    return svg;
  };

  return GlobalDependencies;
})(Zipkin);