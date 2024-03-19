import mill._, scalalib._

// Since dependency mchange-commons-java is shared with the main build,
// we import it from a common definition
import $file.McjInfo

object Dependency {
  val MchangeCommonsJava = McjInfo.Ivy
}

object millbuild extends MillBuildRootModule {
  override def ivyDeps = T{
    super.ivyDeps() ++ Agg(Dependency.MchangeCommonsJava)
  }
}

