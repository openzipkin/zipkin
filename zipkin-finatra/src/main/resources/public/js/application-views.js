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

  var OptionView = Backbone.View.extend({
    tagName: "option",
    render: function() {
      var name = this.model.get("name");
      this.$el.html(name).val(name)
      return this;
    }
  });

  /*
   * @param cookie: string
   */
  var CookiedOptionView = OptionView.extend({

    render: function() {
      CookiedOptionView.__super__.render.apply(this);
      var name = this.model.get("name");
      var cookieName = Zipkin.Base.getCookie(this.cookieName);
      if (name === cookieName) {
        this.$el.attr("selected", "selected");
      }
      return this;
    }
  })

  /*
   * @param optionView: subclass of OptionView
   */
  var SelectView = Backbone.View.extend({
    tagName: "select",

    render: function() {
      var that = this;
      this.collection.each(function(item) {
        var view = new that.optionView({model: item});
        that.$el.append(view.render().el);
      });

      var elems = $("#" + this.id);
      if (!(elems.length !== 1 || elems[0] == this.el)) {
        $("#" + this.id).replaceWith(this.el);
      }
      return this;
    }
  });

  
  
  return {
    OptionView: OptionView,
    CookiedOptionView: CookiedOptionView,
    SelectView: SelectView
  };

})();
