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

class QueryRequest
  
  attr_accessor :service_name, :end_time, :limit, 
    :span_name, :time_annotation, 
    :annotation_key, :annotation_value, :order  
  
  def initialize(params)
    # cant we just send these as one field?
    if params[:end_date].blank?
      raise "No end date specified. It's required."
    end
    
    if params[:end_time].blank?
      raise "No end time specified. It's required."
    end
    
    if params[:service_name].blank?
      raise "No service name specified. It's required."
    end    
    
    @service_name = params[:service_name]
    @end_time = get_date params[:end_date], params[:end_time]
    @limit = params[:limit] ? params[:limit].to_i : 100

    if !params[:span_name].blank? && params[:span_name] != "all"
      @span_name = params[:span_name]
    end

    if !params[:time_annotation].blank?
      @time_annotation = params[:time_annotation]
    end

    if !params[:annotation_key].blank? && !params[:annotation_value].blank?
      @annotation_key = params[:annotation_key]
      @annotation_value =  params[:annotation_value]
    end  
  end
  
  def get_date(date, time)
    # Parse a date (%m-%d-%Y) and a time (%H:%M:%S) into a time object

    month, day, year = date.split("-")
    hour, minute, second = time.split(":")
    Time.utc(year, month, day, hour, minute, second)
  end
  
end