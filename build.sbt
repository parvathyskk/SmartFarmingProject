name := "SmartFarmingProject"

version := "0.1"

scalaVersion := "2.13.14"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.10",
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2", // for JSON support
  "io.circe" %% "circe-generic" % "0.14.3",
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.10.0"
)

