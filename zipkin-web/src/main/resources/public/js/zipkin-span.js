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

Zipkin.Span = (function(superClass) {

  var Span = function(config) {
    Zipkin.Util.bind(this, superClass.prototype.constructor)(config);
    this.traceId       = config.traceId;
    this.startTime     = config.startTime;
    this.endTime       = config.endTime;
    this.duration      = config.duration;
    this.services      = config.services || [];
    this.annotations   = config.annotations || [];
    this.kvAnnotations = config.kvAnnotations || [];
  };

  jQuery.extend(Span.prototype, superClass.prototype);

  Span.prototype.getTraceId       = function() { return this.traceId; };
  Span.prototype.getStartTime     = function() { return this.startTime; };
  Span.prototype.getEndTime       = function() { return this.endTime; };
  Span.prototype.getDuration      = function() { return this.duration; };
  Span.prototype.getServices      = function() { return this.services; };
  Span.prototype.getAnnotations   = function() { return Zipkin.Util.shallowCopy(this.annotations); };
  Span.prototype.getKvAnnotations = function() { return Zipkin.Util.shallowCopy(this.kvAnnotations); };

  Span.prototype.setTraceId       = function(id)   { this.traceId = id; };
  Span.prototype.setStartTime     = function(s)    { this.startTime = s; };
  Span.prototype.setEndTime       = function(e)    { this.endTime = e; };
  Span.prototype.setDuration      = function(d)    { this.duration = d; };
  Span.prototype.setServices      = function(s)    { this.services = s; };
  Span.prototype.setAnnotations   = function(a)    { this.annotations = a; };
  Span.prototype.setKvAnnotations = function(kvAs) { this.kvAnnotations = kvAs; };

  Span.prototype.addAnnotation    = function(a) { this.annotations.push(a); };
  Span.prototype.addKvAnnotation  = function(a) { this.kvAnnotations.push(a); };

  Span.prototype.getServiceName = function() {
    if (this.services.length === 0) {
      return "Unknown";
    }
    this.services.sort();
    return this.services.join(", ");
  };

  Span.prototype.getAll = function() {
    return jQuery.extend(
      Zipkin.Util.bind(this, superClass.prototype.getAll)(),
      {
        traceId       : this.getTraceId(),
        startTime     : this.getStartTime(),
        endTime       : this.getEndTime(),
        duration      : this.getDuration(),
        services      : this.getServices(),
        annotations   : this.getAnnotations(),
        kvAnnotations : this.getKvAnnotations()
      }
    );
  };

  return Span;
})(Zipkin.Node);

Zipkin.fromRawSpan = function(rawSpan) {
  var span = new Zipkin.Span({
    id            : rawSpan.id,
    parentId      : rawSpan.parentId,
    name          : rawSpan.name,
    children      : [],

    traceId       : rawSpan.traceId,
    startTime     : rawSpan.startTime,
    endTime       : rawSpan.startTime + rawSpan.duration,
    duration      : rawSpan.duration,
    services      : rawSpan.services,
    annotations   : [],
    kvAnnotations : [],
  });
  var annotations = $.map(rawSpan.annotations, function(a) {
    var ann = Zipkin.fromRawAnnotation(a);
    ann.setSpan(span);
    return ann;
  });
  var kvAnnotations = $.map(rawSpan.binaryAnnotations, function(b) {
    var ann =  Zipkin.fromRawKvAnnotation(b);
    // convert ca/sa annotations to more readable
    if (ann.key == "ca" || ann.key == "sa") {
      ann.key = (ann.key == "ca") ? "Client Address" : "Server Address";
      ann.setAnnotationType(6); // string
      ann.setValue(ann.host.ipv4 + ":" + ann.host.port);
    }
    ann.setSpan(span);
    return ann;
  });
  span.setAnnotations(annotations);
  span.setKvAnnotations(kvAnnotations);
  return span;
};
