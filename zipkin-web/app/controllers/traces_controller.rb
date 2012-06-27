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

include ActionView::Helpers::DateHelper

require 'base64'

require 'endpoint'
require 'annotation'
require 'names'
require 'trace_summary'
require 'ztrace'
require 'index'
require 'timeline_annotation'

class TracesController < ApplicationController

  respond_to :html, :json

  before_filter :clock_skew, :banner_msg

  def index
    @end_time = Time.zone.now
    @limit = 100 #TODO const somewhere else? used in query request too
  end

  def show
    @trace_id = params[:id]
  end

  def get_trace
    trace_id = params[:id]

    combos = TraceCombo.get_trace_combos_by_ids([trace_id], @adjusters)

    if (!combos.any?)
      return render_404
    end

    combo = combos[0]
    @span_depths = combo.span_depths
    @timeline = combo.timeline
    @trace_summary = combo.summary
    @trace = combo.trace

    @kv_annotations = {}
    @trace.spans.each do |s|
      if s.binary_annotations && s.binary_annotations.length > 0
        @kv_annotations[s.id.to_s] = s.binary_annotations
      end
    end

    # Convert bytes to Base64 encoded string
    @kv_annotations.each do |k,v|
      v.each do |b|
        b.value = Base64.encode64(b.value) if b.annotation_type == Zipkin::AnnotationType::BYTES
      end
    end

    Trace.record(Trace::BinaryAnnotation.new("spans", @trace.spans.size.to_s, "STRING", Trace.default_endpoint))

    Trace.record(Trace::Annotation.new("get_annotation_rows_start", Trace.default_endpoint))
    @annotation_rows = @timeline.get_annotation_rows
    Trace.record(Trace::Annotation.new("get_annotation_rows_end", Trace.default_endpoint))

    Trace.record(Trace::BinaryAnnotation.new("annotations", @annotation_rows.size.to_s, "STRING", Trace.default_endpoint))


    # add HTML newline tags to annotations for readability
    @annotation_rows.each { |row|
      row[:annotation] = row[:annotation].gsub(/\n/, '<br>')
    }

    start_time = Time.at(@annotation_rows.first[:timestamp] / 1000 / 1000)

    rv = {
      :duration            => @timeline.duration_micro / 1000.0,
      :date                => start_time.strftime('%Y-%m-%d'),
      :time                => start_time.strftime('%H:%M:%S'),
      :time_ago_in_words   => time_ago_in_words(start_time),

      :trace               => @trace_summary,
      :spans               => @trace,
      :annotations         => @annotation_rows,
      :kv_annotations      => @kv_annotations
    }
    respond_to do |format|
      format.json { render :json => rv }
    end
  end

  def hex
    hex_trace_id = params[:id]
    trace_id = hex_trace_id.hex
    redirect_to :action => 'show', :id => trace_id
  end

  def services_json
    # Get all of the service names
    render :json => Names.get_service_names.to_a
  end

  def spans_json
    # Get all of the spans for a service_name
    service_name = params[:service_name] || ""
    render :json => Names.get_span_names(service_name).to_a
  end

  def top_annotations
    service_name = params[:service_name] || ""
    top_annotations = ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      client.getTopAnnotations(service_name)
    end
    render :json => top_annotations
  end

  def top_kv_annotations
    service_name = params[:service_name] || ""
    top_annotations = ZipkinQuery::Client.with_transport(Rails.configuration.zookeeper) do |client|
      client.getTopKeyValueAnnotations(service_name)
    end
    render :json => top_annotations
  end

  def query
    begin
      request = QueryRequest.new(params)
      trace_ids = get_trace_ids(request)
      if trace_ids.any?
        # Adjust the time skew, to make sure we don't show any incorrect timings in the results
        @summaries = TraceSummary.get_trace_summaries_by_ids(trace_ids, @adjusters)
        # TODO this is due to us getting some traces that are multiple traces in one, causing GIGANTIC duration times
        @summaries = @summaries.select { |s| s.duration_ms < 60000 }
        max_summary = @summaries.max { |a,b| a.duration_ms <=> b.duration_ms }
        @max_time = max_summary.duration_ms
        @summaries.sort! {|x,y| y.duration_micro <=> x.duration_micro }
      else
        @summaries = []
      end

      # Set the url
      @summaries.each do |s|
        s.set_url(trace_path(s.trace_id))
      end

      render :json => @summaries
    rescue => e
      @error = e.message
      render :json => @error
    end

  end

  # Is this trace 'pinned'? Meaning: do we want to store it for longer than the usual ttl.
  def is_pinned_json
    begin
      trace_id = params[:trace_id]
      if (trace_id.nil?)
        return render_404
      end

      render :json => { :pinned => ZTrace.get_ttl(trace_id.to_i) == Rails.configuration.pinned_ttl_sec }
    rescue => e
      render :json => { :error => e.message }
    end
  end

  # pin or unpin the trace
  def pin_json
    begin
      trace_id = params[:trace_id]
      if (trace_id.nil?)
        return render_404
      end

      pinned = params[:pinned]

      if (pinned == "true")
        ZTrace.set_ttl(trace_id, Rails.configuration.pinned_ttl_sec)
        render :json => { :pinned => true }
      else
        ZTrace.set_ttl(trace_id, ZTrace.get_default_ttl_sec)
        render :json => { :pinned => false }
      end
    rescue => e
      render :json => { :error => e.message }
    end
  end

  private

  def clock_skew
    @adjust_clock_skew = params[:adjust_clock_skew] == "false" ? false : true
    @adjusters = @adjust_clock_skew ? [Zipkin::Adjust::TIME_SKEW] : []
    @url_params = @adjust_clock_skew ? "" : "?adjust_clock_skew=false"
  end

  def banner_msg
    flash[:message] = Rails.configuration.banner_msg if Rails.configuration.banner_msg
  end

  def render_404
    respond_to do |format|
      format.html { render :file => "#{Rails.root}/public/404.html", :status => :not_found }
      format.any  { head :not_found }
    end
  end

  # get the trace ids by whatever field we have received from the user
  # for example service name, span name, time annotation or key value annotation
  def get_trace_ids(request)
    if (request.span_name)
      Index.get_trace_ids_by_span_name(request.service_name, request.span_name, request.end_time, request.limit, :order => request.order)
    elsif !request.time_annotation.blank?
      Index.get_trace_ids_by_annotation(request.service_name, request.time_annotation, nil, request.end_time, request.limit, :order => request.order)
    elsif !request.annotation_key.blank? && !request.annotation_value.blank?
      Index.get_trace_ids_by_annotation(request.service_name, request.annotation_key, request.annotation_value, request.end_time, request.limit, :order => request.order)
    else
      Index.get_trace_ids_by_service_name(request.service_name, request.end_time, request.limit, :order => request.order)
    end
  end

  def get_date(date, time)
    # Parse a date (%m-%d-%Y) and a time (%H:%M:%S) into a time object

    month, day, year = date.split("-")
    hour, minute, second = time.split(":")

    Time.utc(year, month, day, hour, minute, second)
  end

end
