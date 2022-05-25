addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.3.2" )
addSbtPlugin("com.github.sbt"    % "sbt-pgp"      % "2.1.2")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype" % "3.9.12")
addSbtPlugin("com.github.sbt" % "sbt-release"  % "1.1.0")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.11"