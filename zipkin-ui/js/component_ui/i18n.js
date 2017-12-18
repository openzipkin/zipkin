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
