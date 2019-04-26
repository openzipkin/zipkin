/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {component} from 'flightjs';
import $ from 'jquery';
import {i18nInit} from '../component_ui/i18n';

const NavbarUI = component(function navbar() {
  this.onNavigate = function(ev, {route}) {
    this.$node.find('[data-route]').each((i, el) => {
      const $el = $(el);
      if ($el.data('route') === route) {
        $el.addClass('active');
      } else {
        $el.removeClass('active');
      }
    });
  };

  this.after('initialize', function() {
    i18nInit('nav');
    this.on(document, 'navigate', this.onNavigate);
  });
});

export default NavbarUI;
