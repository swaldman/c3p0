import $meta._

import mill._
import mill.api.Result
import mill.scalalib._
import mill.scalalib.publish._
import mill.util.Jvm

object Dependency {
  val MchangeCommonsJava = ivy"com.mchange:mchange-commons-java:0.2.20"
  val JUnit = ivy"org.junit.vintage:junit-vintage-engine:5.10.2"
  val PgJdbc = ivy"org.postgresql:postgresql:42.6.0"
}

object c3p0 extends RootModule with JavaModule with PublishModule {
  val organization = "com.mchange"
  override def artifactName = T{"c3p0"}
  override def publishVersion = T{"0.10.0-pre2-SNAPSHOT"}

  trait Gen extends JavaModule {
    def runIf(conditionAndArgs: Task[Args]): Command[Unit] = T.command {
      val caa = conditionAndArgs().value
      val condition = caa.head
      // println(s"!!!!! $condition")
      val args = caa.tail
      val truthyCondition = "true".equalsIgnoreCase(condition) || "t".equalsIgnoreCase(condition)
      if (truthyCondition) {
        try Result.Success(
            Jvm.runSubprocess(
              finalMainClass(),
              runClasspath().map(_.path),
              forkArgs(),
              forkEnv(),
              args,
              workingDir = forkWorkingDir(),
              useCpPassingJar = runUseArgsFile()
            )
          )
        catch {
          case e: Exception =>
            Result.Failure("subprocess failed")
        }
      }
      else {
        Result.Success( () )
      }
    }
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
  def buildModTime : T[Long] = T.input {
    val out = math.max( os.mtime( os.pwd / "build.sc" ), os.mtime( os.pwd / "mill-build" / "build.sc" ) )
    //println( s"buildModTime: $out" )
    out
  }
  private def recursiveDirModTime( dir : os.Path ) : Long = {
    if (!os.exists(dir)) {
      -1L
    }
    else {
      val mtimes = os.walk.attrs(dir, includeTarget = true).map( _._2.mtime.toMillis )
      if (mtimes.size < 2) -1L else mtimes.max // if there's only one mtime, it's the empty target
    }
  }
  def genDebugSources = T { // mill correctly caches these with no work on our part
    com.mchange.v2.debug.DebugGen.main(Array("--packages=com.mchange",s"--codebase=${os.pwd}/src",s"--outputbase=${T.dest}","--recursive",s"--debug=${Debug}",s"--trace=${Trace}"))
    PathRef(T.dest)
  }
  def genC3P0SubstitutionsSource = T.persistent {
    val version = publishVersion()
    val bmt = buildModTime()
    val dmt = recursiveDirModTime( T.dest )
    if ( bmt > dmt ) {
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
      System.err.println("Regenerating C3P0Substitutions.java")
      os.write.over( path, data = text, createFolders = true )
    }
    PathRef(T.dest)
  }
  def beangenGeneratedSourceDir = T.persistent { T.dest }
  def beangen = T {
    val realDest = beangenGeneratedSourceDir()
    bean.runIf(
      T.task(
        Args(
          (Seq(buildModTime(),recursiveDirModTime(os.pwd/"bean"/"beangen"),recursiveDirModTime(os.pwd/"bean"/"src")).max > recursiveDirModTime(beangenGeneratedSourceDir())).toString,
          "bean/beangen/com/mchange/v2/c3p0/impl",
          (beangenGeneratedSourceDir()/"com"/"mchange"/"v2"/"c3p0"/"impl").toString()
        )
      )
    )()
    PathRef(realDest)
  }
  def proxygenGeneratedSourceDir = T.persistent { T.dest }
  def proxygen = T {
    proxy.runIf(
      T.task(
        Args(
          (math.max(buildModTime(),recursiveDirModTime(os.pwd/"proxy"/"src")) > recursiveDirModTime(proxygenGeneratedSourceDir())).toString,
          proxygenGeneratedSourceDir().toString
        )
      )
    )()
    PathRef(proxygenGeneratedSourceDir())
  }

  override def generatedSources = T {
    super.generatedSources() ++ Agg(genDebugSources(),genC3P0SubstitutionsSource(),beangen(),proxygen())
  }

  // we are currently building in Java 11, but releasing Java 6 compatible class files
  // for users of smaller JDBC subsets
  val JvmCompatVersion = "6"

  // we don't use the newer --release flag, we intentionally compile against newer API, so newer API is supported
  // but old JVMs that hit it wil generate NoSuchMethodError or similar at runtime
  override def javacOptions = Seq("-source",JvmCompatVersion,"-target",JvmCompatVersion)

  object test extends JavaModule with TestModule.Junit5 {
    override def moduleDeps = Seq(c3p0)

    override def ivyDeps = T{
      super.ivyDeps() ++ Agg(Dependency.JUnit,Dependency.PgJdbc)
    }
    override def forkArgs = T {
      "-Dc3p0.jdbcUrl=jdbc:postgresql://localhost:5432/c3p0" ::
      //"-Dcom.sun.management.jmxremote.port=38383" ::
      //"-Dcom.sun.management.jmxremote.authenticate=false" ::
      //"-Dcom.sun.management.jmxremote.ssl=false" ::
      //"-server" ::
      //"-Xrunhprof:cpu=times,file=/tmp/java.hprof,doe=y,format=a" ::
      //"-Xprof" ::
      //"-Xrunhprof:file=/tmp/java.hprof,doe=y,format=b" ::
      //"-verbose:class"
      //"-ea" ::
      Nil
    }
    def c3p0Benchmark = T {
      this.runMain("com.mchange.v2.c3p0.test.C3P0BenchmarkApp")()
    }
    def c3p0Stats = T {
      this.runMain("com.mchange.v2.c3p0.test.StatsTest")()
    }
    def c3p0Proxywrapper = T {
      this.runMain("com.mchange.v2.c3p0.test.ProxyWrappersTest")()
    }
    def c3p0RawConnectionOp = T {
      this.runMain("com.mchange.v2.c3p0.test.RawConnectionOpTest")()
    }
    def c3p0Load = T {
      this.runMain("com.mchange.v2.c3p0.test.LoadPoolBackedDataSource")()
    }
    def c3p0PSLoad = T {
      this.runMain("com.mchange.v2.c3p0.test.PSLoadPoolBackedDataSource")()
    }
    def c3p0InterruptedBatch = T {
      this.runMain("com.mchange.v2.c3p0.test.InterruptedBatchTest")()
    }
    def c3p0Dispersion = T {
      this.runMain("com.mchange.v2.c3p0.test.ConnectionDispersionTest")()
    }
    def c3p0OneThreadRepeat = T {
      this.runMain("com.mchange.v2.c3p0.test.OneThreadRepeatedInsertOrQueryTest")()
    }
    def c3p0RefSer = T {
      this.runMain("com.mchange.v2.c3p0.test.TestRefSerStuff")
    }
    def c3p0JavaBeanRef = T {
      this.runMain("com.mchange.v2.c3p0.test.JavaBeanRefTest")
    }
  }

  override def pomSettings = T {
    PomSettings(
      description = "A mature JDBC3+ Connection pooling library",
      organization = organization,
      url = "https://www.mchange.com/projects/c3p0",
      licenses = Seq(License.`LGPL-2.1-only`,License.`EPL-1.0`),
      versionControl = VersionControl.github("swaldman", "c3p0"),
      developers = Seq(
        Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
  }
}
