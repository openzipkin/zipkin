import 'jquery-i18n-properties';
import $ from 'jquery';
import {contextRoot} from '../publicPath';

export function i18nInit(file) {
  $.i18n.properties({
    name: file,
    path: contextRoot,
    mode: 'map',
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
