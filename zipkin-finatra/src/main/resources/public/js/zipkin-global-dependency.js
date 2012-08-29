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
var Zipkin = Zipkin || {};

/*
  Dependency graph visualization built from Zipkin.TraceTree
  Some elements borrowed from: http://bl.ocks.org/1153292
 */
Zipkin.GlobalDependencies = (function() {

  /* Constants */
  /** Chart **/
  var WIDTH        = 1800
    , HEIGHT       = 1000
    , BORDER       = 5
    , LEFT_GUTTER  = 100
    , RIGHT_GUTTER = 150
    , templatize = Zipkin.Util.templatize
    , TEMPLATES = Zipkin.Util.TEMPLATES
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
    return $("g[id='node-id-" + name + "']");
  }

  var hoverEvent = function(d) {
    var node = nodeSelector(d.name)
    if (!d.selected && d.sourceLinks.length > 0) {
      node.popover({
        placement: function() {
          if (d.x < LEFT_GUTTER) {
            return "right";
          } else {
            return "top";
          }
        },
        trigger: "manual"
      })
      .popover('show');
    } else {
      node.popover('hide');
    }
    d.selected = !d.selected;
    this.redraw();

  };

  var blurEvent = function(d) {
    d.selected = false;
    this.currentTarget = null;

    nodeSelector(d.name).popover('hide');
    this.redraw();
  };


  var getTableView = function(d) {

    var tableView = {
      "headRow": [
        { "headRowElem": "Service Called" },
        { "headRowElem": "Number of Calls" }],
      "body": []
    }
    var count = 0;
    sorted = d.sourceLinks.sort(function(l1, l2) { return l2.count - l1.count });
    for (var i = 0; i < sorted.length; i++) {
      var targetName = sorted[i].target.name;
      var truncated = targetName.length > 30 ? targetName.substring(0, 27) + "..." : targetName;
      var row = [{"tableRowElem": truncated}, {"tableRowElem": sorted[i].count}];
      tableView.body.push({"tableRow": row});
      count += sorted[i].count;
    }
    var row = [{"tableRowElem": "<strong>Total</strong>"}, {"tableRowElem": count}]
    tableView.body.push({"tableRow": row});
    return tableView;
  };

  var setText = function(node) {
    var data = {node: node};
    templatize(TEMPLATES.SIMPLE_TABLE, function(template) {
      data.node.attr("data-content", function(d) {return template.render(getTableView(d))});
    });
  }

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

    sankey
      .nodes(this.nodes)
      .links(this.links)
      .layout(32);

    var path = sankey.link();

     var link = svg.append("g")
               .selectAll(".link")
               .data(sankey.links())
            .enter().append("path")
               .attr("class", "link")
               .attr("d", path)
               .style("stroke-width", function(d) { return Math.max(1, d.dy); })
               .style("z-index", -1)
               .sort(function(a, b) { return b.dy - a.dy; })

      link.append("title")
          .text(function(d) { return d.source.name + " → " + d.target.name + "\n" + format(d.value); });

      var node = svg.append("g").selectAll(".node")
          .data(this.nodes)
        .enter().append("g")
          .attr("id", function(d) { return "node-id-" + d.name; })
          .attr("class", "node")
          .attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; })
          .on("click", Zipkin.Util.bind(this, hoverEvent))
          .attr("rel", "popover")
          .attr("data-original-title", function(d) { return d.name; })
          .style("z-index", 5)
        .call(d3.behavior.drag()
           .origin(function(d) { return d; })
           .on("dragstart", function() { this.parentNode.appendChild(this); })
           .on("drag", dragmove));

      setText(node);

      node.append("rect")
          .attr("height", function(d) { return d.dy; })
          .attr("width", sankey.nodeWidth())
          .style("fill", function(d) { return d.color = color(d.name.replace(/ .*/, "")); })
          .style("stroke", function(d) { return d3.rgb(d.color).darker(2); })
        .append("title")
          .text(function(d) { return d.name + "\n" + format(d.value); });


      // This is quite janky but for some reason the text won't drag, so we create a rectangle that exactly covers the text
      // and make it invisible so we can drag and click the text
      node.append("rect")
          .attr("x", function(d) {return -6 - 6 *  d.name.length - 5; } )
          .attr("y", function(d) { return d.dy / 2 - 10; })
          .attr("width", function(d) {return 6 * d.name.length + 5; } )
          .attr("height", function(d) { return 19; })
          .style("opacity", 0)
        .filter(function(d) { return d.x < width / 2; })
          .attr("x", 6 + sankey.nodeWidth())

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