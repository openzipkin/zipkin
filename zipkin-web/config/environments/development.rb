# Copyright 2012 Twitter Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ZipkinUI::Application.configure do
  # Settings specified here will take precedence over those in config/application.rb

  # In the development environment your application's code is reloaded on
  # every request.  This slows down response time but is perfect for development
  # since you don't have to restart the webserver when you make code changes.
  config.cache_classes = false

  # Log error messages when you accidentally call methods on nil.
  config.whiny_nils = true

  # Show full error reports and disable caching
  config.consider_all_requests_local       = true
  config.action_controller.perform_caching = false

  # Don't care if the mailer can't send
  config.action_mailer.raise_delivery_errors = false

  # Print deprecation notices to the Rails logger
  config.active_support.deprecation = :log

  # Only use best-standards-support built into browsers
  config.action_dispatch.best_standards_support = :builtin

  # Do not compress assets
  config.assets.compress = false

  # Expands the lines which load the assets
  config.assets.debug = true

  # The admin UI communicates over Thrift to the query service

  # Our client could talk to a zookeeper node to find out the location of the service
  # and then make a thrift request to the service itself. We can also hard code the
  # query service hostname.

  # Based on our current network setup for local development
  # agains the remote servers we need need at least two ssh tunnels setup :(

  # See the README file for up to date tunnel information.

  config.zookeeper = {
    :zipkin_query_host => "localhost",
    :zipkin_query_port => 3002,
    #:zk_host => "localhost",
    #:zk_port => 3001,
    :skip_zookeeper => true
  }

  # To run in development you'll need to start the local server
  # Under Rails.root/script there is a file named dev_thrift_server
  # You can run that and use the below config
  # Also, note the :local option in the config below. That is necessary in dev mode
end

