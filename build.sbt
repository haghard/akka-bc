import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.CrossVersion

val akkaVersion = "2.6.8"

val `akka-bc` = project
  .in(file("."))
  .settings(SbtMultiJvm.multiJvmSettings: _*)
  .settings(
    name := "akka-bc",
    version := "0.0.1",
    scalaVersion := "2.13.3",

    //scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    //javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,

      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",

      "io.spray" %%  "spray-json" % "1.3.5",

      //to store local ReplicatedChain  data
      "org.rocksdb" % "rocksdbjni" %  "6.10.2",    //Jun, 2020
      //"com.h2database"  % "h2"          % "1.4.197",
      //"com.h2database"  % "h2-mvstore"  % "1.4.197"

      //transactions search inside ledger
      "com.yandex.yoctodb" % "yoctodb-core"  % "0.0.19",

      //"org.hdrhistogram"  % "HdrHistogram" %  "2.1.10",
      ("com.lihaoyi" % "ammonite" % "2.2.0" % "test").cross(CrossVersion.full),

      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion),

    //fork in run := true,

    // disable parallel tests
    parallelExecution in Test := false,

    javaOptions ++= Seq("-Xmx4G", "-XX:MaxMetaspaceSize=3G", "-XX:+UseG1GC")

  ) configs MultiJvm

//https://tpolecat.github.io/2017/04/25/scalac-flags.html

scalafmtOnCompile := true

//test:run test:console
sourceGenerators in Test += Def.task {
  val file = (sourceManaged in Test).value / "amm.scala"
  IO.write(file, """object amm extends App { ammonite.Main().run() }""")
  Seq(file)
}.taskValue

promptTheme := ScalapenosTheme

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

// (optional) If you need scalapb/scalapb.proto or anything from google/protobuf/*.proto
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"