import $meta._

import mill._
import mill.scalalib._
import mill.scalalib.publish._

object Dependency {
  val MchangeCommonsJava = ivy"com.mchange:mchange-commons-java:0.2.20"
}

object c3p0 extends RootModule with JavaModule with PublishModule {
  trait Gen extends JavaModule {
    override def ivyDeps = T{
      super.ivyDeps() ++ Agg(Dependency.MchangeCommonsJava)
    }
    override def forkArgs = T{Seq("-Dcom.mchange.v2.log.MLog=com.mchange.v2.log.FallbackMLog")}
  }
  object bean  extends Gen {}

  object proxy extends Gen {
    override def sources = T { super.sources() :+ PathRef( os.pwd / "src-proxy-interface" ) }
    override def mainClass = T { Some("com.mchange.v2.c3p0.codegen.JdbcProxyGenerator") }
  }

  val Debug = true
  val Trace = 5

  override def sources = T { super.sources() :+ PathRef( os.pwd / "src-proxy-interface" ) }

  override def ivyDeps = T{
    super.ivyDeps() ++ Agg(Dependency.MchangeCommonsJava)
  }
  def genDebugSources = T.persistent {
    com.mchange.v2.debug.DebugGen.main(Array("--packages=com.mchange",s"--codebase=${os.pwd}/src",s"--outputbase=${T.dest}","--recursive",s"--debug=${Debug}",s"--trace=${Trace}"))
    PathRef(T.dest)
  }
  def genC3P0SubstitutionsSource = T.persistent {
    val version = publishVersion()
    val text =
      s"""|package com.mchange.v2.c3p0.subst;
          |
          |public final class C3P0Substitutions
          |{
          |    public final static String VERSION    = "${version}";
          |    public final static String DEBUG      = "${Debug}";
          |    public final static String TRACE      = "${Trace}";
          |    public final static String TIMESTAMP  = "${java.time.Instant.now}";
          |
          |    private C3P0Substitutions()
          |    {}
          |}
          |""".stripMargin
    val path = T.dest / "com" / "mchange" / "v2" / "c3p0" / "subst" / "C3P0Substitutions.java"
    os.write( path, data = text, createFolders = true )
    PathRef(T.dest)
  }
  def beangenGeneratedSourceDir = T{ T.dest }
  def beangen = T {
    bean.run(
      T.task(Args("beangen/com/mchange/v2/c3p0/impl",(beangenGeneratedSourceDir()/"com"/"mchange"/"v2"/"c3p0"/"impl").toString()))
    )()
    PathRef(beangenGeneratedSourceDir())
  }
  def proxygenGeneratedSourceDir = T{ T.dest }
  def proxygen = T {
    proxy.run(T.task(Args(proxygenGeneratedSourceDir().toString)))()
    PathRef(proxygenGeneratedSourceDir())
  }

  override def generatedSources = T {
    super.generatedSources() ++ Agg(genDebugSources(),genC3P0SubstitutionsSource(),beangen(),proxygen())
  }

  override def artifactName = T{"c3p0"}
  override def publishVersion = T{"0.10.0-pre1-SNAPSHOT"}

  override def pomSettings = T {
    PomSettings(
      description = "A mature JDBC3+ Connection pooling library",
      organization = "com.mchange",
      url = "https://www.mchange.com/projects/c3p0",
      licenses = Seq(License.`LGPL-2.1-only`,License.`EPL-1.0`),
      versionControl = VersionControl.github("swaldman", "c3p0"),
      developers = Seq(
        Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
  }
}
