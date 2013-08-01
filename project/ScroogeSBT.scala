package com.twitter.scrooge

import sbt._
import Keys._

import java.io.File
import scala.collection.JavaConverters._

object ScroogeSBT extends Plugin {

  def compile(
               log: Logger,
               outputDir: File,
               thriftFiles: Set[File],
               thriftIncludes: Set[File],
               namespaceMappings: Map[String, String],
               language: String,
               flags: Set[String]
               ) {

    val compiler = new Compiler()
    compiler.destFolder = outputDir.getPath
    thriftFiles.map { _.getPath }
    thriftIncludes.map { compiler.includePaths += _.getPath }
    namespaceMappings.map { e => compiler.namespaceMappings.put(e._1, e._2)}
    Main.parseOptions(compiler, flags.toSeq ++ thriftFiles.map { _.getPath })
    compiler.language = language.toLowerCase
    compiler.run()
  }

  val scroogeBuildOptions = SettingKey[Seq[String]](
    "scrooge-build-options",
    "command line args to pass to scrooge"
  )

  val scroogeThriftIncludeFolders = SettingKey[Seq[File]](
    "scrooge-thrift-include-folders",
    "folders to search for thrift 'include' directives"
  )

  val scroogeThriftNamespaceMap = SettingKey[Map[String, String]](
    "scrooge-thrift-namespace-map",
    "namespace rewriting, to support generation of java/finagle/scrooge into the same jar"
  )

  val scroogeThriftSourceFolder = SettingKey[File](
    "scrooge-thrift-source-folder",
    "directory containing thrift source files"
  )

  val scroogeThriftSources = SettingKey[Seq[File]](
    "scrooge-thrift-sources",
    "thrift source files to compile"
  )

  val scroogeThriftOutputFolder = SettingKey[File](
    "scrooge-thrift-output-folder",
    "output folder for generated scala files (defaults to sourceManaged)"
  )

  val scroogeIsDirty = TaskKey[Boolean](
    "scrooge-is-dirty",
    "true if scrooge has decided it needs to regenerate the scala files from thrift sources"
  )

  val scroogeGen = TaskKey[Seq[File]](
    "scrooge-gen",
    "generate scala code from thrift files using scrooge"
  )

  /**
   * these settings will go into both the compile and test configurations.
   * you can add them to other configurations by using inConfig(<config>)(genThriftSettings),
   * e.g. inConfig(Assembly)(genThriftSettings)
   */
  val genThriftSettings: Seq[Setting[_]] = Seq(
    scroogeThriftSourceFolder <<= (sourceDirectory) { _ / "thrift" },
    scroogeThriftSources <<= (scroogeThriftSourceFolder) { srcDir => (srcDir ** "*.thrift").get },
    scroogeThriftOutputFolder <<= (sourceManaged) { x => x },
    scroogeThriftIncludeFolders := Seq(),
    scroogeThriftNamespaceMap := Map(),

    // look at includes and our sources to see if anything is newer than any of our output files
    scroogeIsDirty <<= (
      streams,
      scroogeThriftSources,
      scroogeThriftOutputFolder,
      scroogeThriftIncludeFolders
      ) map { (out, sources, outputDir, inc) =>
    // figure out if we need to actually rebuild, based on mtimes.
      val allSourceDeps = sources ++ inc.foldLeft(Seq[File]()) { (files, dir) =>
        files ++ (dir ** "*.thrift").get
      }
      val sourcesLastModified:Seq[Long] = allSourceDeps.map(_.lastModified)
      val newestSource = if (sourcesLastModified.size > 0) {
        sourcesLastModified.max
      } else {
        Long.MaxValue
      }
      val outputsLastModified = (outputDir ** "*.scala").get.map(_.lastModified)
      val oldestOutput = if (outputsLastModified.size > 0) {
        outputsLastModified.min
      } else {
        Long.MinValue
      }
      oldestOutput < newestSource
    },

    // actually run scrooge
    scroogeGen <<= (
      streams,
      scroogeIsDirty,
      scroogeThriftSources,
      scroogeThriftOutputFolder,
      scroogeBuildOptions,
      scroogeThriftIncludeFolders,
      scroogeThriftNamespaceMap
      ) map { (out, isDirty, sources, outputDir, opts, inc, ns) =>
    // for some reason, sbt sometimes calls us multiple times, often with no source files.
      outputDir.mkdirs()
      if (isDirty && !sources.isEmpty) {
        out.log.info("Generating scrooge thrift for %s ...".format(sources.mkString(", ")))
        compile(out.log, outputDir, sources.toSet, inc.toSet, ns, "scala", opts.toSet)
      }
      (outputDir ** "*.scala").get.toSeq
    },
    sourceGenerators <+= scroogeGen
  )

  val newSettings = Seq(
    scroogeBuildOptions := Seq("--finagle")
  ) ++ inConfig(Test)(genThriftSettings) ++ inConfig(Compile)(genThriftSettings)
}

