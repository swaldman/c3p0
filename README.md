# c3p0

```plaintext
 _____________________________________
   .................................
   ..(c3p0).........................
   ............a.........fresh......
   ....coat.......of........stucco..
   .........over.......that.....old.
   ...jdbc........driver............
   .................................
 -------------------------------------
```

> [!WARNING]
> c3p0 and its dependencies have been used as [deserialization gadgets](https://medium.com/@dub-flow/deserialization-what-the-heck-actually-is-a-gadget-chain-1ea35e32df69).
> Please see [the docs](https://www.mchange.com/projects/c3p0/#security-note) for more information.
> The vulnerabilities that enable this are addressed as of c3p0-0.12.0.
> Please update your applications!

c3p0 is a mature, highly concurrent JDBC Connection pooling library, with
support for caching and reuse of `PreparedStatement` objects.

c3p0 is available as managed dependency on Maven Central,
<code>[groupId: com.mchange, artifactId: c3p0]</code> For available versions, look [here](https://oss.sonatype.org/content/repositories/releases/com/mchange/c3p0/).

Please see the [documentation](http://www.mchange.com/projects/c3p0/) for more.

From the current *development snapshot*, here is the latest [CHANGELOG](CHANGELOG).

Please address comments and questions to the [library author](mailto:swaldman@mchange.com).

However, please keep in mind he is an abysmal correspondent and basically an asshole. 

Despite that, your feedback is very much appreciated. Pull requests are gratefully accepted. You may also open issues.

Thank you for your interest in c3p0. I do hope that you find it useful!

### Building c3p0

For now (v0.12.0), c3p0 is built under a Java 11 VM, targetting JDK 7 classfiles for continued compatibility with legacy apps.

c3p0 relies on the excellent build tool [`mill`](https://mill-build.com/).

Install `mill`. Then, within this repository direcory, run

```plaintext
$ mill jar
```

You'll find the raw as library `out/jar.dest/out.jar`.

If you maintain a local ivy repository, You can customize `publishVersion` in [`build.mill`](build.mill), then run

```plaintext
$ mill publishLocal
```

To build the documentation

```plaintext
$ mill doc.docroot
```

You can then open in your browser `out/doc/docroot.dest/index.html`

### Testing c3p0

By default the tests expect to find a database at `jdbc:postgresql://localhost:5432/c3p0`.
As you can see, I usually test against a local postgres database. You can change this in 
the `forkArgs` function of the `test` module, in [`build.mill`](build.mill).

c3p0's testing is, um, embarrassingly informal. There is a junit test suite, but it covers a
very small fraction of c3p0 functionality. To run that, it's just

```plaintext
$ mill test.test
```

Mostly c3p0 is tested by running a few test applications, and varying config _ad hoc_ to see how things work.

_If you think c3p0 could/should be tested more professionally and automatically, me too! I'd love a pull request._

[`build.mill`](build.mill) contains a lot of test applications, but the most important are

```plaintext
$ mill test.c3p0Benchmark
```

This is c3p0 most basic, common, test-of-first-resort.
It runs through and times a bunch of different c3p0 operations, and puts the library through pretty good exercise

```plaintext
$ mill test.c3p0Load
```

This one puts c3p0 under load of a 100 thread performing 1000 database operations each,
then terminates.

```plaintext
$ mill test.c3p0PSLoad
```

This one puts c3p0 under load of a 100 thread performing database operations indefinitely.
It uses `PreparedStatement` for its database operations, so is a good way of exercising the
statement cache.

#### Test configuration

You can observe (most of) the config of your c3p0 `DataSource` when you test, because c3p0 logs it at `INFO`
upon the first `Connection` checkout attempt. When testing, verify that you are working with the configuration
you expect!

Tests are configured by command-line arguments and by a `c3p0.properties` file.
To play with different configurations, edit [`test/resources-local/c3p0.properties`](test/resources-local/c3p0.properties).
Also check the `forkArgs()` method in [`build.mill`](build.mill)

Sometimes you want to put the library through its paces with pathological configuration.
A baseline pathological configuration is defined in [`test/resources-local-rough/c3p0.properties`](test/resources-local-rough/c3p0.properties).

To give this effect, temporarily edit [`build.mill`](build.mill):

```scala
    override def runClasspath : T[Seq[PathRef]] = T{
      super.runClasspath() ++ localResources()
      // super.runClasspath() ++ localResourcesRough()
    }
```

* Comment out `super.runClasspath() ++ localResources()`
* Uncomment in `super.runClasspath() ++ localResourcesRough()`

Then of course you can edit [`test/resources-local-rough/c3p0.properties`](test/resources-local-rough/c3p0.properties).

#### Test logging

Often you will want to focus logging on a class or feature you are testing. By default, c3p0 tests
are configured to use `java.util.logging.`, and be configured by the file [`test/conf-logging/logging.properties`](test/conf-logging/logging.properties).

Of course you can change the config (in `c3p0.properties`) to use another logging library if you'd like,
but you may need to modify the build to bring third-party logging libraries in, and configure those libraries
in their own ways.

#### Testing under other JVM versions

The build insists on a particular JVM version (currently Java 11), but you may want to try tests, with
all the necessary dependencies and the same config, under other JVM versions. To support this,
first, in the build's required JVM version, run

```plaintext
% mill test.printExternalCommandBase
```

That will print a long String, beginning with "java" and typically ending with a very long `CLASSPATH`.

Now you can change your environment (usually reset the `JAVA_HOME` environment variable) so you run a different JVM version.

Paste the long output mill printed for you onto a command line, and then append the fully qualified name of one of c3p0's
test applications, usually one of...

* `com.mchange.v2.c3p0.test.C3P0BenchmarkApp`
* `com.mchange.v2.c3p0.test.LoadPoolBackedDataSource`
* `com.mchange.v2.c3p0.test.PSLoadPoolBackedDataSource`
* `com.mchange.v2.c3p0.test.StatsTest`
* `com.mchange.v2.c3p0.test.ProxyWrappersTest`
* `com.mchange.v2.c3p0.test.RawConnectionOpTest`
* `com.mchange.v2.c3p0.test.InterruptedBatchTest`
* `com.mchange.v2.c3p0.test.ConnectionDispersionTest`
* `com.mchange.v2.c3p0.test.OneThreadRepeatedInsertOrQueryTest`
* `com.mchange.v2.c3p0.test.TestRefSerStuff`
* `com.mchange.v2.c3p0.test.JavaBeanRefTest`
* `com.mchange.v2.c3p0.test.DynamicPreparedStatementTest`


### Building c3p0-loom

Because c3p0 currently builds under Java 11, but c3p0-loom requires Java 21, c3p0 loom is a
[separate project](https://github.com/swaldman/c3p0-loom). 

It is just a parallel mill project.
The instructions above apply (except `c3p0-loom` does not have independent documentation to build).

### License

c3p0 is licensed under [LGPL v.2.1](LICENSE-LGPL) or [EPL v.1.0](LICENSE-EPL), at your option. You may also
opt to license c3p0 under any version of LGPL higher than v.2.1.

---

**Note:** c3p0 has had a good experience with reporting of a security vulnerability via Sonatype's _Central Security Project_.
If you find a c3p0 security issue, do consider reporting it via https://hackerone.com/central-security-project





