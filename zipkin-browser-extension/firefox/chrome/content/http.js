define([
        "zipkin/log"
    ],
    function(log) {
        const XMLHttpRequest = Components.Constructor("@mozilla.org/xmlextras/xmlhttprequest;1");

        /**
         * Send a GET request to a URL.
         *
         * The callback parameter receives the text response and HTTP Status
         * number. It is executed with the request context (i.e. this == req).
         */
        function fetch(url, callback, timeout) {
            if (typeof timeout == 'undefined') {
                timeout = 5000;
            }
            try {
                var req = new XMLHttpRequest();
                req.onload = function() {
                    callback.call(req, req.responseText, req.status);
                };
                req.open("GET", url, true);
                // Headers must be set after open()ing the request.
                req.setRequestHeader('X-Zipkin-Extension', '1');
                req.timeout = timeout;
                req.send();
                req.URL = url; // Custom property
                return req;
            }
            catch(e) {
                log("Sending an HTTP request failed with error: " + e);
            }
        }

        return {
            request: fetch
        };
    }
);

