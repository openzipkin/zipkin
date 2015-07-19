import Tests._
import com.typesafe.sbt.site.SphinxSupport.Sphinx

defaultSettings
Project.defaultSettings
site.settings
site.sphinxSupport()

scalacOptions in doc <++= (version).map(v => Seq("-doc-title", "Zipkin", "-doc-version", v))

includeFilter in Sphinx := ("*.html" | "*.jpg" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")

// A dummy partitioning scheme for tests
def partitionTests(tests: Seq[TestDefinition]) = {
  Seq(new Group("inProcess", tests, InProcess))
}
// Workaround for sbt bug: Without a testGrouping for all test configs,
// the wrong tests are run
testGrouping <<= definedTests in Test map partitionTests
testGrouping in DocTest <<= definedTests in DocTest map partitionTests

/* Test Configuration for running tests on doc sources */
lazy val DocTest = config("doctest") extend(Test)
configs(DocTest)
inConfig(DocTest)(Defaults.testSettings)
unmanagedSourceDirectories in DocTest <+= baseDirectory { _ / "src/sphinx/code" }
//resourceDirectory in DocTest <<= baseDirectory { _ / "src/test/resources" }
// Make the "test" command run both, test and doctest:test
test <<= Seq(test in Test, test in DocTest).dependOn
