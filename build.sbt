import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.CrossVersion

val akkaVersion = "2.6.20"

//val Scala212 = "2.12.11"
val Scala213 = "2.13.9"
//val scalaVersions = Seq(Scala212, Scala213)

//https://tpolecat.github.io/2017/04/25/scalac-flags.html
lazy val scalacSettings2_13 = Seq(
  /*scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-target:jvm-1.8",
    "-encoding",
    "UTF-8",
    "-Ywarn-unused:imports"
  )*/

  scalacOptions ++= Seq(
    //"-deprecation",
    "-explaintypes",
    "-feature",
    "-unchecked"
  )

  /*scalacOptions ++= Seq(
    //"-deprecation",     // Emit warning and location for usages of deprecated APIs.
    "-unchecked",         // Enable additional warnings where generated code depends on assumptions.
    "-encoding", "UTF-8", // Specify character encoding used by source files.
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard"              // Warn when non-Unit expression results are unused.
  )*/

)


val `akka-bc` = project
  .in(file("."))
  .settings(scalacSettings2_13)
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .settings(
    name := "akka-bc",
    version := "0.0.1",
    scalaVersion := "2.13.8",

    //These setting is used when
    // Compile / run / fork := true and you run one of the aliases or use runMain,
    //otherwise use
    // sbt -J-Xmx1024M -J-XX:MaxMetaspaceSize=850M -J-XX:+UseG1GC -J-XX:+PrintCommandLineFlags -J-XshowSettings:vm first
    // or set .sbtopts
    javaOptions ++= Seq("-Xms512M", "-Xmx700M", "-XX:MaxMetaspaceSize=600M", "-XX:+UseG1GC", "-XX:+PrintCommandLineFlags", "-XshowSettings:vm"),

    //javaOptions in Universal ++= Seq("-J-Xms512M", "-J-Xmx700M", "-J-XX:MaxMetaspaceSize=600M"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-typed"    % akkaVersion,
      "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,

      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.3",

      "io.spray" %%  "spray-json" % "1.3.6",

      //to store local ReplicatedChain  data
      "org.rocksdb" % "rocksdbjni" %  "7.6.0",    //Jun, 2020
      //"com.h2database"  % "h2"          % "1.4.197",
      //"com.h2database"  % "h2-mvstore"  % "1.4.197"

      //search inside a ledger
      "com.yandex.yoctodb" % "yoctodb-core"  % "0.0.20",

      //https://github.com/typelevel/algebra/blob/46722cd4aa4b01533bdd01f621c0f697a3b11040/docs/docs/main/tut/typeclasses/overview.md
      //"org.typelevel" %% "algebra" % "2.7.0",

      "org.hdrhistogram"  % "HdrHistogram" %  "2.1.12",

      //JsonValueCodecJsValue
      //PlayJsonJsoniter.serialize()
      "com.evolutiongaming" %% "play-json-jsoniter" % "0.10.0",

      ("com.lihaoyi" % "ammonite" % "2.5.4" % "test").cross(CrossVersion.full),

      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion),

    fork / run := false,

    // disable parallel tests
    Test / parallelExecution := false,
  ) configs MultiJvm


scalafmtOnCompile := true

//test:run
Test  / sourceGenerators += Def.task {
  val file = (Test / sourceManaged).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")

promptTheme := ScalapenosTheme

Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value)

// (optional) If you need scalapb/scalapb.proto or anything from google/protobuf/*.proto
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"