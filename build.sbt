scalaVersion := "2.11.7"

scalacOptions ++= 
  List(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Xfuture",
    "-Yno-adapted-args")

libraryDependencies ++=
  List(
    "org.scalaz" %% "scalaz-core" % "7.1.5",
    "org.scalaz" %% "scalaz-concurrent" % "7.1.5")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
