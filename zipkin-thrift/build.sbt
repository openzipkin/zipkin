defaultSettings

// this is a hack to get -idl artifacts for thrift.  Better would be to
// define a whole new artifact that gets included in the scrooge publish task
(artifactClassifier in packageSrc) := Some("idl")
