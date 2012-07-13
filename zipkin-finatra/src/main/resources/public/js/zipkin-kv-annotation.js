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
 Zipkin.KvAnnotation = (function() {

  var KvAnnotation = function(config) {
    this.key            = config.key;
    this.value          = config.value;
    this.annotationType = config.annotationType;
    this.span           = config.span;
  };

  KvAnnotation.prototype.getKey            = function() { return this.key; };
  KvAnnotation.prototype.getValue          = function() { return this.value; };
  KvAnnotation.prototype.getAnnotationType = function() { return this.annotationType; };
  KvAnnotation.prototype.getSpan           = function() { return this.span; };

  KvAnnotation.prototype.setKey            = function(k) { this.key = k; };
  KvAnnotation.prototype.setValue          = function(v) { this.value = v; };
  KvAnnotation.prototype.setAnnotationType = function(t) { this.annotationType = t; };
  KvAnnotation.prototype.setSpan           = function(s) { this.span = s; };

  return KvAnnotation;
 })();

 Zipkin.fromRawKvAnnotation = function(rawKvAnnotation) {
  return new Zipkin.KvAnnotation({
    key: rawKvAnnotation.key,
    value: rawKvAnnotation.value,
    annotationType: rawKvAnnotation.annotationType
  });
 };
