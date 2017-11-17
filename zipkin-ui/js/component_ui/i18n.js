import 'jquery-i18n-properties';

export function i18n_init(name) {
  jQuery.i18n.properties({
    name:name, 
    path:'',
    mode:'map',
    callback: function() {
      $('[data-i18n]').each(function(index, item) {
        if(item.tagName != 'INPUT') {
          $(item).html(jQuery.i18n.prop($(item).attr('data-i18n')));
        }else {
          $(item).attr('placeholder', jQuery.i18n.prop($(item).attr('data-i18n')));
        }
      });
    }
  });
}