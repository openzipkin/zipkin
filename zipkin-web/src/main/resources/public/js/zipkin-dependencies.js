/*
 * Copyright 2013 Twitter Inc.
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
var Zipkin = Zipkin || {};

/**
 * radial graph of global dependencies
 */
Zipkin.RadialDependencies = (function () {

  var Moments = {};
  Moments.empty = { "m0":0, "m1":0, "m2":0, "m3":0, "m4":0 };

  /**
   * cribbed from algebird.  Probably should do this server-side
   */
  Moments.merge = function(a, b) {
    var delta = b.m1 - a.m1;
    var countCombined = a.m0 + b.m0
    if(countCombined == 0) {
      return Moments.empty;
    }

    var meanCombined = (a.m0*a.m1 + b.m0*b.m1) / countCombined

    var m2 = a.m2 + b.m2 +
        Math.pow(delta, 2) * a.m0 * b.m0 / countCombined;

    var m3 = a.m3 + b.m3 +
        Math.pow(delta, 3) * a.m0 * b.m0 * (a.m0 - b.m0) / Math.pow(countCombined, 2) +
        3 * delta * (a.m0 * b.m2 - b.m0 * a.m2) / countCombined;

    var m4 = a.m4 + b.m4 +
        Math.pow(delta, 4) * a.m0 * b.m0 * (Math.pow(a.m0, 2) -
            a.m0 * b.m0 + Math.pow(b.m0, 2)) / Math.pow(countCombined, 3) +
        6 * Math.pow(delta, 2) * (Math.pow(a.m0, 2) * b.m2 +
            Math.pow(b.m0, 2) * a.m2) / Math.pow(countCombined, 2) +
        4 * delta * (a.m0 * b.m3 - b.m0 * a.m3) / countCombined;

    return { "m0":countCombined, "m1":meanCombined, "m2":m2, "m3":m3, "m4":m4 };
  }

  var RadialDependencies = function (startTime, endTime) {
    this.startTime = startTime;
    this.endTime = endTime;

    this.diameter = 1024;
    this.radius = this.diameter / 2;
    this.innerRadius = this.radius - 120;
  };

  RadialDependencies.prototype.render = function () {

    var line = d3.svg.line.radial()
        .interpolate("basis")
        .tension(.85)
        .radius(function (d) {
          return d.y;
        })
        .angle(function (d) {
          return d.x / 180 * Math.PI;
        });

    var svg = d3.select(".dependencies").append("svg")
        .attr("width", this.diameter)
        .attr("height", this.diameter)
        .append("g")
        .attr("transform", "translate(" + this.radius + "," + this.radius + ")");

    d3.select(self.frameElement).style("height", this.diameter + "px");

    // chunk through the data
    d3.json("/api/dependencies", function (json) {
      var graph = process_data(json);
      console.log(graph);

      svg.selectAll(".node")
          .data(graph.nodes)
          .enter()
          .append("g")
          .on("mouseover", on_mouse_over)
          .on("mouseout", on_mouse_out)
          .attr("class", "node")
          .attr("transform", function (d) {
            return "rotate(" + (d.x - 90) + ")translate(" + d.y + ")";
          })
          .append("text")
          .attr("dx", function (d) {
            return d.x < 180 ? 8 : -8;
          })
          .attr("dy", ".31em")
          .attr("text-anchor", function (d) {
            return d.x < 180 ? "start" : "end";
          })
          .attr("transform", function (d) {
            return d.x < 180 ? null : "rotate(180)";
          })
          .text(function (d) {
            return d.name;
          });


      svg.selectAll("path.link")
          .data(graph.links)
          .enter()
          .append("svg:path")
          .classed("link", true)
          .attr("d", line);
    });

    function on_mouse_over(moused_node) {
      d3.select(this).classed("focused", true);
      svg
          .selectAll("path.link")
          .filter(function (d) {
            return d[0].name == moused_node.name;
          })
          .classed("outbound", true);
      svg
          .selectAll("path.link")
          .filter(function (d) {
            return d[2].name == moused_node.name;
          })
          .classed("inbound", true);
    }

    function on_mouse_out(moused_node) {
      d3.select(this).classed("focused", false);
      svg
          .selectAll("path.link")
          .filter(function (d) {
            return d[0].name == moused_node.name;
          })
          .classed("focused", false);
      svg
          .selectAll("path.link")
          .filter(function (d) {
            return d[0].name == moused_node.name;
          })
          .classed("outbound", false);
      svg
          .selectAll("path.link")
          .filter(function (d) {
            return d[2].name == moused_node.name;
          })
          .classed("inbound", false);
    }

    function newNode(name) {
      return {
        name: name,
        inboundMoments: Moments.empty,
        outboundMoments: Moments.empty,
        inboundLinks: 0,
        outboundLinks: 0,
        linksFrom: [],
        linksTo: []
      };
    }

    function process_data(json) {

      var nodeMap = {};
      var graph = {"nodes": [], "links": []};

      // build a map of each node
      _.each(json.links, function (pairing) {
        nodeMap[pairing.parent] = newNode(pairing.parent);
        nodeMap[pairing.child] = newNode(pairing.child);
      });

      // build up each link and calculate node count totals for inbound and outbound
      _.each(json.links, function (pairing) {
        if (pairing.parent != pairing.child) {
          var sourceNode = nodeMap[pairing.parent];
          var targetNode = nodeMap[pairing.child];

          // count the total inbound and outbound calls
          sourceNode.outboundMoments = Moments.merge(sourceNode.outboundMoments, pairing.durationMoments);
          targetNode.inboundMoments = Moments.merge(targetNode.inboundMoments, pairing.durationMoments);

          // and count the links between unique services
          sourceNode.outboundLinks += 1;
          targetNode.inboundLinks += 1;

          var link = {source: sourceNode, target: targetNode, moments: pairing.durationMoments};
          graph.links.push([sourceNode, targetNode]);

          // finally record these links in each node
          sourceNode.linksFrom.push(link);
          targetNode.linksTo.push(link);
        }
      });

      graph.nodes = _.sortBy(_.values(nodeMap), function (node) {
        return node.name
      });

      // fill in values needed for layout
      _.each(graph.nodes, function (node, index) {
        node.x = index / graph.nodes.length * 360
        node.y = 350
      });

      graph.links = _.map(graph.links, function (link, index) {
        var middle = {
          x: (link[0].x + link[1].x) / 2,
          y: link[0].y / 2
        };

        return [link[0], middle, link[1]];
      });

      return graph;
    }
  };

  return RadialDependencies;

}());