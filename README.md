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
>
> c3p0-0.13.0 eliminates from c3p0's `CLASSPATH`
> the `javax.naming.Reference` &rarr; Java serialization pipeline that makes possible the mischief.
>
> c3p0-0.14.0 further hardens JNDI and serialization-related utilities, and prevents attacks that
> rely on JavaBeans-related libraries triggering `getConnection()` or `getPooledConnection()`
> on maliciously constructed `DataSource` instances.
>
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

For now (v0.15.0), c3p0 is built under a Java 11 VM, targetting JDK 7 classfiles for continued compatibility with legacy apps.

c3p0 relies on the excellent build tool [`mill`](https://mill-build.com/).

Install `mill`. Then, within this repository directory, run

```plaintext
$ ./mill jar
```

You'll find the raw as library `out/jar.dest/out.jar`.

If you maintain a local ivy repository, You can customize `publishVersion` in [`build.mill`](build.mill), then run

```plaintext
$ ./mill publishLocal
```

To build the documentation

```plaintext
$ ./mill doc.docroot
```

You can then open in your browser `out/doc/docroot.dest/index.html`

### Build freshness

Much of c3p0 is generated during the build: a `Debug` class per package beneath `src`, JavaBean base
classes from the `beangen-xml` files, explicit `BeanInfo`s reflected off those compiled beans, and the
JDBC proxies. You should never need `mill clean` to get a correct jar out of that. To verify it:

```plaintext
$ ./checkBuildFreshness
```

It perturbs a build input of each kind — a new package under `src`, a new property in a
`beangen-xml`, a change to generator code — and checks that an incremental build afterwards matches
a clean build of the same sources, that the perturbation really did move the output, and that
reverting restores the artifacts bit for bit. It takes well under a minute, exits nonzero on
failure, and leaves your working tree as it found it.

### Reproducible builds

c3p0 supports reproducible builds of its binary and source jar files.
(It does not yet support reproducible builds of doc jars.)

To prevent everchanging timestamps, set the environment variable
[`SOURCE_DATE_EPOCH`](https://reproducible-builds.org/docs/source-date-epoch/)
when building.

The build reads `SOURCE_DATE_EPOCH` from the environment of the `mill` command you invoke, and
treats it as a build input, so changing it is itself a reason to regenerate. Neither the `-i` flag
nor a clean build is required. So, for example

```plaintext
$ export SOURCE_DATE_EPOCH=1234567890
$ ./mill jar
$ ./mill sourceJar
```

The files `out/jar.dest/out.jar` and `out/sourceJar.dest/out.jar` will have been deterministically and reproducibly built.

### Testing c3p0

By default the tests expect to find a database at `jdbc:postgresql://localhost:5432/c3p0`.
As you can see, I usually test against a local postgres database. You can change this by
setting `C3P0_TEST_JDBC_URL` in the environment — see [Test configuration](#test-configuration) below.

c3p0's testing is, um, embarrassingly informal. There is a junit test suite, but it covers a
very small fraction of c3p0 functionality. To run that, it's just

```plaintext
$ mill test
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

Tests are configured by a `c3p0.properties` file and by environment variables.
To play with different configurations, edit [`test/resources-local/c3p0.properties`](test/resources-local/c3p0.properties).

Test runs are configured from the environment rather than by editing [`build.mill`](build.mill).
Since these affect only how tests run, switching between setups rebuilds nothing:

| Variable | Effect |
| --- | --- |
| `C3P0_TEST_CONFIG` | `local` (the default) or `rough`, selecting which `c3p0.properties` the tests see |
| `C3P0_TEST_JDBC_URL` | overrides the default URL, `jdbc:postgresql://localhost:5432/c3p0` |
| `C3P0_TEST_USER` | sets `-Dc3p0.user`, when present |
| `C3P0_TEST_PASSWORD` | sets `-Dc3p0.password`, when present (may be empty) |
| `C3P0_TEST_JVM_ARGS` | whitespace-separated extra JVM args, appended last; may define c3p0 properties such as `-Dc3p0.maxStatements=100` |

Sometimes you want to put the library through its paces with pathological configuration.
A baseline pathological configuration is defined in [`test/resources-local-rough/c3p0.properties`](test/resources-local-rough/c3p0.properties).
To give this effect:

```bash
$ C3P0_TEST_CONFIG=rough mill test.c3p0Load
```

Then of course you can edit [`test/resources-local-rough/c3p0.properties`](test/resources-local-rough/c3p0.properties).

Test environment changes that, in versions of c3p0 prior to 0.15.0, you would have uncommented in the `forkArgs()` task of `build.mill` now go in `C3P0_TEST_JVM_ARGS`.
For example:

```bash
$ C3P0_TEST_JVM_ARGS='-ea -Dc3p0.maxStatements=100' mill test.c3p0PSLoad
```

Running against hsqldb rather than postgres additionally requires uncommenting `Dependency.Hsqldb`
in the test module's `mvnDeps`. That one stays a source edit deliberately: the test module is
published, and an environment-conditional dependency would make the published pom depend on the
environment it happened to be published from.

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
* `com.mchange.v2.c3p0.test.StatementStateTest`

### Developer documentation

In addition to this `README.md`, please see...

* [Adding properties](adding-properties.md)
* [The c3p0 Statement Cache: structures and invariants](src/com/mchange/v2/c3p0/stmt/statement-cache-internals.md)
* [The statement cache auditor, and the harnesses around it](test/src/com/mchange/v2/c3p0/stmt/statement-cache-auditor.md)

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
