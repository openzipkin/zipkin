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
import 'jquery-i18n-properties';
import $ from 'jquery';
import {contextRoot} from '../publicPath';

export function i18nInit(file) {
  // https://github.com/jquery-i18n-properties/jquery-i18n-properties
  $.i18n.properties({
    name: file,
    path: contextRoot,
    mode: 'map',
    // do not append a unix timestamp when requesting the language (.properties) files
    // this allows them to be cached by the browser
    cache: true,
    // do not perform blocking XHR requests, as it blocks the entire browser, including
    // rendering
    async: true,
    callback: () => {
      $('[data-i18n]').each((index, item) => {
        if (item.tagName === 'INPUT' || item.tagName === 'SELECT') {
          $(item).attr('placeholder', $.i18n.prop($(item).attr('data-i18n')));
        } else {
          $(item).html($.i18n.prop($(item).attr('data-i18n')));
        }
      });
    }
  });
}
