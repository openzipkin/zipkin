package com.twitter.sbt

import java.util.regex.Pattern
import sbt._
import Keys._

/**
 * build a twitter style packgae containing the packaged jar, all its deps,
 * configs, scripts, etc.
 */
object PackageDist extends Plugin {
  // only works for git projects
  import GitProject._

  /**
   * flag for determining whether we name this with a version or a sha
   */
  val packageDistReleaseBuild =
    SettingKey[Boolean]("package-dist-release-build", "is this a release build")

  /**
   * where to build and stick the dist
   */
  val packageDistDir =
    SettingKey[File]("package-dist-dir", "the directory to package dists into")

  /**
   * the task to actually build the zip file
   */
  val packageDist =
    TaskKey[File]("package-dist", "package a distribution for the current project")

  /**
   * the name of our distribution
   */
  val packageDistName =
    SettingKey[String]("package-dist-name", "name of our distribution")

  /**
   * where to find config files (if any)
   */
  val packageDistConfigPath =
    SettingKey[Option[File]]("package-dist-config-path", "location of config files (if any)")

  /**
   * where to write configs within the zip
   */
  val packageDistConfigOutputPath =
    SettingKey[Option[File]]("package-dist-config-output-path", "location of config output path")

  /**
   * where to find resource files (if any)
   */
  val packageDistResourcesPath =
    SettingKey[Option[File]]("package-dist-resources-path", "location of resources (if any)")

  /**
   * where to write resource files in the zip
   */
  val packageDistResourcesOutputPath =
    SettingKey[Option[File]]("package-dist-resources-output-path", "location of resources output path")


  /**
   * where to find script files (if any)
   */
  val packageDistScriptsPath =
    SettingKey[Option[File]]("package-dist-scripts-path", "location of scripts (if any)")

  /**
   * where to write script files in the zip
   */
  val packageDistScriptsOutputPath =
    SettingKey[Option[File]]("package-dist-scripts-output-path", "location of scripts output path")

  /**
   * the name of our zip
   */
  val packageDistZipName =
    TaskKey[String]("package-dist-zip-name", "name of packaged zip file")

  /**
   * where to rebase files inside the zip
   */
  val packageDistZipPath =
    TaskKey[String]("package-dist-zip-path", "path of files inside the packaged zip file")

  /**
   * task to clean up the dist directory
   */
  val packageDistClean =
    TaskKey[Unit]("package-dist-clean", "clean distribution artifacts")

  /**
   * task to generate the map of substitutions to perform on scripts as they're copied
   */
  val packageVars =
    TaskKey[Map[String, String]]("package-vars", "build a map of subtitutions for scripts")

  /**
   * task to copy dependent jars from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyLibs =
    TaskKey[Set[File]]("package-dist-copy-libs", "copy scripts into the package dist folder")

  /**
   * task to copy scripts from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyScripts =
    TaskKey[Set[File]]("package-dist-copy-scripts", "copy scripts into the package dist folder")

  /**
   * task to copy resources from the source folder to dist, doing @VAR@ substitutions along the way
   */
  val packageDistCopyResources =
    TaskKey[Set[File]]("package-dist-copy-resources", "copy resources into the package dist folder")


  val packageDistConfigFiles =
    TaskKey[Set[File]]("package-dist-config-files", "config files to package with the server")

  /**
   * task to copy config files from the source folder to dist
   */
  val packageDistCopyConfig =
    TaskKey[Set[File]]("package-dist-copy-config", "copy config files into the package dist folder")

  /**
   * task to copy exported jars from the target folder to dist
   */
  val packageDistCopyJars =
    TaskKey[Set[File]]("package-dist-copy-jars", "copy exported files into the package dist folder")

  /**
   * task to copy all dist-ready files to dist
   */
  val packageDistCopy =
    TaskKey[Set[File]]("package-dist-copy", "copy all dist files into the package dist folder")

  // validate config files
  val packageDistConfigFilesValidationRegex =
    SettingKey[Option[String]](
      "package-dist-config-files-validation-regex",
      "regex to match config files against, if you would like package-time validation"
    )

  val packageDistValidateConfigFiles =
    TaskKey[Set[File]]("package-dist-validate-config-files", "validate config files")

  // utility to copy a directory tree to a new one
  def copyTree(
    srcOpt: Option[File],
    destOpt: Option[File],
    selectedFiles: Option[Set[File]] = None
  ): Set[(File, File)] = {
    srcOpt.flatMap { src =>
      destOpt.map { dest =>
        val rebaser = Path.rebase(src, dest)
        selectedFiles.getOrElse {
          (PathFinder(src) ***).filter(!_.isDirectory).get
        }.flatMap { f =>
          rebaser(f) map { rebased =>
            (f, rebased)
          }
        }
      }
    }.getOrElse(Seq()).toSet
  }

  val newSettings = Seq(
    exportJars := true,

    // export source and docs
    (exportedProducts in Compile) <<= (
      exportedProducts in Compile,
      packageSrc in Compile,
      packageDoc in Compile
    ) map { (exports, src, doc) =>
      exports ++ Seq(Attributed.blank(src), Attributed.blank(doc))
    },

    // write a classpath entry to the manifest
    packageOptions <+= (dependencyClasspath in Compile, mainClass in Compile) map { (cp, main) =>
      val manifestClasspath = cp.files.map(f => "libs/" + f.getName).mkString(" ")
      // not sure why, but Main-Class needs to be set explicitly here.
      val attrs = Seq(("Class-Path", manifestClasspath)) ++ main.map { ("Main-Class", _) }
      Package.ManifestAttributes(attrs: _*)
    },
    packageDistReleaseBuild <<= (version) { v => !(v.toString contains "SNAPSHOT") },
    packageDistName <<= (packageDistReleaseBuild, name, version) { (r, n, v) =>
      if (r) {
        n + "-" + v
      } else {
        n
      }
    },
    packageDistDir <<= (baseDirectory, packageDistName) { (b, n) => b / "dist" / n },
    packageDistConfigPath <<= (baseDirectory) { b => Some(b / "config") },
    packageDistConfigOutputPath <<= (packageDistDir) { d => Some(d / "config") },
    packageDistScriptsPath <<= (baseDirectory) { b => Some(b / "src" / "scripts") },
    packageDistScriptsOutputPath <<= (packageDistDir) { d => Some(d / "scripts") },
    packageDistResourcesPath <<= (baseDirectory) { b => Some(b / "src/main" / "resources")},
    packageDistResourcesOutputPath <<= (packageDistDir) { d => Some(d / "resources") },

    // for releases, default to putting the zipfile contents inside a subfolder.
    packageDistZipPath <<= (
      packageDistReleaseBuild,
      packageDistName
    ) map { (release, name) =>
      if (release) name else ""
    },

    // if release, then name it the version. otherwise the first 8 characters of the sha
    packageDistZipName <<= (
      packageDistReleaseBuild,
      gitProjectSha,
      name,
      version
    ) map { (r, g, n, v) =>
      val revName = g.map(_.substring(0, 8)).getOrElse(v)
      "%s-%s.zip".format(n, if (r) v else revName)
    },

    packageVars <<= (
      dependencyClasspath in Runtime,
      dependencyClasspath in Test,
      exportedProducts in Compile,
      crossPaths,
      name,
      version,
      scalaVersion,
      gitProjectSha
    ) map { (rcp, tcp, exports, crossPaths, name, version, scalaVersion, sha) =>
      val distClasspath = rcp.files.map("${DIST_HOME}/libs/" + _.getName) ++
        exports.files.map("${DIST_HOME}/" + _.getName)
      Map(
        "CLASSPATH" -> rcp.files.mkString(":"),
        "TEST_CLASSPATH" -> tcp.files.mkString(":"),
        "DIST_CLASSPATH" -> distClasspath.mkString(":"),
        "DIST_NAME" -> (if (crossPaths) (name + "_" + scalaVersion) else name),
        "VERSION" -> version,
        "REVISION" -> sha.getOrElse("")
      )
    },

    packageDistCopyScripts <<= (
      packageVars,
      packageDistScriptsPath,
      packageDistScriptsOutputPath
    ) map { (vars, script, scriptOut) =>
      copyTree(script, scriptOut).map { case (source, destination) =>
        destination.getParentFile().mkdirs()
        com.twitter.sbt.FileFilter.filter(source, destination, vars)
        List("chmod", "+x", destination.absolutePath.toString) !!;
        destination
      }
    },

    packageDistCopyResources <<= (
      packageVars,
      packageDistResourcesPath,
      packageDistResourcesOutputPath
    ) map { (vars, resource, resourceOut) =>
      copyTree(resource, resourceOut).map { case (source, destination) =>
        destination.getParentFile().mkdirs()
        com.twitter.sbt.FileFilter.filter(source, destination, vars)
        destination
      }
    },

    packageDistCopyConfig <<= (
      packageDistConfigPath,
      packageDistConfigOutputPath,
      packageDistConfigFiles
    ) map { (conf, confOut, files) =>
      IO.copy(copyTree(conf, confOut, Some(files)))
    },

    packageDistCopyLibs <<= (
      dependencyClasspath in Runtime,
      exportedProducts in Compile,
      packageDistDir
    ) map { (cp, products, dest) =>
      val jarFiles = cp.files.filter(f => !products.files.contains(f))
      val jarDest = dest / "libs"
      jarDest.mkdirs()
      IO.copy(jarFiles.map { f => (f, jarDest / f.getName) })
    },

    // copy all our generated "products" (i.e. "the jars")
    packageDistCopyJars <<= (
      exportedProducts in Compile,
      exportedProducts in Test,
      packageDistDir
    ) map { (products, testProducts, dest) =>
      IO.copy((products ++ testProducts).files.map(p => (p, dest / p.getName)))
    },

    packageDistCopy <<= (
      packageDistCopyLibs,
      packageDistCopyScripts,
      packageDistCopyResources,
      packageDistCopyConfig,
      packageDistCopyJars
    ) map { (libs, scripts, resources, config, jars) =>
      libs ++ scripts ++ resources ++ config ++ jars
    },

    packageDistConfigFiles <<= (
      packageDistConfigPath
    ) map { (configPath) =>
      configPath.map { p =>
        (PathFinder(p) ***).filter { f =>
          // skip anything matching "/target/", which are probably cached pre-compiled config files.
          !f.isDirectory && !(f.getPath contains "/target/")
        }.get
      }.getOrElse(Seq()).toSet
    },

    packageDistConfigFilesValidationRegex := None,

    packageDistValidateConfigFiles <<= (
      streams,
      packageDistCopyJars,
      packageDistConfigFilesValidationRegex,
      packageDistCopyConfig,
      packageDistCopyLibs
    ) map { (s, jars, regex, files, _) =>
      val jar = jars.filter { f =>
        !f.getName.contains("-sources") && !f.getName.contains("-javadoc")
      }.head
      files.filter { file =>
        regex.map { r => Pattern.matches(r, file.getName) }.getOrElse { false }
      }.map { file =>
        s.log.info("Validating config file: " + file.absolutePath)
        if ((List("java", "-jar", jar.absolutePath, "-f", file.absolutePath, "--validate")!) != 0) {
          throw new Exception("Failed to validate config file: " + file.toString)
        }
        file
      }
    },

    // package all the things
    packageDist <<= (
      test in Test,
      baseDirectory,
      packageDistCopy,
      packageDistValidateConfigFiles,
      packageDistDir,
      packageDistName,
      packageDistZipPath,
      packageDistZipName,
      streams
    ) map { (_, base, files, _, dest, distName, zipPath, zipName, s) =>
      // build the zip
      s.log.info("Building %s from %d files.".format(zipName, files.size))
      val zipRebaser = Path.rebase(dest, zipPath)
      val zipFile = base / "dist" / zipName
      IO.zip(files.map(f => (f, zipRebaser(f).get)), zipFile)
      zipFile
    }
  )
}
