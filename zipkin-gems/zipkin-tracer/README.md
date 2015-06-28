# ZipkinTracer

Rack integration middleware for Zipkin tracing.

## Usage

Options can be provided via Rails.config for a Rails 3+ app, or can be passed
as a hash argument to the Rack plugin.

    require 'zipkin-tracer'
    use ZipkinTracer::RackHandler, config # config is optional

where Rails.config.zipkin_tracer or config is a hash that can contain the following keys:

 * `:service_name` (REQUIRED) - the name of the service being traced
 * `:service_port` (REQUIRED) - the port of the service being traced (e.g. 80 or 443)
 * `:scribe_server` (default from scribe gem) - the address of the scribe server where traces are delivered
 * `:scribe_max_buffer` (default: 10) - the number of annotations stored until automatic flush
       (note that annotations are also flushed when the request is complete)
 * `:sample_rate` (default: 0.1) - the ratio of requests to sample, from 0 to 1
 * `:annotate_plugin` - plugin function which recieves Rack env, and
   response status, headers, and body; and can record annotations
 * `:filter_plugin` - plugin function which recieves Rack env and will skip tracing if it returns false
 * `:whitelist_plugin` - plugin function which recieves Rack env and will force sampling if it returns true

## Warning

NOTE that access to the response body (available in the annotate
plugin) may cause problems in the case that a response is being
streamed; in general, this should be avoided (see the Rack
specification for more detail and instructions for properly hijacking
responses).

## Plugins

### annotate_plugin
The annotate plugin expects a function of the form:

    lambda {|env, status, response_headers, response_body| ...}

The annotate plugin is expected to perform annotation based on content
of the Rack environment and the response components. The return value
is ignored.

For example:

    lambda do |env, status, response_headers, response_body|
      # string annotation
      ::Trace.record(::Trace::BinaryAnnotation.new('http.referrer', env['HTTP_REFERRER'], 'STRING', ::Trace.default_endpoint))
      # integer annotation
      ::Trace.record(::Trace::BinaryAnnotation.new('http.content_size', [env['CONTENT_SIZE']].pack('N'), 'I32', ::Trace.default_endpoint))
      ::Trace.record(::Trace::BinaryAnnotation.new('http.status', [status.to_i].pack('n'), 'I16', ::Trace.default_endpoint))
    end

### filter_plugin
The filter plugin expects a function of the form:

    lambda {|env| ...}

The filter plugin allows skipping tracing if the return value is
false.

For example:

    # don't trace /static/ URIs
    lambda {|env| env['PATH_INFO'] ~! /^\/static\//}

### whitelist_plugin
The whitelist plugin expects a function of the form:

    lambda {|env| ...}

The whitelist plugin allows forcing sampling if the return value is
true.

For example:

    # sample if request header specifies known device identifier
    lambda {|env| KNOWN_DEVICES.include?(env['HTTP_X_DEVICE_ID'])}
