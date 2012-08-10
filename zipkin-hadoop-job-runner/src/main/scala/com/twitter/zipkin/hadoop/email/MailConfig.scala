package com.twitter.zipkin.hadoop.email

import com.twitter.util.Config

class MailConfig() extends Config[Email] {

  // You'll need to change these
  var smtpServer: String = "your.smtp.server"
  var smtpPort: Int = -1
  var from: String = "zipkin-service-report@abc.xyz"
  var bcc: String = "zipkin-service-report@abc.xyz"
  var auth: Boolean = false
  var user: String = "username"
  var password: String = "password"

  // All test emails are delivered to this address
  var testTo: String = "zipkin-service-report-test@abc.xyz"

  def apply(testMode: Boolean): Email = {
    new Email(from, testTo, bcc, testMode, smtpServer, smtpPort, auth, user, password)
  }

  def apply(): Email = {
    apply(false)
  }
}
