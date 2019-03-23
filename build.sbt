name := "graph-db"

version := "1.0"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.13"
lazy val leveldbVersion = "0.7"
lazy val leveldbjniVersion = "1.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
//  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,

  // local levelDB stores
  "org.iq80.leveldb"            % "leveldb"          % leveldbVersion,
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % leveldbjniVersion,
  
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.beachape" %% "enumeratum" % "1.5.13"
)
