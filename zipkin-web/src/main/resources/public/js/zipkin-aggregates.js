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

var Zipkin = Zipkin || {};

/**
 * Code for loading aggregate dependencies from zipkin
 */
Zipkin.Aggregates = {};


/**
 * Base class for nodes
 */
Zipkin.Aggregates.NodeData = function (name) {
  this.name = name;
  this.moments = Zipkin.Moments.empty();
  this.inboundLinks = 0;
  this.outboundLinks = 0;
  this.linksFrom = [];
  this.linksTo = [];
}

/**
 * Load json from the aggregates api, massage it for d3, and then call the given callback
 * @param callback
 */
Zipkin.Aggregates.loadJson = function (callback) {

  var nodeMap = {};
  var graph = {};

  /**
   * process a link between two services and update each service's aggregate statistics
   * return the link structure back
   */
  function processLink(pairing) {
    var sourceNode = nodeMap[pairing.parent];
    var targetNode = nodeMap[pairing.child];

    // count the total inbound and outbound calls
    var moments = targetNode.moments.plus(new Zipkin.Moments(pairing.durationMoments));
    targetNode.moments = moments;

    // and count the links between unique services
    sourceNode.outboundLinks += 1;
    targetNode.inboundLinks += 1;

    var link = {source: sourceNode, target: targetNode, moments: new Zipkin.Moments(pairing.durationMoments)};
    graph.links.push([link.source, link.target]);

    // finally record these links in each node
    sourceNode.linksFrom.push(link);
    targetNode.linksTo.push(link);
  }

  function processJson(json) {
    graph = {"nodes": [], "links": []};

    // build a map of each node
    _.each(json.links, function (pairing) {
      nodeMap[pairing.parent] = new Zipkin.Aggregates.NodeData(pairing.parent);
      nodeMap[pairing.child] = new Zipkin.Aggregates.NodeData(pairing.child);
    });

    // build up each link and calculate node count totals for inbound and outbound
    _.each(json.links, function (pairing) {
      if (pairing.parent != pairing.child) {
        processLink(pairing);
      }
    });

    graph.nodes = _.sortBy(_.values(nodeMap), function (node) {
      return node.name
    });

    callback(graph);
  }

  // chunk through the data
  d3.json("/api/dependencies", processJson);
};
