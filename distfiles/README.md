About this repository
=====================

This repository contains the binary distribution of the *__Data-bag__ file
synchronization tool*. Its top-level components are:

		databag.jar    The data-bag executable.
        docs/          Data-bag user's guide.
        LICENSE/       Legal documents with the project's licensing terms.
        NOTICE.html    A summary of licenses that apply to Data-bag with
                        references to detailed legal documents.
        README.html    This file with the distribution's summary.

Prerequisites
============= 

Enclosed _data-bag_ executable is a single file named `databag.jar`
that you can run on different machines. A machine can run that file if it has a
<dfn>Java runtime</dfn> installed. The tool is compatible with:

 - [OpenJDK Runtime Environment for JDK 6 or 7][openjdk] that
 may be installed on, or available as an option for your system; _or_

 - [Java runtime version 5, 6, or 7 (JRE 1.5+)][jre] downloadable
 from Oracle


Running *Data-bag*
==================

As of now, the only interface to *Data-bag* is command line. The `databag.jar`
executable does not require any additional Java configuration to run. For
example, if `databag.jar` is in your current directory and your shell can find
the Java runtime executable on its search path,  you can run:

        java -jar databag.jar -?

You should see verbose output explaining the tool's command line syntax and
describing most of its options. Please refer to the
[*Data-bag* manual](docs/manual.html)
for further information.

Contacting the project's team
=============================

You can send a message to the project's team via the
[Contact page](http://www.livitski.name/contact) at <http://www.livitski.name/>
or via *GitHub*. We will be glad to hear from you!


 [openjdk]: http://openjdk.java.net/install/index.html "OpenJDK packages"
 [jre]: http://java.com/en/download/index.jsp "Oracle Java Runtime downloads"
