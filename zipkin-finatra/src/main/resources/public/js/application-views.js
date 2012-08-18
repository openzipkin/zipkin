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
Zipkin.Application.Views = (function() {

  /* Takes a Service model */
  var ServiceOptionView = Backbone.View.extend({

    tagName: "option",
    className: "service-select",
    events: {

    },

    render: function() {
      var name = this.model.get("name");
      this.$el.html(name).val(name)
      return this;
    }

  });

  var ServiceSelectView = Backbone.View.extend({

    tagName: "select",
    id: "service_name",

    render: function() {
      var that = this;
      this.collection.each(function(item) {
        var view = new ServiceOptionView({model: item});
        that.$el.append(view.render().el);
      });
      return this;
    }
  });

  s = new Zipkin.Application.Models.ServiceList();
  s.on("change", function() {
    v = new ServiceSelectView({collection: s})
  });
  s.fetch();
  
  
})();
