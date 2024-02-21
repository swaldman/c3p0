import mill._, scalalib._

object Dependency {
  val MchangeCommonsJava = ivy"com.mchange:mchange-commons-java:0.3.0-SNAPSHOT"
}

object millbuild extends MillBuildRootModule {
  override def ivyDeps = T{
    super.ivyDeps() ++ Agg(Dependency.MchangeCommonsJava)
  }
}

