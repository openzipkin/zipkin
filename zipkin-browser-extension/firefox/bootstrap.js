/* See LICENSE.txt for terms of use */

// ********************************************************************************************* //
// XPCOM

var Cc = Components.classes, Ci = Components.interfaces, Cu = Components.utils;

// ********************************************************************************************* //
// Helpers

// Extension installation path. Set within startup callback.
var installPath;

// ********************************************************************************************* //
// Firefox Bootstrap API

function install(data, reason) {
}

function uninstall(data, reason) {
}

function startup(data, reason) {
    // Remember so we can use later within the firebugStartup callback.
    installPath = data.installPath;

    // Firebug extension start-up callback. Since extension load order isn't guaranteed
    // the code needs to be ready for two alternatives:
    // 1) Firebug is already loaded: good, let's just execute firebugStartup() callback
    // that will ensure proper Firebug related initialization for this extension.
    // 2) Firebug is not loaded yet - as soon as Firebug is loaded it'll execute this
    // method automatically.
    firebugStartup();
}

function shutdown(data, reason) {
    firebugShutdown();
}

function isFirebugLoaded() {
    try {
        // Import Firebug modules into this scope. It fails if Firebug isn't loaded yet.
        Cu.import("resource://firebug/loader.js");
        Cu.import("resource://firebug/prefLoader.js");

        return true;
    }
    catch (e) {
        // Report the error only if you want to track cases where this extension
        // is loaded before Firebug.
        //Cu.reportError(e);
    }

    return false;
}

// ********************************************************************************************* //
// Firebug Bootstrap API

/**
 * Executed by Firebug framework when Firebug is started. Since the order of Firebug
 * and its bootstrapped extensions is not guaranteed this function is executed twice.
 * 1) When Firebug is loaded
 * 2) When this extension is loaded
 */
function firebugStartup() {
    // If Firebug isn't loaded just bail out; Firebug will execute this method
    // as soon as it loads.
    if (!isFirebugLoaded())
        return;

    // At this point, Firebug is loaded and we can use its API.
    FirebugLoader.registerBootstrapScope(this);

    // Load default preferences. This is probably unnecessary
    PrefLoader.loadDefaultPrefs(installPath, "prefs.js");
}

/**
 * Executed by Firefox when this extension shuts down.
 */
function firebugShutdown() {
    try {
        FirebugLoader.unregisterBootstrapScope(this);
    }
    catch (e) {
        Cu.reportError(e);
    }
}

// * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //

/**
 * Executed by Firebug framework for every browser window. Use this function to append
 * any new elements into the browser window (browser.xul). Don't forget to remove
 * these elements in topWindowUnload.
 *
 * @param {Window} win The browser window
 */
function topWindowLoad(win) {
    // overlay global browser window
}

/**
 * Executed by Firebug framework when this extension
 * @param {Object} win
 */
function topWindowUnload(win) {
    // remove global browser window overlays
}

/**
 * Entire Firebug UI is running inside an iframe (firebugFrame.xul). This function
 * is executed by Firebug framework when the frame is loaded. This happens when
 * the user requires Firebug for the first time (doesn't have to happen during the
 * Firefox session at all)
 *
 * @param {Window} win The Firebug window
 */
function firebugFrameLoad(Firebug) {
    // Register trace listener the customizes trace logs coming from this extension
    // * zipkin; is unique prefix of all messages that should be customized.
    // * DBG_ZIPKIN is a class name with style defined in the specified stylesheet.
    Firebug.registerTracePrefix("zipkin;", "DBG_ZIPKIN", true, "chrome://zipkin/skin/zipkin.css");

    // The registration process will automatically look for 'main' module and load it.
    // The is the same as what happens in a XUL overlay applied on:
    // chrome://firebug/content/firebugOverlay.xul
    var config = {id: "zipkin-extension@twitter.com"};
    Firebug.registerExtension("zipkin", config);
}

function firebugFrameUnload(Firebug) {
    if (!Firebug.isInitialized)
        return;

    Firebug.unregisterExtension("zipkin");
    Firebug.unregisterTracePrefix("zipkin;");
}

// ********************************************************************************************* //
