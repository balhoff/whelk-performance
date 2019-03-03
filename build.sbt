enablePlugins(JavaAppPackaging)

organization  := "org.geneontology"

name          := "whelk-performance"

version       := "0.2.2.0"

publishArtifact in Test := false

licenses := Seq("BSD-3-Clause" -> url("https://opensource.org/licenses/BSD-3-Clause"))

scalaVersion  := "2.12.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

mainClass in Compile := Some("org.geneontology.whelk.Main")

javaOptions += "-Xmx20G"

libraryDependencies ++= {
  Seq(
    "net.sourceforge.owlapi" %  "owlapi-distribution"    % "4.5.9",
    "org.phenoscape"         %% "scowl"                  % "1.3.2",
    "org.geneontology"       %% "whelk"                  % "0.2.2",
    "org.semanticweb.elk"    %  "elk-owlapi"             % "0.4.3",
    "com.outr"               %% "scribe-slf4j"           % "2.7.2"
  )
}
