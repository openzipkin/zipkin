## Running a Hadoop job
It's possible to setup Scribe to log into Hadoop. If you do this you can generate various reports from the data
that is not easy to do on the fly in Zipkin itself.

We use a library called <a href="http://github.com/twitter/scalding">Scalding</a> to write Hadoop jobs in Scala.

1. To run a Hadoop job first make the fat jar.
    `sbt 'project zipkin-hadoop' compile assembly`
2. Change scald.rb to point to the hostname you want to copy the jar to and run the job from.
3. Update the version of the jarfile in scald.rb if needed.
3. You can then run the job using our scald.rb script.
    `./scald.rb --hdfs com.twitter.zipkin.hadoop.[classname] --date yyyy-mm-ddThh:mm yyyy-mm-ddThh:mm --output [dir]`

