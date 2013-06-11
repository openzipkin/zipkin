See LICENSE.txt for terms of use.

Author: Isaac Sukin, @IceCreamYou

This extension allows seeing
[Zipkin](https://github.com/twitter/zipkin) traces in a Firebug panel.
Depends on [Firebug](https://getfirebug.com/).

*To install* the extension, find the "Install Add-on From File..." button in the
"Tools > Add-Ons" dialog and navigate to the XPI file. Afterwards, edit the
extension's options (click the "Options" button under the extension's
description in the Add-Ons dialog) and change the URLs to the ones you want
to work with.

*To use* the extension, go to the site you want to profile, open Firebug, and go
to the Zipkin tab. Any requests that match the "Site to trace" setting will
be listed in that pane along with links to the Zipkin trace visualization and
other useful information. If you would like to clear the traces in the pane,
click the small options arrow next to where it says "Zipkin" in the tab title
and then click "Clear Traces" from the drop-down menu. That drop-down also has
an option to toggle whether tracing is enabled.

*To build* the extension, run `ant` in the extension's directory. This will
generate `zipkin-VERSION.xpi`, where `VERSION` is defined in ant.properties.
If you'd like to dig into the code, all the business logic is in
chrome/content/main.js and chrome/content/zipkinPanel.js. See CONTRIBUTING.md
for details on contributing.

*To update* the extension, find the "Install Add-on From File..." button in the
"Tools > Add-Ons" dialog and navigate to the XPI file. The new version of the
extension will overwrite the existing one. If you are developing the extension
and testing changes to it, you may want to remove the extension before doing
this to make sure you have a clean environment (you don't need to restart
Firefox after clicking the Remove button, but you will probably lose your
settings). Either way, if you have had Firebug open in your current browsing
session, you will need to reload Firebug after upgrading the Zipkin browser
extension by refreshing the page.
