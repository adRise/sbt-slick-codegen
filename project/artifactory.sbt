ThisBuild / credentials ++= {
  val logger = streams.value.log
  if (sys.env.contains("ARTIFACTORY_USERNAME")) {
    logger.info("spotted credential in env, will add to credentials")
    Some(
      Credentials(
        "Artifactory Realm",
        "tubins.jfrog.io",
        sys.env.getOrElse("ARTIFACTORY_USERNAME", ""),
        sys.env.getOrElse("ARTIFACTORY_PASSWORD", "")
      )
    )
  } else {
    Credentials.loadCredentials(Path.userHome / ".artifactory" / "credentials") match {
      case Right(credentials: DirectCredentials) =>
        logger.info(s"Using credentials found in the home directory for host ${credentials.host}")
        Some(credentials)
      case Left(err: String) =>
        logger.warn(s"Could not find artifactory credentials in home directory: $err")
        None
    }
  }
}