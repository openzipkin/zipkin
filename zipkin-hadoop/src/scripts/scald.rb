#!/usr/bin/env ruby
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
require 'fileutils'
require 'thread'

JAR_VERSION="0.1.3-SNAPSHOT"

#Usage : scald.rb [--hdfs|--local|--print] job <job args>
# --hdfs: if job ends in ".scala" or ".java" and the file exists, link it against JARFILE (below) and then run it on HOST.
#         else, it is assumed to be a full classname to an item in the JARFILE, which is run on HOST
# --local: run locally according to the rules above
# --print: print the command YOU SHOULD ENTER on the remote node. Useful for screen sessions.

# NOTE: Configuration in script YOU MUST EDIT TO CHANGE SCALA VERSION!
##############################################################
REDUCERS=50

#Get the absolute path of the original (non-symlink) file.
ORIGINAL_FILE=File.symlink?(__FILE__) ? File.readlink(__FILE__) : __FILE__
ZIPKIN_HADOOP_ROOT=File.expand_path(File.dirname(ORIGINAL_FILE)+"/../../")
JARFILE=ZIPKIN_HADOOP_ROOT + "/target/zipkin-hadoop-assembly-#{JAR_VERSION}.jar" #what jar has all the depencies for this job
puts JARFILE
HOST="my.remote.host" #where the job is rsynced to and run
TMPDIR="/tmp"
BUILDDIR=TMPDIR+"/script-build"
LOCALMEM="3g" #how much memory for java to use when running in local mode
#replace COMPILE_CMD="scalac" if you want to run with your systems default scala compiler
SBT_HOME="#{ENV['HOME']}/.sbt"
COMPILE_CMD="java -cp #{SBT_HOME}/boot/scala-2.8.1/lib/scala-library.jar:#{SBT_HOME}/boot/scala-2.8.1/lib/scala-compiler.jar -Dscala.home=#{SBT_HOME}/boot/scala-2.8.1/lib/ scala.tools.nsc.Main"
##############################################################

if ARGV.size < 1
  $stderr.puts("ERROR: insufficient args.")
  #Make sure to print out up to Configuration above:
  system("head -n 19 #{__FILE__} | tail -n+4")
  exit(1)
end

MODE = case ARGV[0]
  when "--hdfs"
    ARGV.shift
  when "--local"
    ARGV.shift
  when "--print"
    ARGV.shift
  else
    #default:
    "--hdfs"
end

JOBFILE=ARGV.shift

# check if running on windows platform
def Kernel.is_windows?
  processor, platform, *rest = RUBY_PLATFORM.split("-")
  platform == 'mswin32' or platform == 'mingw32'
end
IS_WINDOWS=Kernel.is_windows? == true

def file_type
  JOBFILE =~ /\.(scala|java)$/
  $1
end

def is_file?
  !file_type.nil?
end

PACK_RE = /^package ([^;]+)/
JOB_RE = /class\s+([^\s(]+).*extends\s+.*Job/
EXTENSION_RE = /(.*)\.(scala|java)$/

#Get the name of the job from the file.
#the rule is: last class in the file, or the one that matches the filename
def get_job_name(file)
  package = ""
  job = nil
  default = nil
  if file =~ EXTENSION_RE
    default = $1
    File.readlines(file).each { |s|
      if s =~ PACK_RE
        package = $1.chop + "."
      elsif s =~ JOB_RE
        unless job and default and (job.downcase == default.downcase)
          #use either the last class, or the one with the same name as the file
          job = $1
        end
      end
    }
    raise "Could not find job name" unless job
    "#{package}#{job}"
  else
    file
  end
end

JARPATH=File.expand_path(JARFILE)
JARBASE=File.basename(JARFILE)
JOBPATH=File.expand_path(JOBFILE)
JOB=get_job_name(JOBFILE)
JOBJAR=JOB+".jar"
JOBJARPATH=TMPDIR+"/"+JOBJAR

#These are all the threads we need to join before finishing
THREADS = []

#If any of the threads cannot finish their work, we add an error message here:
FAILURES = []
FAILURES_MTX = Mutex.new
def add_failure_msg(msg)
  FAILURES_MTX.synchronize {
    FAILURES << msg
  }
end

#this is used to record the last time we rsynced
def rsync_stat_file(filenm)
  TMPDIR+"/"+"."+filenm.gsub(/\//,'.')+".touch"
end

#In another thread, rsync the file. If it succeeds, touch the rsync_stat_file
def rsync(from, to)
  rtouch = rsync_stat_file(from)
  if !File.exists?(rtouch) || File.stat(rtouch).mtime < File.stat(from).mtime
    $stderr.puts("rsyncing #{to} in background...")
    THREADS << Thread.new(from, to) { |ff,tt|
      if system("rsync -e ssh -z #{ff} #{HOST}:#{tt}")
        #this indicates success and notes the time
        FileUtils.touch(rtouch)
      else
        #indicate failure
        add_failure_msg("Could not rsync: #{ff} to #{HOST}:#{tt}")
        FileUtils.rm_f(rtouch)
      end
    }
  end
end

def is_local?
  (MODE =~ /^--local/) != nil
end

def needs_rebuild?
  !File.exists?(JOBJARPATH) || File.stat(JOBJARPATH).mtime < File.stat(JOBPATH).mtime
end

def build_job_jar
  $stderr.puts("compiling " + JOBFILE)
  FileUtils.mkdir_p(BUILDDIR)
  unless system("#{COMPILE_CMD} -classpath #{JARPATH} -d #{BUILDDIR} #{JOBFILE}")
    FileUtils.rm_f(rsync_stat_file(JOBJARPATH))
    FileUtils.rm_rf(BUILDDIR)
    exit(1)
  end

  FileUtils.rm_f(JOBJARPATH)
  system("jar cf #{JOBJARPATH} -C #{BUILDDIR} .")
  FileUtils.rm_rf(BUILDDIR)
end

def hadoop_command
  "HADOOP_CLASSPATH=/usr/share/java/hadoop-lzo-0.4.14.jar:#{JARBASE}:job-jars/#{JOBJAR} " +
    "hadoop jar #{JARBASE} -libjars job-jars/#{JOBJAR} -Dmapred.reduce.tasks=#{REDUCERS} #{JOB} --hdfs " +
    ARGV.join(" ")
end

def jar_mode_command
  "hadoop jar #{JARBASE} com.twitter.scalding.Tool -Dmapred.reduce.tasks=#{REDUCERS} #{JOB} --hdfs " + ARGV.join(" ")
end

#Always sync the remote JARFILE
rsync(JARPATH, JARBASE) if !is_local?
if is_file?
  build_job_jar if needs_rebuild?

  if !is_local?
    #Make sure the job-jars/ directory exists before rsyncing to it
    system("ssh #{HOST} '[ ! -d job-jars/ ] && mkdir job-jars/'")
    #rsync only acts if the file is out of date
    rsync(JOBJARPATH, "job-jars/" + JOBJAR)
  end
end

SHELL_COMMAND = case MODE
  when "--hdfs"
    puts jar_mode_command
    if is_file?
      "ssh -C #{HOST} #{hadoop_command}"
    else
      "ssh -C #{HOST} #{jar_mode_command}"
    end
  when "--print"
    if is_file?
      "echo #{hadoop_command}"
    else
      "echo #{jar_mode_command}"
    end
  when "--local"
    if is_file?
      if (IS_WINDOWS)
  		  "java -Xmx#{LOCALMEM} -cp #{JARPATH};#{JOBJARPATH} com.twitter.scalding.Tool #{JOB} --local " + ARGV.join(" ")
		  else
			  "java -Xmx#{LOCALMEM} -cp #{JARPATH}:#{JOBJARPATH} com.twitter.scalding.Tool #{JOB} --local " + ARGV.join(" ")
		  end
    else
      "java -Xmx#{LOCALMEM} -cp #{JARPATH} com.twitter.scalding.Tool #{JOB} --local " + ARGV.join(" ")
    end
  else
    raise "Unrecognized mode: " + MODE
  end

#Now block on all the threads:
if THREADS.size > 0
  puts "Waiting for background threads..."
  THREADS.each { |rsyncT| rsyncT.join }
end
#If there are no errors:
if FAILURES.size == 0
  system(SHELL_COMMAND)
else
  FAILURES.each { |msg| $stderr.puts msg }
  exit(1)
end
