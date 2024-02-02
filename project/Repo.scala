import sbt.*

object Repo {

  object Jfrog {
    private val domain = "tubins.jfrog.io"
    private val jFrogRoot = s"https://$domain"

    object Tubins {
      private val pathPrefix = "tubins"

      lazy val sbtDev: MavenRepository = "sbt-dev" at s"$jFrogRoot/$pathPrefix/sbt-dev"

      lazy val sbtRelease: MavenRepository = "sbt-release" at s"$jFrogRoot/$pathPrefix/sbt-release"

      lazy val jvmSnapshot: MavenRepository = "jvm-snapshot" at s"$jFrogRoot/$pathPrefix/jvm-snapshots"

      lazy val jvm: MavenRepository = "jvm-release" at s"$jFrogRoot/$pathPrefix/jvm"
    }
  }

}
