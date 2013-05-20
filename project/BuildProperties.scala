package com.twitter.sbt

import sbt._
import Keys._
import java.io.{File, FileWriter}
import java.text.SimpleDateFormat
import java.util.{Date, Properties}

/**
 * Add various build-environment properties to a build.properties file in the built jar.
 */
object BuildProperties extends Plugin {
  // most of this stuff is only supported by default in GitProjects
  import GitProject._

  val buildPropertiesPackage = SettingKey[String](
    "build-properties-package",
    "the package in which to write build.properties"
  )

  val buildPropertiesFolder = SettingKey[File](
    "build-properties-folder",
    "the directory to write build.properties into (usually build-properties-package)"
  )

  val buildPropertiesFile = SettingKey[File](
    "build-properties-path",
    "the full path to build.properties"
  )

  val buildPropertiesWrite = TaskKey[Seq[File]](
    "build-properties-write",
    "writes various build properties to a file in resources"
  )

  def writeBuildProperties(
    name: String,
    version: String,
    timestamp: Long,
    currentRevision: Option[String],
    branchName: Option[String],
    lastFewCommits: Option[Seq[String]],
    targetFile: File
  ): Seq[File] = {
    targetFile.getParentFile.mkdirs()

    def buildName = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(timestamp))

    val buildProperties = new Properties
    buildProperties.setProperty("name", name)
    buildProperties.setProperty("version", version)
    buildProperties.setProperty("build_name", buildName)
    currentRevision.foreach(buildProperties.setProperty("build_revision", _))
    branchName.foreach(buildProperties.setProperty("build_branch_name", _))
    lastFewCommits.foreach { commits =>
      buildProperties.setProperty("build_last_few_commits", commits.mkString("\n"))
    }

    val fileWriter = new FileWriter(targetFile)
    buildProperties.store(fileWriter, "")
    fileWriter.close()
    Seq(targetFile)
  }

  val newSettings: Seq[Setting[_]] = Seq(
    buildPropertiesPackage <<= (organization, name) { (o, n) => o + "." + n },
    buildPropertiesFolder <<= (resourceManaged in Compile, buildPropertiesPackage) {(r, b) =>
      val packageSplits = b.split("\\.")
      packageSplits.foldLeft(r) {(f, d) => new File(f, d)}
    },
    buildPropertiesFile <<= (buildPropertiesFolder) { b => new File(b, "build.properties")},
    buildPropertiesWrite <<= (
      streams,
      name,
      version,
      buildPropertiesFile,
      gitProjectSha,
      gitBranchName,
      gitLastCommits
    ) map { (out, n, v, b, sha, branch, commits) =>
        out.log.info("Writing build properties to: %s".format(b))
        writeBuildProperties(n, v, System.currentTimeMillis, sha, branch, commits, b)
    },
    resourceGenerators in Compile <+= buildPropertiesWrite
  )
}
