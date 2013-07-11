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
 * Statistical moments.
 * cribbed from algebird:
 * https://github.com/twitter/algebird/blob/develop/algebird-core/src/main/scala/com/twitter/algebird/MomentsGroup.scala
 */
Zipkin.Moments = (function() {

  var Moments = function(m) {
    this.m0 = m.m0;
    this.m1 = m.m1;
    this.m2 = m.m2;
    this.m3 = m.m3;
    this.m4 = m.m4;
  };

  Moments.prototype.count = function() { return this.m0; };
  Moments.prototype.mean = function() { return this.m1; };
  Moments.prototype.variance = function() { return this.m2 / this.m0; };
  Moments.prototype.stddev = function() { return Math.sqrt(this.m2 / this.m0); };

  Moments.prototype.plus = function(b) {
    var a = this;
    var delta = b.m1 - a.m1;
    var countCombined = a.m0 + b.m0
    if(countCombined == 0) {
      return Moments.empty();
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

    return new Moments({"m0":countCombined, "m1":meanCombined, "m2":m2, "m3":m3, "m4":m4});
  }

  Moments.empty = function() { return new Moments({"m0":0, "m1":0, "m2":0, "m3":0, "m4":0}); };

  return Moments;
})();
