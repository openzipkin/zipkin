'use strict';

define(
  [
    'flightjs'
  ],

  function (flight) {

    return flight.component(backToTop);

    function backToTop() {

      this.toTop = function(e) {
        event.preventDefault();
        $('html, body').animate({scrollTop: 0}, 300);
        return false;
      };

      this.after('initialize', function() {
        /*handle window scroll here*/
        $(window).scroll(function() {
          if ($(this).scrollTop() > 200) {
            $('.back-to-top').fadeIn(300);
          } else {
            $('.back-to-top').fadeOut(300);
          }
        });

    	this.on('click', this.toTop);
      });
    }
  }

);

