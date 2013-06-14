/*
 * I am skeptical that this is needed.
 */

Firebug.registerTracePrefix("zipkin;", "DBG_ZIPKIN", true, "chrome://zipkin/skin/zipkin.css");

// The registration process will automatically look for 'main' module and load it.
var config = {id: "zipkin-extension@twitter.com"};
Firebug.registerExtension("zipkin", config);
