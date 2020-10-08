import com.typesafe.sbt.packager.docker.DockerChmodType

Global / version := "0.0.1"
Global / scalaVersion := "2.13.3"

val http4sVersion  = "0.21.6"
val grpcVersion    = "1.31.0"
val circeVersion   = "0.12.3"
val fansiVersion   = "0.2.7"
val rocksDbVersion = "6.6.4"
val catsVersion    = "2.0.0"

lazy val scalacSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Xcheckinit",
    "-feature",
    "-Xfatal-warnings",
    "-Ywarn-dead-code",
    "-Ywarn-value-discard"
  )
)

lazy val cli =
  project
    .in(file("cli"))
    .settings(scalacSettings)
    .settings(
      Compile / mainClass := Some("logsdb.cli.CliApp"),
      nativeImageOptions := Seq(
        "-H:+ReportExceptionStackTraces",
        "--no-fallback",
        "--allow-incomplete-classpath"
      ),
      name := "logsdb-cli",
      assemblyJarName := "logsdb-cli.jar",
      libraryDependencies ++= List(
        "io.grpc"        % "grpc-netty"      % grpcVersion,
        "co.fs2"         %% "fs2-io"         % "2.4.0",
        "com.monovore"   %% "decline-effect" % "1.0.0",
        "com.lihaoyi"    %% "fansi"          % fansiVersion,
        "io.circe"       %% "circe-core"     % circeVersion,
        "io.circe"       %% "circe-parser"   % circeVersion,
        "dev.profunktor" %% "console4cats"   % "0.8.1"
      ),
      assemblyMergeStrategy in assembly := {
        case "META-INF/MANIFEST.MF" => MergeStrategy.discard
        case _                      => MergeStrategy.first
      }
    )
    .dependsOn(protobuf)
    .enablePlugins(NativeImagePlugin)

lazy val protobuf =
  project
    .in(file("protobuf"))
    .settings(scalacSettings)
    .settings(
      scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
      libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
    )
    .enablePlugins(Fs2Grpc)
    .disablePlugins(AssemblyPlugin)

lazy val server =
  project
    .in(file("server"))
    .dependsOn(protobuf)
    .settings(scalacSettings)
    .settings(
      name := "server",
      assemblyJarName := "server.jar",
      libraryDependencies ++= List(
        "io.grpc"              % "grpc-netty"           % grpcVersion,
        "io.grpc"              % "grpc-services"        % grpcVersion,
        "org.rocksdb"          % "rocksdbjni"           % rocksDbVersion,
        "org.typelevel"        %% "cats-core"           % catsVersion,
        "org.typelevel"        %% "cats-effect"         % "2.1.4",
        "com.monovore"         %% "decline-effect"      % "1.0.0",
        "io.circe"             %% "circe-config"        % "0.8.0",
        "io.circe"             %% "circe-generic"       % "0.13.0",
        "com.github.valskalla" %% "odin-core"           % "0.8.1",
        "com.lihaoyi"          %% "fansi"               % fansiVersion,
        "org.http4s"           %% "http4s-dsl"          % http4sVersion,
        "org.http4s"           %% "http4s-circe"        % http4sVersion,
        "org.http4s"           %% "http4s-blaze-server" % http4sVersion,
        "org.http4s"           %% "http4s-blaze-client" % http4sVersion
      ),
      assemblyMergeStrategy in assembly := {
        case "META-INF/MANIFEST.MF" => MergeStrategy.discard
        case _                      => MergeStrategy.first
      }
    )
    .settings(
      packageName in Docker := "logsdb",
      dockerUsername := Some("alireza"),
      dockerExposedPorts := Seq(9080, 8080),
      defaultLinuxInstallLocation in Docker := "/opt/logsdb",
      dockerExposedVolumes := Seq("/data"),
      dockerEntrypoint := Seq("/opt/logsdb/bin/entrypoint.sh"),
      dockerPackageMappings in Docker ++= List(
        baseDirectory.value / "docker" / "docker-entrypoint.sh" -> "/opt/logsdb/bin/entrypoint.sh"
      ),
      dockerChmodType := DockerChmodType.UserGroupWriteExecute
    )
    .enablePlugins(JavaAppPackaging)
    .enablePlugins(DockerPlugin)

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(protobuf, server, cli)
  .disablePlugins(AssemblyPlugin)
