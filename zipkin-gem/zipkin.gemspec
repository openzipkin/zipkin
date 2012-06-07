# -*- encoding: utf-8 -*-
lib = File.expand_path('../lib/', __FILE__)
$:.unshift lib unless $:.include?(lib)

require 'zipkin/version'

Gem::Specification.new do |s|
  s.name                      = "zipkin"
  s.version                   = Zipkin::VERSION
  s.authors                   = ["Franklin Hu"]
  s.email                     = ["franklin@twitter.com"]
  s.homepage                  = "https://github.com/twitter/zipkin"
  s.summary                   = "Zipkin"
  s.description               = "Sends trace data to Zipkin"

  s.required_rubygems_version = ">= 1.3.5"

  s.files                     = Dir.glob("{bin,lib}/**/*")
  s.require_path              = 'lib'
end
