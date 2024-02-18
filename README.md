# c3p0

c3p0 is a mature, highly concurrent JDBC Connection pooling library, with
support for caching and reuse of `PreparedStatement` objects.

c3p0 is available as managed dependency on Maven Central,
<code>[groupId: com.mchange, artifactId: c3p0]</code> For available versions, look [here](https://oss.sonatype.org/content/repositories/releases/com/mchange/c3p0/).

Please see the [documentation](http://www.mchange.com/projects/c3p0/) for more.

From the current *development snapshot*, here is the latest [CHANGELOG](CHANGELOG).

Please address comments and questions to the [library author](mailto:swaldman@mchange.com), although keep in mind he is an abysmal correspondent and basically an asshole. 

Despite that, your feedback is very much appreciated. Pull requests are gratefully accepted. You may also open issues.

Thank you for your interest in c3p0. I do hope that you find it useful!

### Building c3p0

c3p0 relies on the excellent build tool [`mill`](https://mill-build.com/).

Install `mill`. Then, within this repository direcory, run

```plaintext
$ mill jar
```

You'll find the raw as library `out/jar.dest/out.jar`.

If you maintain a local ivy repository, You can customize `publishVersion` in [`build.sc`](build.sc), then run

```plaintext
$ mill publishLocal
```

For now (v0.10.x), c3p0 is built under a Java 11 VM, targetting JDK 7 classfiles for continued compatibility with legacy apps.

### License

c3p0 is licensed under [LGPL v.2.1](LICENSE-LGPL) or [EPL v.1.0](LICENSE-EPL), at your option.

---

**Note:** c3p0 has had a good experience with reporting of a security vulnerability via Sonatype's _Central Security Project_.
If you find a c3p0 security issue, do consider reporting it via https://hackerone.com/central-security-project





