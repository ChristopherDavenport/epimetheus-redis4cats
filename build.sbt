import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val specs2V = "4.8.1"

val kindProjectorV = "0.11.3"
val betterMonadicForV = "0.3.1"

// Projects
lazy val `epimetheus-redis4cats` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "epimetheus-redis4cats",
    // we need to work around deprecated methods we have to implement from upstream, but we can't use @nowarn
    // as long as 2.12 is still supported – so we rely on 2.13 warnings only
    scalacOptions := (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, y)) if y >= 13 =>  scalacOptions.value :+ "-Wconf:cat=deprecation:is"
      case _ => scalacOptions.value.filter(_ != "-Xfatal-warnings")
    })
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings{
    import microsites._
    Seq(
      micrositeName := "epimetheus-redis4cats",
      micrositeDescription := "Redis4cats Metrics",
      micrositeAuthor := "Christopher Davenport",
      micrositeGithubOwner := "ChristopherDavenport",
      micrositeGithubRepo := "epimetheus-redis4cats",
      micrositeBaseUrl := "/epimetheus-redis4cats",
      micrositeDocumentationUrl := "https://www.javadoc.io/doc/io.chrisdavenport/epimetheus-redis4cats_2.12",
      micrositeGitterChannelUrl := "ChristopherDavenport/libraries", // Feel Free to Set To Something Else
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      micrositeCompilingDocsTool := WithMdoc,
      scalacOptions in Tut --= Seq(
        "-Xfatal-warnings",
        "-Ywarn-unused-import",
        "-Ywarn-numeric-widen",
        "-Ywarn-dead-code",
        "-Ywarn-unused:imports",
        "-Xlint:-missing-interpolator,_"
      ),
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
          file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "100")),
          file("LICENSE")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "101"))
      )
    )
  }

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq(scalaVersion.value, "2.12.12"),

  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),

  libraryDependencies ++= Seq(
    "io.chrisdavenport"           %% "epimetheus"                 % "0.4.0",
    "dev.profunktor"              %% "redis4cats-effects"         % "0.11.1",

    "org.specs2"                  %% "specs2-core"                % specs2V       % Test,
    "org.specs2"                  %% "specs2-scalacheck"          % specs2V       % Test
  )
)

// General Settings
inThisBuild(List(
  organization := "io.chrisdavenport",
  developers := List(
    Developer("ChristopherDavenport", "Christopher Davenport", "chris@christopherdavenport.tech", url("https://github.com/ChristopherDavenport"))
  ),

  homepage := Some(url("https://github.com/ChristopherDavenport/epimetheus-redis4cats")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),

  pomIncludeRepository := { _ => false},
  scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/ChristopherDavenport/epimetheus-redis4cats/blob/v" + version.value + "€{FILE_PATH}.scala"
  )
))