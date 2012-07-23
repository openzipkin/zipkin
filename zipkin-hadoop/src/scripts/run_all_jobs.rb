#!/usr/bash/env ruby
# Runs all scalding jobs in package com.twitter.zipkin.hadoop

require 'optparse'
require 'ostruct'
require 'pp'
require 'date'

class OptparseAllJobArguments

  #
  # Return a structure describing the options.
  #
  def self.parse(args)
    # The options specified on the command line will be collected in *options*.
    # We set default values here.
    options = OpenStruct.new
    options.uses_hadoop_config = false
    options.dates = []
    options.output = ""

    opts = OptionParser.new do |opts|
      opts.banner = "Usage: run_job.rb -d DATE -o OUTPUT -c CONFIG"

      opts.separator ""
      opts.separator "Specific options:"

     opts.on("-d", "--date STARTDATE,ENDDATE", Array, "The DATES to run the jobs over.  Expected format for dates are is %Y-%m-%dT%H:%M") do |list|
        options.dates = list
      end

      opts.on("-o", "--output OUTPUT",
              "The OUTPUT file to write to") do |output|
        options.output = output
      end
      
      opts.on("-c", "--config [CONFIG]", "Optional hadoop configurations for the job.") do |config|
        options.uses_hadoop_config = true
        options.hadoop_config = config || ''
      end

      opts.separator ""
      opts.separator "Common options:"      
      opts.on_tail("-h", "--help", "Show this message") do
        puts opts
        exit
      end
    end
    opts.parse!(args)
    options
  end
end

options = OptparseAllJobArguments.parse(ARGV)

date_cmd = options.dates.join(" ")
end_date_cmd = options.dates.length > 1 ? options.dates.at(1) : options.dates.at(0)
config_string = options.uses_hadoop_config ? " --config " + options.hadoop_config : ""
run_job_cmd = "ruby " + File.dirname(__FILE__) + "/run_job.rb" + config_string

puts run_job_cmd
puts date_cmd

def run_jobs_in_parallel(jobs)
  threads = []
  for job in jobs 
    threads << Thread.new(job) { |job| system(job) }
  end
  threads.each { |aThread| aThread.join }
end

#Run the commands
system(run_job_cmd + " -j Preprocessed -p -d " + date_cmd)

jobs_set_1 = Array[ run_job_cmd + " -j WorstRuntimes -o " + options.output + "/WorstRuntimes -d " + end_date_cmd,
                 run_job_cmd + " -j MemcacheRequest -o " + options.output + "/MemcacheRequest -d " + end_date_cmd,
                 run_job_cmd + " -j FindNames -p -d " + end_date_cmd ]


jobs_set_2 = Array[ run_job_cmd + " -j PopularKeys -o " + options.output + "/PopularKeys -d " + end_date_cmd,
                 run_job_cmd + " -j PopularAnnotations -o " + options.output + "/PopularAnnotations -d " + end_date_cmd,
                 run_job_cmd + " -j WhaleReport -o " + options.output + "/WhaleReport -d " + end_date_cmd,
                 run_job_cmd + " -j FindIDtoName -p -d " + end_date_cmd ]



jobs_set_3 = Array[ run_job_cmd + " -j DependencyTree -o " + options.output + "/DependencyTree -d " + end_date_cmd,
                 run_job_cmd + " -j Timeouts -s \" --error_type finagle.timeout \" -o " + options.output + "/Timeouts -d " + end_date_cmd,
                 run_job_cmd + " -j Timeouts -s \" --error_type finagle.retry \" -o " + options.output + "/Retries -d " + end_date_cmd ]

run_jobs_in_parallel(jobs_set_1)
run_jobs_in_parallel(jobs_set_2)
run_jobs_in_parallel(jobs_set_3)

puts "All jobs finished!"

