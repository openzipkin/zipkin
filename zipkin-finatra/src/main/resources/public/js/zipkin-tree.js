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
//= require zipkin-node

var Zipkin = Zipkin || {};
Zipkin.Tree = (function() {

  var Tree = function(root) {
    this.root = root;
  };

  Tree.prototype.getRoot = function () { return this.root; };

  Tree.prototype.clone = function() {
    var newRoot
      ;

    var walkTree = function(node) {
      var cloned = node.clone()
        , newChildren = []
        ;

      $.each(node.getChildren(), function(i, child) {
        var newChild = walkTree(child);
        newChildren.push(newChild);
      });

      cloned.setChildren(newChildren);
      return cloned;
    };

    newRoot = walkTree(this.root);
    return new Tree(newRoot);
  };

  /**
   * Applies a function to each node in the tree.
   *
   * `f` is guaranteed to run on the children of a node before it runs on the node.
   **/
  Tree.prototype.map = function(f) {
    var walkTree = function(node) {
      var mapped = $.map(node.getChildren(), function(child) {
        return walkTree(child);
      });
      node.setChildren(mapped);
      return f(node);
    };
    this.root = walkTree(this.root);
    return this;
  };

  /**
   * Applies a function to the children of a node.
   * If `f` returns a list, the node's children list is replaced with that list.
   *
   * `f` is guaranteed to run on the children of a node before it runs on the node.
   **/
  Tree.prototype.reduceChildren = function(f) {

    var walkTree = function(node) {
      var children = node.getChildren();

      $.each(children, function(i, child) {
        walkTree(child);
      });

      if (children.length > 0) {
        var reduced = f(children);
        node.setChildren(reduced);
      }
    };

    walkTree(this.root);
    return this;
  };

  Tree.prototype.toList = function() {
    return this.root.toList();
  };

  return Tree;
})();
