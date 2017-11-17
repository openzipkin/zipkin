import {component} from 'flightjs';
import $ from 'jquery';
import {i18n_init} from '../component_ui/i18n';

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
    i18n_init('nav');
    this.on(document, 'navigate', this.onNavigate);
  });
});

export default NavbarUI;
