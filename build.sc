import $meta._

import mill._
import mill.api.{JarManifest,Result}
import mill.scalalib._
import mill.scalalib.publish._
import mill.util.Jvm

import scala.annotation.tailrec

object Dependency {
  // mill-build/build.sc also has a mchange-commons-java dependency. Best to keep them in sync
  val MchangeCommonsJavaVersion = "0.3.0-SNAPSHOT"

  val MchangeCommonsJava = ivy"com.mchange:mchange-commons-java:${MchangeCommonsJavaVersion}"
  val JUnit = ivy"org.junit.vintage:junit-vintage-engine:5.10.2"
  val PgJdbc = ivy"org.postgresql:postgresql:42.6.0"
}

object c3p0 extends RootModule with JavaModule with PublishModule {
  val organization = "com.mchange"
  override def artifactName = T{"c3p0"}
  override def publishVersion = T{"0.10.0-pre4-SNAPSHOT"}

  // we are currently building in Java 11, but releasing Java 7 compatible class files
  // for users of smaller JDBC subsets
  val JvmCompatVersion = "7"

  require( sys.props("java.runtime.version").startsWith("11"), s"Bad build JVM: ${sys.props("java.runtime.version")} -- We currently expect to build under Java 11. (We generate Java $JvmCompatVersion compatible source files.)" )

  // we don't use the newer --release flag, we intentionally compile against newer API, so newer API is supported
  // but old JVMs that hit it wil generate NoSuchMethodError or similar at runtime
  override def javacOptions = Seq("-source",JvmCompatVersion,"-target",JvmCompatVersion)

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

  private val WritePermissions : os.PermSet = "-w--w--w-"

  def readOnly( perms : os.PermSet ) : os.PermSet = perms -- WritePermissions

  // i intended to make generated source dir contents read-only, but this prevents the build from
  // overwriting when necessary. so this is not currently in use.
  //
  // i'd have to add logic to restore writability during rebuilds, cleans,
  // etc which seems maybe too complicated.
  def makeDirContentsReadOnly( dir : os.Path ) = os.walk(dir).foreach( p => os.perms.set(p, readOnly(os.perms(p))) )

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
    val realDest = proxygenGeneratedSourceDir()
    proxy.runIf(
      T.task(
        Args(
          (math.max(buildModTime(),recursiveDirModTime(os.pwd/"proxy"/"src")) > recursiveDirModTime(proxygenGeneratedSourceDir())).toString,
          proxygenGeneratedSourceDir().toString
        )
      )
    )()
    PathRef(realDest)
  }

  override def generatedSources = T {
    super.generatedSources() ++ Agg(genDebugSources(),genC3P0SubstitutionsSource(),beangen(),proxygen())
  }

  override def manifest = T {
    val mainTups = JarManifest.MillDefault.main + Tuple2("Automatic-Module-Name","com.mchange.v2.c3p0")
    JarManifest( main = mainTups )
  }

  def allLicenses : T[Seq[PathRef]] = T {
    (os.pwd / "LICENSE" :: os.pwd / "LICENSE-LGPL" :: os.pwd / "LICENSE-EPL" :: Nil).map( PathRef(_) )
  }

  // is the same manifest good?
  // some users want licenses in the source jars... https://github.com/swaldman/c3p0/issues/167
  def sourceJar: T[PathRef] = T {
    Jvm.createJar(
      (allSources() ++ resources() ++ compileResources() ++ allLicenses()).map(_.path).filter(os.exists),
      manifest()
    )
  }

  object doc extends JavaModule
  {
    val StagingDir = os.Path("/Users/swaldman/development/gitproj/www.mchange.com/projects/c3p0-versions")

    def replacements : T[Map[String,String]] = T {
        import java.time.ZonedDateTime
        Map(
          "@c3p0.version@" -> c3p0.publishVersion(),
          "@mchange-commons-java.version@" -> Dependency.MchangeCommonsJavaVersion,
          "@c3p0.copyright.year@" -> ZonedDateTime.now().getYear.toString
        );
    }

    @tailrec
    def replaceAll( replacements : List[(String,String)], template : String ) : String = {
      replacements match {
      	case head :: tail => replaceAll(tail, template.replace(head._1, head._2))
        case Nil          => template
      }
    }

    def docsrc : T[PathRef] = T.source { millSourcePath / "docsrc" }

    def docroot : T[PathRef] = T {
      val javadocRoot = c3p0.docJar().path / os.up / "javadoc"
      val replaceMap = replacements()
      val docsrcDir = docsrc().path
      val sourcePaths = os.walk(docsrcDir)
      def destPath( sourcePath : os.Path ) = T.dest / sourcePath.relativeTo(docsrcDir)
      sourcePaths.foreach { sp =>
        if (sp.toString.endsWith(".html")) {
          val replaced = replaceAll( replaceMap.toList, os.read(sp) )
          os.write(destPath(sp), replaced)
        }
        else
          os.copy.over( from=sp, to=destPath(sp) )
      }
      os.copy.over( from = javadocRoot, to = T.dest / "apidocs" )
      PathRef(T.dest)
    }

    def stage : T[Unit] = T {
      os.copy.over( from = docroot().path, to = StagingDir / ("c3p0-" + c3p0.publishVersion()) )
    }
  }

  // for now at least, I don't mean to publish tests as a public library,
  // but I do want to publish them locally so I have them when working on related
  // libraries
  object test extends JavaModule with TestModule.Junit5 with PublishModule {
    override def moduleDeps = Seq(c3p0)

    override def artifactName = T{"c3p0-test"}
    override def publishVersion = T{ c3p0.publishVersion() }

    /**
      * A place for resources we want seen by local (test) runs, but
      * should not be published (even locally) into the c3p0-test jar,
      * so that when we run tests from elsewhere, they are not polluted
      * by settings in these resources
      */
    def localResources : T[Seq[PathRef]] = T.sources { millSourcePath / "resources-local" }

    override def runClasspath : T[Seq[PathRef]] = T{
      super.runClasspath() ++ localResources()
    }

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
    override def pomSettings = T {
      PomSettings(
        description = "Tests of c3p0, a mature JDBC3+ Connection pooling library",
        organization = organization,
        url = "https://www.mchange.com/projects/c3p0",
        licenses = Seq(License.`LGPL-2.1-or-later`,License.`EPL-1.0`),
        versionControl = VersionControl.github("swaldman", "c3p0"),
        developers = Seq(
          Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
        )
      )
    }
  }

  override def pomSettings = T {
    PomSettings(
      description = "A mature JDBC3+ Connection pooling library",
      organization = organization,
      url = "https://www.mchange.com/projects/c3p0",
      licenses = Seq(License.`LGPL-2.1-or-later`,License.`EPL-1.0`),
      versionControl = VersionControl.github("swaldman", "c3p0"),
      developers = Seq(
        Developer("swaldman", "Steve Waldman", "https://github.com/swaldman")
      )
    )
  }
}
