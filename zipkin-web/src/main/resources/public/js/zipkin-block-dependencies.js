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
Zipkin.BlockDependencies = (function () {

  var topMargin = 20; // spacing from the top
  var boxSpacing = 5;
  var boxHeight = 17;
  var selectedHeight = 600; // how tall is the middle box
  var boxWidth = 200;
  var firstColumn = 0;
  var secondColumn = 400;
  var thirdColumn = 800;
  var fourthColumn = 1200; // only used for animation starting point

  var transitionDuration = 750;

  var width = $(".content").width();
  var height = 1600;

  var BlockDependencies = function(start) {

    var self = this;

    var svg = d3.select("#dependencies").append("svg")
        .attr("width", width)
        .attr("height", height);

    var currGeneration = 0; // every time we click, this gets incremented.  Used to track who's in and out of the scene.

    // chunk through the data and build the graph
    Zipkin.Aggregates.loadJson(function (graph) {

      // add a few more defaults
      var id = 0;
      self.nodes = _.map(graph.nodes, function (node) {
        node.x=0;
        node.y=0;
        node.x0=0;
        node.y0=0;
        node.height = boxHeight;
        node.id = id;
        id += 1;
        return node;
      });

      self.selected = _.find(self.nodes, function (node) {
        return node.name == start
      });
      var zoomed = zoom_data(self.selected);
      render(zoomed);
    });

    var diagonal = d3.svg.diagonal()
      .projection(function (d) { return [d.y, d.x]; })
      .source(function (d) { //noinspection JSSuspiciousNameCombination
              return {x: d.source.y, y: d.source.x, name: d.source.name }; })
      .target(function (d) { //noinspection JSSuspiciousNameCombination
              return {x: d.target.y, y: d.target.x, name: d.target.name }; })
      ;

    function drawNode(group) {
      // EXIT
      group.exit()
        .remove()
        ;

      // ENTER
      var svgNode = group.enter()
        .append("g")
        .classed("blockNode", true);

      svgNode.append("svg:rect");
      svgNode.append("svg:text");

      // UPDATE
      group
        .classed("blockChild", function (d) { return d.class == "blockChild"; })
        .classed("blockParent", function (d) { return d.class == "blockParent"; })
        .classed("blockTarget", function (d) { return d.class == "blockTarget"; })
        .on("click", click_node)
        .attr("transform", function (d) { return "translate(" + d.x0 + "," + d.y0 + ")"; })
        .transition()
        .duration(transitionDuration)
        .attr("transform", function (d) { return "translate(" + d.x + "," + d.y + ")"; })
        .call(drawNodeBox)
        ;
    }

    function drawNodeBox(group) {

      group
        .select("rect")
        .attr("y", -boxHeight / 2)
        .attr("rx", 5)
        .attr("ry", 5)
        .attr("height", function (d) { return d.height; })
        .attr("width", boxWidth)
        ;

      group
        .select("text")
        .attr("dy", 4)
        .attr("dx", 7)
        .text(function (d) { return d.name })
        ;
    }

    function drawLink(group) {
      group.exit()
        .remove()
        ;

      group.enter()
        .append("path")
        .attr("class", "blockLink")
        ;

      group
        .attr("d", function (d) {
          return diagonalChord({
            source: {x: d.source.x0, y: d.source.y0},
            target: {x: d.target.x0, y: d.target.y0},
            height: 1,
            offset: 0
          });
        })
        .transition()
        .attr("d", function (d) { return diagonalChord(d); })
        .duration(transitionDuration)
        .style("stroke-opacity", 1)
        ;
    }

    function render(graph) {

      svg.selectAll("g.blockNode")
        .data(self.nodes, function (d) { return d.name; })
        .call(drawNode)
        ;

      // links
      svg.selectAll(".blockLink")
        .data(graph.links, function (d) { return d.id })
        .call(drawLink)
        ;
    }

    function click_node(node) {
      var graph = zoom_data(node);
      render(graph);
    }

    function diagonalChord(d) {
      var d1 = diagonal({
        target: d.source,
        source: {x: d.target.x, y: d.target.y + d.offset}
      });
      var d2 = diagonal({
        target: {x: d.target.x, y: d.target.y + d.offset + d.height},
        source: d.source
      });

      var combined = d1 + " L" + d2.substr(1) + " Z";
      return combined;

    }

    /**
     * Given a list of all nodes and one specific selected node, return
     * a data structure enumerating all nodes connected to our selection,
     * and all links to connect them.
     */
    function zoom_data(selected) {

      currGeneration += 1; // each zoom is a new generation

      var graph = { nodes: [], links: [] };

      var sortedParentLinks = selected.linksTo.sort(function(a,b) {
        return b.moments.m0 - a.moments.m0;
      });
      var parents = [];
      _.each(sortedParentLinks, function(link, index) {
        var node = link.source;

        node.ratio = link.moments.m0 / selected.moments.m0;
        if (node.generation < currGeneration - 1 || node.class != "blockTarget") {
          // nodes not currently visible slide in from off screen
          // depending on which way we're shifting
          node.y0 = topMargin;
          node.x0 = (selected.class == "blockParent") ? -firstColumn : secondColumn;
        }
        else {
          node.x0 = node.x;
          node.y0 = node.y;
        }
        node.y = topMargin + index * (boxHeight + boxSpacing);
        node.x = firstColumn;
        node.height = boxHeight;
        node.class = "blockParent";
        node.generation = currGeneration;

        parents.push(node);
      });

      var sortedChildren = selected.linksFrom.sort(function(a,b) {
        return b.mean - a.mean;
      });

      var children = [];
      _.each(sortedChildren,function (link, index) {
        var node = link.target;
        if (node.generation == currGeneration) {
          // this means we have a dupe
          node = _.clone(link.target);
          node.name = node.name + " (loop)";
        }

        node.ratio = link.moments.mean() / selected.moments.mean();

        if (node.generation < currGeneration - 1 || node.class != "blockTarget") {
          // nodes not currently visible slide in from off screen
          // depending on which way we're shifting
          node.y0 = 0;
          node.x0 = (selected.class == "blockChild") ? fourthColumn : secondColumn;
        }
        else {
          // visible nodes just slide from their old position
          node.x0 = node.x;
          node.y0 = node.y;
        }
        node.y = topMargin + index * (boxSpacing + boxHeight);
        node.x = thirdColumn;
        node.height = boxHeight;
        node.class = "blockChild";
        node.generation = currGeneration;

        children.push(node);
      });

      selected.x0 = selected.x;
      selected.y0 = selected.y;
      selected.class = "blockTarget"
      selected.y = topMargin; // somewhere in the middle
      selected.x = secondColumn;
      selected.height = selectedHeight;
      selected.generation = currGeneration;

      graph.nodes = _.flatten([children, parents, [selected]]);

      var chordPos = 0;
      var childLinks = _.map(children, function (node) {
        var link = {
          id: "childlink" + node.id + selected.id,
          offset: chordPos,
          source: node,
          target: {
            x: selected.x + boxWidth,
            y: selected.y,
            x0: selected.x0 + boxWidth,
            y0: selected.y0,
            id: selected.id
          },
          height: (selected.height - boxHeight) * node.ratio
        };
        chordPos += (selectedHeight - boxHeight) * node.ratio;
        return link;
      });
      chordPos = 0
      var parentLinks = _.map(parents, function (node) {
        var link = {
          id: "parentlink" + node.id + selected.id,
          offset: chordPos,
          source: {
            x: node.x + boxWidth,
            y: node.y,
            x0: node.x0 + boxWidth,
            y0: node.y0
          },
          target: {
            x: selected.x,
            y: selected.y,
            x0: selected.x0,
            y0: selected.y0
          },
          height: (selected.height - boxHeight) * node.ratio
        };
        chordPos += (selectedHeight - boxHeight) * node.ratio;
        return link;
      });

      graph.links = childLinks.concat(parentLinks);

      return graph;
    }
  };

  return BlockDependencies;

}());