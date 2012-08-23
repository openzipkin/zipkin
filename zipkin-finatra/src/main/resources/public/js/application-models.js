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
Zipkin.Application = Zipkin.Application || {};
Zipkin.Application.Models = (function() {
  var Service = Backbone.Model.extend();
  var ServiceList = Backbone.Collection.extend({
    model: Service,
    url: function() {
      return "/api/services";
    }
  });

  var Span = Backbone.Model.extend();

  /*
   * @param serviceName: string
   */
  var SpanList = Backbone.Collection.extend({
    model: Span,

    initialize: function(models, options) {
      this.serviceName = options.serviceName;
    },

    url: function() {
      return "/api/spans?serviceName=" + this.serviceName;
    }
  });

  return {
    Service: Service,
    ServiceList: ServiceList,

    Span: Span,
    SpanList: SpanList
  }
})();