[![](https://jitpack.io/v/exceljava/com4j.svg)](https://jitpack.io/#exceljava/com4j)

About
=====

This is a fork of [kohsuke/com4j](https://github.com/kohsuke/com4j) and contains some changes necessary for calling the Excel COM API from Java. All changes are raised as a PR in the original repo, as the intention is that this repo is only needed temporarily.

Using com4j
===========

[Download com4j](https://github.com/exceljava/com4j/releases) or [access it from jitpack.io](https://jitpack.io/#exceljava/com4j)

To add to a Maven project, add the following to your pom.xml (using the lastest release name for the artifactId)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.exceljava.com4j</groupId>
    <artifactId>com4j</artifactId>
    <version>release-20180529</version>
</dependency>
```

Building com4j
==============
Building com4j is divided into two parts. Native and Java.

If you are only interested in hacking Java side of com4j, we made it so that you don't have to have the whole native build environment. For this purpose, we commit *.dll and *.pdb into Git, which are the output of the native builds.

To build the native side of com4j, you need:

- checkout git submodules that are linked
- Visual Studio 2008
    - From options menu, add JDK's JNI include/lib folders to your environment.
      (Do not add those to the project since these values aren't portable.)

Run your "Visual Studio command prompt" and execute ant from the `native` directory.


javah
-----
If you change the Java classes that define native methods, be sure to execute `native/run_javah.bat` to keep header files in sync
