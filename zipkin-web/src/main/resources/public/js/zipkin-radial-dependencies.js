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

    var svg = d3.select("#dependencies").append("svg")
        .attr("width", this.diameter)
        .attr("height", this.diameter)
        .append("g")
        .attr("transform", "translate(" + this.radius + "," + this.radius + ")");

    d3.select(self.frameElement).style("height", this.diameter + "px");

    Zipkin.Aggregates.loadJson(function(graph) {

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

      svg.selectAll(".node")
          .data(graph.nodes)
          .enter()
          .append("g")
          .on("mouseover", on_mouse_over)
          .on("mouseout", on_mouse_out)
          .on("click", click_node)
          .attr("class", "radialNode")
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
          .classed("radialLink", true)
          .attr("d", line);
    });

    function on_mouse_over(moused_node) {
      d3.select(this).classed("radialFocused", true);
      svg
        .selectAll("g.radialNode")
        .filter(function (d) {
          return d.name != moused_node.name;
        })
        .classed("radialUnfocused", true);
      svg
        .selectAll("path.radialLink")
        .filter(function (d) {
          return d[0].name == moused_node.name;
        })
        .classed("radialOutbound", true);
      svg
        .selectAll("path.radialLink")
        .filter(function (d) {
          return d[2].name == moused_node.name;
        })
        .classed("radialInbound", true);
    }

    function on_mouse_out(moused_node) {
      d3.select(this).classed("radialFocused", false);
      svg
          .selectAll("g.radialNode")
          .filter(function (d) {
            return d.name != moused_node.name;
          })
          .classed("radialUnfocused", false);
      svg
          .selectAll("path.radialLink")
          .filter(function (d) {
            return d[0].name == moused_node.name;
          })
          .classed("radialFocused", false);
      svg
          .selectAll("path.radialLink")
          .filter(function (d) {
            return d[0].name == moused_node.name;
          })
          .classed("radialOutbound", false);
      svg
          .selectAll("path.radialLink")
          .filter(function (d) {
            return d[2].name == moused_node.name;
          })
          .classed("radialInbound", false);
    }

    function click_node(node) {
      d3.select("svg").remove();
      var block = new Zipkin.BlockDependencies(node.name);
    }
  };

  return RadialDependencies;

}());