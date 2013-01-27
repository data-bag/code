About this repository
=====================

This repository contains the source code of the *__Data-bag__ file
synchronization tool*. Its top-level components are:

        src/           Data-bag source files.
        lib/           Run-time libraries used by Data-bag.
        LICENSE/       Legal documents with the project's licensing terms.
        NOTICE.md      A summary of licenses that apply to Data-bag with
                        references to detailed legal documents.
        build.xml      Configuration file for the tool (ANT) that builds the
                        project's executable and other artifacts.
        .classpath     Eclipse configuration file for the project.
        .project       Eclipse configuration file for the project.
        docs/          The project's documentation.
        test/          Source files of the project's regression tests.

Building a *Data-bag* executable
================================ 

To build a *Data-bag* executable from these sources, you need:

   - A Java SDK, also known as JDK, Standard Edition (SE), version 6 or
   later, available from OpenJDK <http://openjdk.java.net/> or Oracle
   <http://www.oracle.com/technetwork/java/javase/downloads/index.html>
   
   Even though a Java runtime may already be installed on your machine
   (check that by running `java --version`), the build will fail if you
   don't have a complete JDK (check that by running `javac`).

   - Apache ANT version 1.7.0 or newer, available from the Apache Software
   Foundation <http://ant.apache.org/>.

We further assume that you have properly configured `JAVA_HOME` and `ANT_HOME`
environment variables and that you can run both `ant` and `java` without
prefixes on the command line. You may want to tweak the following commands
if you have a different configuration.

To build a *Data-bag* executable, change dir to the working copy of this
repository (_working tree_), and run ANT:

        ant

The result is a JAR file `databag.jar` with statically-linked runtime
libraries.


Running *Data-bag*
==================

As of now, the only interface to *Data-bag* is command line. The
statically-linked `databag.jar` built using the above instructions does not
require any additional Java configuration to run. For example, to test a
*Data-bag* binary built at the root of your working tree, run:

        java -jar databag.jar -?

You should see verbose output explaining the tool's command line syntax and
describing most of its options. Please refer to the
[*Data-bag* manual](docs/manual.md) for further instructions.

Hacking *Data-bag*
==================

Once you have a clone of this repository, you can import it into Eclipse
using the enclosed `.classpath` and `.project` files. You may have to update
source and javadoc locations for the run-time libraries. To compile and
run tests for the project, you'll have to download *JUnit 4.5* or newer from
<http://www.junit.org> and add them as a library to your build path. To
generate javadoc for the project, use ANT:

        ant javadoc

Then, navigate to file `index.html` in the `javadoc` directory of your working
tree to browse the _javadoc_.

Contacting the project team
===========================

You can send a message to the project's team via the
[Contact page](http://www.livitski.name/contact) at <http://www.livitski.name/>
or via *GitHub*. We will be glad to hear from you!
