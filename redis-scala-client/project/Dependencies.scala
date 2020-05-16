import sbt._

object Dependencies {
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.1.3"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  lazy val fs2 = "co.fs2" %% "fs2-core" % "2.2.1" // For cats 2 and cats-effect 2
  lazy val fs2IO = "co.fs2" %% "fs2-io" % "2.2.1"

  
}
