#!/usr/bin/env ruby

# Command line tool to squash merge pull requests
#
# Uses Ruby 1.8.7

require "rubygems"
require "highline/import"

require "json"
require "net/http"
require "net/https"
require "shellwords"

REPO = "twitter/zipkin"

USAGE = """
Usage: git-pull-request.rb <command> <args>

Commands:
  clean        - Clears saved Github API token
  close  <id>  - Closes a particular pull request or issue
  issues       - Lists open issues
  merge  <id>  - Squash-merge a pull request
  preview <id> - Preview the commit message for a pull request
  pulls        - Lists open pull requests
"""

module Git extend self

  def run(command, tolerate_failure = false)
    puts "Executing: 'git #{command}'"
    output = `git #{command}`
    raise "Unsuccessful git command" if !tolerate_failure && !$?.success?
    output
  end

  # Need to special case this since backtick execution `command`
  # does not work for editors (vi, etc)
  def commit(args = "")
    system("git commit #{args}")
    raise "Unsuccessful git commit" unless $?.success?
  end

end

module Github extend self

  BASE_URI = "https://api.github.com/"

  def get(method, options = {})
    dispatch(Net::HTTP::Get, method, options)
  end

  def post(method, options = {})
    dispatch(Net::HTTP::Post, method, options)
  end

  def patch(method, options = {})
    dispatch(Patch, method, options)
  end

  def clean
    unset_github_token
  end

  private

  # Ruby 1.8.7 Net::HTTP lib doesn't have a Patch method
  class Patch < Net::HTTPRequest
    METHOD = 'PATCH'
    REQUEST_HAS_BODY = true
    RESPONSE_HAS_BODY = true
  end

  def dispatch(action, method, options = {})
    path = BASE_URI + method

    basic_auth = (options.has_key? :username) && (options.has_key? :password)
    if !basic_auth
      token = get_token
      path += "?access_token=#{token}"
    end
    uri = URI.parse(path)

    req = action.new(uri.to_s)
    req.basic_auth options[:username], options[:password] if basic_auth
    req.body = options[:body].to_json if options[:body]

    JSON.parse(send_request(uri, req).body)
  end

  def send_request(uri, request)
    http = Net::HTTP.new(uri.host, uri.port)
    http.use_ssl = true
    http.start do |http|
      return http.request(request)
    end
  end

  # Gets the Github authentication token. If it doesn't exist in git config,
  # force the user to authenticate
  def get_token
    token = get_github_token(true)
    if !token || token.empty?
      puts "=No Github API token found, please authenticate"
      username = ask("==Github username: ")
      password = ask("==Github password: ") { |q| q.echo = false }

      resp = self.post(
        "authorizations",
        :username => username,
        :password => password,
        :body => { :scopes => "repo", :note => "PullRequest Gem" }
      )

      token =  resp["token"]
      set_github_token(token)

      puts "==Token acquired"
    end
    token
  end

  def get_github_token(tolerate_failure = false)
    Git.run("config --get github.token", tolerate_failure)
  end

  def set_github_token(token)
    Git.run("config github.token '#{token}'")
  end

  def unset_github_token
    Git.run("config --unset github.token")
  end

end

module PullRequest
  extend self

  # Lists open pull requests
  def pulls
    items = Github.get("repos/#{REPO}/pulls")
    puts "#{items.length} pull requests"
    items.each do |i|
      puts "_" * 80
      puts color("\##{i['number']} #{i['title']}", :green)
      puts i['html_url']
      puts "#{word_wrap(i['body'])}" if i['body'] && !i['body'].empty?
      puts "_" * 80
      puts ""
    end
  end

  # Lists open issues
  def issues
    items = Github.get("repos/#{REPO}/issues")
    puts "#{items.length} issues"
    items.each do |i|
      puts "_" * 80
      puts color("\##{i['number']} #{i['title']} (#{i['user']['login']})", :green)
      puts "Pull request: #{i['pull_request']['html_url']}" if i['pull_request']['html_url']
      puts "#{word_wrap(i['body'])}" if i['body'] && !i['body'].empty?
      puts "_" * 80
      puts ""
    end
  end

  # Cleans state
  def clean
    Github.clean
  end

  # Squash-merges a pull request
  def merge(number)
    pull_request = Github.get("repos/#{REPO}/pulls/#{number}")
    issue = Github.get("repos/#{REPO}/issues/#{number}")

    raise color("Pull request not found", :red) if !pull_request['state']
    raise color("Pull request already closed", :red) if pull_request['state'] == "closed"

    head = pull_request["head"]
    base = pull_request["base"]

    # Verify we're merging something into master
    raise color("Not merging into master", :red) unless base['ref'] == "master"

    puts "Merging " + color(head['ref'] + ": " + head['sha'], :green)
    puts "   into " + color(base['ref'] + ": " + base['sha'], :green)

    with_temporary_branch(base) do |tmp|
      puts "Merging head to temporary branch"
      Git.run("merge --squash #{head['sha']}")

      commit_msg = merge_commit_msg(pull_request, issue)

      puts "Committing"
      Git.commit("-m #{Shellwords.escape(commit_msg)} -e")

      puts "Merging temporary branch to master"
      Git.run("checkout master")
      Git.run("merge #{tmp}")

      puts "Pushing to origin"
      Git.run("push origin master")

      puts "Deleting local and remote branches"
      Git.run("push origin :#{head['ref']}")
      Git.run("branch -D #{head['ref']}")
    end
  end

  def preview(number)
    pull_request = Github.get("repos/#{REPO}/pulls/#{number}")
    issue = Github.get("repos/#{REPO}/issues/#{number}")
    puts "-" * 80
    puts merge_commit_msg(pull_request, issue)
    puts "-" * 80
  end

  def close(num)
    close_pull_request(num)
  end

  private

  # Github API calls
  def close_pull_request(number)
    Github.patch("repos/#{REPO}/pulls/#{number}", :body => { :state => :closed })
  end

  # Misc
  def merge_commit_msg(pull_request, issue)
    output = "#{pull_request['title']}\n\n"
    output += "#{word_wrap(pull_request['body'])}\n\n" if pull_request['body'] && !pull_request['body'].empty?
    output += "Author: @#{issue['user']['login']}\nFixes \##{pull_request['number']}\nURL: #{pull_request['html_url']}"
  end

  def word_wrap(str, len = 80)
    count = 0
    output = ""
    str.split("\n").each do |s1|
      s1.split(" ").each do |s2|
        if count + s2.length > len
          output += "\n#{s2} "
          count = 0
        else
          output += "#{s2} "
        end
        count += s2.length + 1
      end
      output += "\n"
    end
    output
  end

  @colors = {:clear => 0, :green => 32, :red => 31}
  def color(str, color)
    "\e[#{@colors[color]}m" + str + "\e[#{@colors[:clear]}m"
  end

  def with_temporary_branch(base)
    Git.run("checkout master")
    Git.run("pull")
    tmp_branch = "__tmp_merge_branch_#{base['ref']}_#{base['sha']}"
    begin
      puts "Creating temporary branch: #{tmp_branch}"
      Git.run("checkout -b #{tmp_branch}")

      yield tmp_branch
    ensure
      Git.run("reset --hard")
      puts "Deleting temporary branch: #{tmp_branch}"
      Git.run("checkout master")
      Git.run("branch -D #{tmp_branch}")
    end
  end
end

action = ARGV[0]
case action
when "pulls"
  PullRequest.pulls
when "issues"
  PullRequest.issues
when "clean"
  PullRequest.clean
when "merge"
  PullRequest.merge ARGV[1]
when "preview"
  PullRequest.preview ARGV[1]
when "close"
  PullRequest.close ARGV[1]
else
  puts USAGE
end
