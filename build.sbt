Global / version := "0.1"
Global / scalaVersion := "2.13.3"

lazy val cli =
  project
    .in(file("cli"))
    .settings(
      name := "cli",
      libraryDependencies ++= List(
        "io.grpc"      % "grpc-netty"      % "1.31.0",
        "co.fs2"       %% "fs2-io"         % "2.4.0",
        "com.monovore" %% "decline-effect" % "1.0.0",
        "com.lihaoyi"  %% "fansi"          % "0.2.7"
      ),
      assemblyMergeStrategy in assembly := {
        case "META-INF/MANIFEST.MF" => MergeStrategy.discard
        case _                      => MergeStrategy.first
      }
    )
    .dependsOn(protobuf)

lazy val protobuf =
  project
    .in(file("protobuf"))
    .enablePlugins(Fs2Grpc)
    .settings(
      scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage
    )

lazy val server =
  project
    .in(file("server"))
    .dependsOn(protobuf)
    .settings(
      name := "server",
      libraryDependencies ++= List(
        "io.grpc"       % "grpc-netty"      % "1.31.0",
        "io.grpc"       % "grpc-services"   % "1.31.0",
        "org.rocksdb"   % "rocksdbjni"      % "6.6.4",
        "org.typelevel" %% "cats-core"      % "2.0.0",
        "org.typelevel" %% "cats-effect"    % "2.1.4",
        "com.monovore"  %% "decline-effect" % "1.0.0"
      ),
      assemblyMergeStrategy in assembly := {
        case "META-INF/MANIFEST.MF" => MergeStrategy.discard
        case _                      => MergeStrategy.first
      }
    )

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true
  )
  .aggregate(protobuf, server, cli)
