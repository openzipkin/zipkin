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

var Zipkin = Zipkin || {};

Zipkin.Node = (function() {

  var Node = function(config) {
    this.id       = config.id;
    this.parentId = config.parentId;
    this.parent   = config.parent;
    this.name     = config.name;
    this.children = config.children || [];
    this.depth    = config.depth;
  };

  Node.prototype.getId       = function() { return this.id; };
  Node.prototype.getParentId = function() { return this.parentId; };
  Node.prototype.getParent   = function() { return this.parent; };
  Node.prototype.getName     = function() { return this.name; };
  Node.prototype.getChildren = function() { return Zipkin.Util.shallowCopy(this.children); };
  Node.prototype.getDepth    = function() { return this.depth; };

  Node.prototype.setId       = function(id) { this.id = id; };
  Node.prototype.setParentId = function(p) { this.parentId = p; };
  Node.prototype.setParent   = function(p) { this.parent = p; };
  Node.prototype.setName     = function(n) { this.name = n; };
  Node.prototype.setChildren = function(c) { this.children = c; };
  Node.prototype.setDepth    = function(d) { this.depth = d; };

  Node.prototype.addChild    = function(c) { this.children.push(c); };
  Node.prototype.addChildren = function(c) { this.children = this.children.concat(c); };

  Node.prototype.hasParent   = function() { return this.parentId !== undefined; };

  Node.prototype.getAll = function() {
    return {
      id       : this.getId(),
      parentId : this.getParentId(),
      parent   : this.getParent(),
      name     : this.getName(),
      children : this.getChildren(),
      depth    : this.getDepth()
    };
  };

  Node.prototype.toList = function() {
    var output = [this];

    $.each(this.getChildren(), function(i, child) {
      output = output.concat(child.toList());
    });

    return output;
  };

  Node.prototype.clone = function() {
    var config = Zipkin.Util.bind(this, this.getAll)();
    return new this.constructor(config);
  };

  return Node;
})();
