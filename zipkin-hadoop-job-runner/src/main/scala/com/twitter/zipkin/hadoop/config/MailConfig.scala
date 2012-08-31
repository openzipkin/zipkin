/*
* Copyright 2012 Twitter Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.twitter.zipkin.hadoop.config

import com.twitter.util.Config
import com.twitter.zipkin.hadoop.email.SMTPClient

class MailConfig() extends Config[SMTPClient] {

  // You'll need to change these
  var smtpServer: String = "your.postmaster"
  var smtpPort: Int = 0
  var from: String = "zipkin-service-report@abc.efg"
  var bcc: String = "zipkin-service-report@abc.efg"
  var auth: Boolean = true
  var user: String = "username"
  var password: String = "password"

  // All test emails are delivered to this address
  var testTo: String = "zipkin-service-report-test@abc.efg"

  def apply(testMode: Boolean): SMTPClient = {
    new SMTPClient(from, testTo, bcc, testMode, smtpServer, smtpPort, auth, user, password)
  }

  def apply(): SMTPClient = {
    apply(false)
  }
}