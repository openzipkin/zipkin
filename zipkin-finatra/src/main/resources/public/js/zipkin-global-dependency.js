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
  var WIDTH        = 1300
    , HEIGHT       = 2000
    , BORDER       = 5
    , LEFT_GUTTER  = 100
    , RIGHT_GUTTER = 150
    ;

  var GlobalDependencies = function(nodes, links, options) {
    this.width         = options.width       || WIDTH;
    this.height        = options.height      || HEIGHT;
    this.border        = options.border      || BORDER;
    this.leftGutter    = options.leftGutter  || LEFT_GUTTER;
    this.rightGutter   = options.rightGutter || RIGHT_GUTTER;

    this.nodes = nodes;
    this.links = links;

    this.chart         = this.render();
  };

  var nodeSelector = function(name) {
    return $("node[id='node-id-" + name + "']");
  };

  var hoverEvent = function(d) {
    d.selected = true;
    this.currentTarget = d;

    nodeSelector(d.name)
      .popover({
        placement: function() {
          if (d.x < this.leftGutter) {
            return "right";
          } else {
            return "top";
          }
        },
        trigger: "manual"
      })
      .popover('show');

    this.redraw();
  };

  var blurEvent = function(d) {
    d.selected = false;
    this.currentTarget = null;

    node(d.name).popover('hide');
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

    var formatNumber = d3.format(",.0f"),
        format = function(d) { return formatNumber(d) + " TWh"; },
        color = d3.scale.category20();

    var svg = d3.select("#global-dependency").append("svg")
        .attr("width", width)
        .attr("height", height)
      .append("g");

    var sankey = d3.sankey()
        .width(width)
        .nodeWidth(15)
        .nodePadding(10)
        .size([width, height]);

    var path = sankey.link();

    sankey
      .nodes(this.nodes)
      .links(this.links)
      .layout(32);


     var link = svg.append("g").selectAll(".link")
          .data(this.links)
        .enter().append("path")
          .attr("class", "link")
          .attr("d", path)
          .style("stroke-width", function(d) { return Math.max(1, d.dy); })
          .sort(function(a, b) { return b.dy - a.dy; });

      link.append("title")
          .text(function(d) { return d.source.name + " → " + d.target.name + "\n" + format(d.value); });

      var node = svg.append("g").selectAll(".node")
          .data(this.nodes)
        .enter().append("g")
          .attr("id", function(d) { return "node-id-" + d.name; })
          .attr("class", "node")
          .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; })
          .on("mouseover", Zipkin.Util.bind(this, hoverEvent))
          .on("mouseout", Zipkin.Util.bind(this, blurEvent))
          .attr("rel", "popover")
        .call(d3.behavior.drag()
          .origin(function(d) { return d; })
          .on("dragstart", function() { this.parentNode.appendChild(this); })
          .on("drag", dragmove));

      node.append("rect")
          .attr("height", function(d) { return d.dy; })
          .attr("width", sankey.nodeWidth())
          .style("fill", function(d) { return d.color = color(d.name.replace(/ .*/, "")); })
          .style("stroke", function(d) { return d3.rgb(d.color).darker(2); })
        .append("title")
          .text(function(d) { return d.name + "\n" + format(d.value); });

      node.append("text")
          .attr("x", -6)
          .attr("y", function(d) { return d.dy / 2; })
          .attr("dy", ".35em")
          .attr("text-anchor", "end")
          .attr("transform", null)
          .text(function(d) { return d.name; })
        .filter(function(d) { return d.x < width / 2; })
          .attr("x", 6 + sankey.nodeWidth())
          .attr("text-anchor", "start");

      function dragmove(d) {
        d3.select(this).attr("transform", "translate(" + d.x + "," + (d.y = Math.max(0, Math.min(height - d.dy, d3.event.y))) + ")");
        sankey.relayout();
        link.attr("d", path);
      }
    return svg;
  };

  return GlobalDependencies;
})(Zipkin);