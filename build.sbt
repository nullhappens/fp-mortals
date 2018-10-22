import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.6",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "FP for Mortals",
    scalacOptions in ThisBuild ++= Seq(
      "-language:_",
      "-Ypartial-unification",
      "-language:higherKinds",
      "-Xfatal-warnings"
    ),
    scalafmtOnCompile := true,
    libraryDependencies ++= Seq(
      "com.github.mpilquist" %% "simulacrum" % "0.13.0",
      "org.scalaz" %% "scalaz-core" % "7.2.25",
      "com.propensive" %% "contextual" % "1.1.0",
      scalaTest % Test
    )
  )

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7")
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.2.4")
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
