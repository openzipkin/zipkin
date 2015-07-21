package io.zipkin.sbt

import sbt._
import Keys._

/**
 * various tasks for working with git-based projects
 */
object GitProject extends Plugin {
  val gitIsRepository = TaskKey[Boolean](
    "git-is-repository",
    "true if this is project is inside a git repo"
  )

  val gitProjectSha = TaskKey[Option[String]](
    "git-project-sha",
    "the SHA of the current project head (if it's in a git repo)"
  )

  val gitLastCommits = TaskKey[Option[Seq[String]]](
    "git-last-commits",
    "the latest commits to the project (if it's in a git repo)"
  )

  val gitLastCommitsCount = SettingKey[Int](
    "git-last-commits-count",
    "the number of commits to report from git-last-commits"
  )

  val gitBranchName = TaskKey[Option[String]](
    "git-branch-name",
    "the name of the current git branch, failing back to git-project-sha (if we're in a git repo)"
  )

  val gitCommitMessage = TaskKey[String](
    "git-commit-message",
    "create a commit message for a new release of this project (used by git-commit)"
  )

  val gitCommit = TaskKey[Int](
    "git-commit",
    "commit pending changes to this project (usually as part of publishing a release)"
  )

  val gitTag = TaskKey[Int](
    "git-tag",
    "tag the project with the current version"
  )

  val gitTagName = TaskKey[String](
    "git-tag-name",
    "define a tag name for the current project version (used by git-tag)"
  )

  /**
   * run f if isRepo is true
   */
  def ifRepo[T](isRepo: Boolean)(f: => T): Option[T] = {
    if (isRepo) {
      Some(f)
    } else {
      None
    }
  }

  val gitSettings = Seq(
    gitIsRepository := { ("git status" ! NullLogger) == 0 },
    gitProjectSha <<= (gitIsRepository) map { isRepo =>
      ifRepo(isRepo) {
        ("git rev-parse HEAD" !!).trim
      }
    },
    gitLastCommitsCount := 10,
    gitLastCommits <<= (gitIsRepository, gitLastCommitsCount) map { (isRepo, lastCommitsCount) =>
      ifRepo(isRepo) {
        (("git log --oneline --decorate --max-count=%s".format(lastCommitsCount)) !!).split("\n")
      }
    },
    gitBranchName <<= (gitIsRepository) map { isRepo =>
      ifRepo(isRepo) {
        ("git symbolic-ref -q HEAD" #|| "git rev-parse HEAD" !!).trim
      }
    },
    gitTagName <<= (organization, name, version) map { (o, n, v) =>
      "org=%s,name=%s,version=%s".format(o, n, v)
    },
    gitTag <<= (gitTagName) map { tag =>
      ("git tag -m %s %s".format(tag, tag)).run(false).exitValue
    },
    gitCommitMessage <<= (organization, name, version) map { (o, n, v) =>
      "release commit for %s:%s:%s".format(o, n, v)
    },
    gitCommit <<= (gitCommitMessage) map { m =>
      val pb = ("git add ." #&& Seq("git", "commit", "-m", "'%s'".format(m)))
      val proc = pb.run(false)
      proc.exitValue
    },
    // set these to false... we don't run commit/tag recursively on subprojects
    aggregate in gitCommit := false,
    aggregate in gitTag := false
  )
}
