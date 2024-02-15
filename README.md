# c3p0

c3p0 is a mature, highly concurrent JDBC Connection pooling library, with
support for caching and reuse of PreparedStatements. It is licensed under 
[LGPL v.2.1](LICENSE-LGPL)
or [EPL v.1.0](LICENSE-EPL), 
at your option.

c3p0 is now maintained on [github](https://github.com/swaldman/c3p0).

c3p0 is available as managed dependency on [Sonatype's open-source software repostory](https://oss.sonatype.org/content/repositories/releases/), 
under <code>[groupId: com.mchange, artifactId: c3p0]</code> For available values of <code>version</code>, look [here](https://oss.sonatype.org/content/repositories/releases/com/mchange/c3p0/).

Documentation is [here](http://www.mchange.com/projects/c3p0/).

From the current *development snapshot*, here are the latest [CHANGELOG](CHANGELOG)

Please address comments and questions to the [library author](mailto:swaldman@mchange.com), although keep in mind he is an abysmal correspondent and basically an asshole. Despite that, your feedback is very much appreciated. You may also open issues on github and/or sourceforge.

Thank you for your interest in c3p0. I do hope that you find it useful!

### Building c3p0

c3p0 relies on the excellent build tool [`mill`](https://mill-build.com/).

Install `mill`, then run

```plaintext
$ mill jar
```

You'll find the raw as library `out/out.jar`.

If you maintain a local ivy repository, You can customize `publishVersion` in [`build.sc`](build.sc), then run

```plaintext
$ mill publishLocal
```

---

**Note:** c3p0 has had a good experience with reporting of a security vulnerability via Sonatype's _Central Security Project_.
If you find a c3p0 security issue, do consider reporting it via https://hackerone.com/central-security-project





