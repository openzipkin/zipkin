See LICENSE.txt for terms of use.

Author: Isaac Sukin, @IceCreamYou

This extension allows seeing
[Zipkin](https://github.com/twitter/zipkin) traces in a Firebug panel.
Depends on [Firebug](https://getfirebug.com/).

**To install** the extension, find the "Install Add-on From File..." button in
the "Tools > Add-Ons" dialog and navigate to the XPI file. Afterwards, edit the
extension's options (click the "Options" button under the extension's
description in the Add-Ons dialog) and change the URLs to the ones you want
to work with.

**To use** the extension, open Firebug and go to the Zipkin tab, then load the
site you want to profile. Any HTTP requests that match the "Site to trace"
setting will be listed in that pane along with a summary of the Zipkin trace, a
link to the full trace, and other useful information. The summary visualization
shows the services that took the longest to process during the request. If you
would like to clear the traces in the pane, click the small options arrow next
to where it says "Zipkin" in the tab title and then click "Clear Traces" from
the drop-down menu. That drop-down also has an option to toggle whether tracing
is enabled and whether to trace the Zipkin web UI if applicable. If you have
traced over 100 requests without clearing them, old requests are expired after
15 minutes.

**To build** the extension, run `ant` in the extension's directory (ant 1.8+ is
required). This will (re)generate `zipkin.xpi`. If you'd like to dig into the
code, please see CONTRIBUTING.md for details on how the code is structured.

**To update** the extension, find the "Install Add-on From File..." button in
the "Tools > Add-Ons" dialog and navigate to the XPI file. The new version of
the extension will overwrite the existing one. If you have had Firebug open in
your current browsing session, you will need to reload Firebug. The easiest way
to do so is to restart Firefox.

![Zipkin browser extension screenshot](https://f.cloud.github.com/assets/203177/651191/7df48586-d46e-11e2-8738-6727b7eda926.png)
