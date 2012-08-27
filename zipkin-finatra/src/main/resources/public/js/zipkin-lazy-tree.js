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
//= require zipkin-tree

var Zipkin = Zipkin || {};
Zipkin.LazyTree = (function() {

  var LazyTree = function(id) {
    this.id = id;
    this.rootId = null;

    /** Maps a `Node`'s id to a Node object */
    this.nodes = {};

    /** A list of parents */
    this.parents = [];
  };

  LazyTree.prototype.addNode = function(node) {
    var nodeId = node.getId()
      , parentId = node.getParentId()
      ;
    this.nodes[nodeId] = node;
    this.parents.push(parentId);
    if (!parentId) {
      this.rootId = nodeId;
    } else {
      //Zipkin.Util.defaultDictPush(this.nodeToChildren, parentId, nodeId);
    }
  };

  LazyTree.prototype.resolveRoot = function() {
    if (this.rootId === null) {
      var ids = [];
      $.each(this.nodes, function(i, s) {
        ids.push(s.id);
      });

      var parents = $.unique(this.parents);
      ids = $.unique(ids);

      var root = parents.filter(function(id) { return ids.indexOf(id) === -1; });
      if (root.length > 1) {
        console.info("More than one nonexistant span, taking first");
      }
      this.rootId = root[0];
    }

    if (this.nodes.hasOwnProperty(this.rootId)) {
      this.root = this.nodes[this.rootId];
    } else {
      console.error("No root, creating fake")
      var rootId = this.rootId;
      var startTime = Number.MAX_VALUE
        , endTime = Number.MIN_VALUE
        ;

      $.each(this.nodes, function(nodeId, node) {
        startTime = Math.min(startTime, node.getStartTime());
        endTime = Math.max(endTime, node.getEndTime());
      });

      this.root = new Zipkin.Span(
        {
          id: rootId,
          parentId: null,
          name: "Unknown",
          startTime: startTime,
          endTime: endTime,
          duration: endTime - startTime,
          services: ["Unknown"]
        });
      this.nodes[rootId] = this.root;
    }
  };

  LazyTree.prototype.build = function() {
    var that = this;

    this.resolveRoot();

    $.each(this.nodes, function(nodeId, node) {
      var parent = that.nodes[node.getParentId()];
      if (parent) {
        parent.addChild(node);
        node.setParent(parent);
      } else {
        console.error("Parent not found: " + node.getParentId());
      }
    });

    var walkTree = function(node, depth) {
      node.setDepth(depth);
      $.each(node.getChildren(), function(i, child) {
        walkTree(child, depth + 1);
      });
    };
    walkTree(that.root, 0);
    return new Zipkin.Tree(that.root);
  };

  return LazyTree;
})();
