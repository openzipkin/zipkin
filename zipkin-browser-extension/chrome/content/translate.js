define([
        "firebug/lib/locale"
    ],
    function(Locale) {
        return function(n) {
            return Locale.$STR("zipkin.panel." + n);
        };
    }
);
