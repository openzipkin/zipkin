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

require File.expand_path('../boot', __FILE__)

# Remove activerecord... for now
#require 'rails/all'

require "action_controller/railtie"
require "action_mailer/railtie"
require "active_resource/railtie"
require "rails/test_unit/railtie"
require 'sprockets/railtie'

# If you have a Gemfile, require the gems listed there, including any gems
# you've limited to :test, :development, or :production.
Bundler.require(:default, Rails.env) if defined?(Bundler)

module Zipkin
  class Application < Rails::Application
    # Settings in config/environments/* take precedence over those specified here.
    # Application configuration should go into files in config/initializers
    # -- all .rb files in that directory are automatically loaded.

    # Custom directories with classes and modules you want to be autoloadable.
    # config.autoload_paths += %W(#{config.root}/extras)

    # Only load the plugins named here, in the order given (default is alphabetical).
    # :all can be used as a placeholder for all plugins not explicitly named.
    # config.plugins = [ :exception_notification, :ssl_requirement, :all ]

    # Activate observers that should always be running.
    # config.active_record.observers = :cacher, :garbage_collector, :forum_observer

    # Set Time.zone default to the specified zone and make Active Record auto-convert to this zone.
    # Run "rake -D time" for a list of tasks for finding time zone names. Default is UTC.
    # config.time_zone = 'Central Time (US & Canada)'

    # The default locale is :en and all translations from config/locales/*.rb,yml are auto loaded.
    # config.i18n.load_path += Dir[Rails.root.join('my', 'locales', '*.{rb,yml}').to_s]
    # config.i18n.default_locale = :de

    # JavaScript files you want as :defaults (application.js is always included).
    # config.action_view.javascript_expansions[:defaults] = %w(jquery rails)

    # Configure the default encoding used in templates for Ruby 1.9.
    config.encoding = "utf-8"

    config.assets.enabled = true
    config.assets.version = '1.0'
    config.assets.prefix = "/assets"

    config.assets.paths << "#{Rails.root}/vendor/assets/images"

    # Configure sensitive parameters which will be filtered from the log file.
    config.filter_parameters += [:password]

    # Configure the TTL for pinning traces
    config.pinned_ttl_sec = 6*30*24*60*60  # 6 months-ish seconds

    # Banner message to display in header
    config.banner_msg = nil

    # ZooKeeper configs to reach the Query daemon (2 options)
    #
    # Option 1: Direct ZooKeeper connection to find out location of the service
    # config.zookeeper = {
    #   :zk_host => "zookeeper_host_name",
    #   :zk_port => "zookeeper_port_number"
    # }
    #
    # Options 2: Tunnel directly to a query daemon and skip ZooKeeper
    # config.zookeeper = {
    #   :zipkin_query_host   => "localhost",
    #   :zipkin_query_port   => 3002,
    #   :skip_zookeeper      => true
    #}

    config.zipkin_tracer = {
        # Scribe settings that can be overwritten
        #:scribe_server => "HOST:PORT",
        #:scribe_max_buff => 10

        # Required settings
        :service_name => "ZipkinUI",
        :service_port => 80,

        :sample_rate => 1
    }

  end
end
