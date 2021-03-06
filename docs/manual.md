
_Data-bag_ manual
=================

<small>Copyright &copy; 2010-2013, 2016 Stan Livitski. License terms apply.
Please read file [`NOTICE.md`][NOTICE] or browse
<http://www.livitski.name/projects/data-bag/license> for details.</small>

* * *

_This manual is a work in progress._ 

* * *

Contents
--------
<div class="toc0">
<?name.livitski.tools.html.toc version="1.0" outline="h2,h3,h4" linetags="div,li,li" blocktags=",ul.toc1,ul.toc2" ?>
<small>Generated table of contents is placed here</small>
<?name.livitski.tools.html.toc /?>
</div>

What is _data-bag_?
-------------------

_Data-bag_ is a tool that helps keep files consistent across multiple
devices, track the updates, and restore lost or changed files.

It will create
and maintain a database with copies of your files on a shared medium, such as
a USB drive, memory stick, smartphone, tablet, or network server. As long as
you have access to that medium (e.g. carry your phone or memory stick), you
can create up-to-date copies of your files on any device capable of running
_data-bag_. Changes made to a local copy will be stored on the shared
medium next time you synchronize the copy. _Data-bag_ will keep track
of your changes and update other local copies of your files when you synchronize
those copies. It will also store histories of changes to synchronized files,
allowing you to review past changes, resolve conflicting updates, and restore
corrupt or deleted files.

_Data-bag_ user's guide
-----------------------

### Prerequisites

The _data-bag_ executable is usually a single file named `databag.jar`
that you can run on different machines. A machine can run that file if it has a
<dfn>Java runtime</dfn> installed. The tool is compatible with:

 - [OpenJDK Runtime Environment for JDK 6 or 7][openjdk] that
 may be installed on, or available as an option for your system; _or_

 - [Java runtime version 5, 6, or 7 (JRE 1.5+)][jre] downloadable
 from Oracle

Throughout this guide, we assume that your machine runs a (mostly)
[<dfn>POSIX-compliant</dfn>][POSIX] operating system, such as GNU/Linux, BSD,
or Mac OS X. _Data-bag_ is designed to run on various platforms, and
you may be able to use
it on a system that does not comply with [POSIX][] specifications. In
that case you have to adjust the syntax of your commands and file locations
according to the system's conventions, or install a <dfn>POSIX compatibility
kit</dfn>, to be able to follow this guide.

We also assume that you use a single shared medium, mounted at `/mnt/`,
containing a copy of the _data-bag_ executable `databag.jar` and that
your shell can find the Java runtime executable on its search path.
These conditions are not necessary, and _data-bag_ will work in other
environments if you make adjustments to the commands you enter.

### Getting started

To run the standard _data-bag_ executable, use the `-jar` option of
the Java runtime command: 

	$ java -jar /mnt/databag.jar

Initially, there is no [bag][] for _data-bag_ to work with, and the tool
informs you of that and suggests adding `--help` to the command line
to display the usage syntax:

>     Data-bag: file synchronization, backup, and change tracking tool, v.1.07.161026
>     Copyright 2010-2014,2016 Stan Livitski and others.
>     See file "LICENSE/data-bag.md" for applicable terms,
>      http://data-bag.org to learn more about the project.
>
>     Oct 31, 2016 2:54:11 PM name.livitski.databag.cli.Launcher run
>     WARNING: Could not find a bag on "/home/user", giving up.
>     To find out more about data-bag's syntax, run it with --help switch.

To create a [bag][] in the current directory, add the [`--create` option][--create]
to the previous command line. To work with a medium at another location, add the
[`--medium` switch][--medium] (or its shorthand `-d`) with the path to that medium: 

	$ java -jar /mnt/databag.jar -d /mnt --create

Given this command line, _data-bag_ will create a [bag][] and tell you
that there is no local [replica][] defined for your user account:

>     Data-bag: file synchronization, backup, and change tracking tool, v.1.07.161026
>     Copyright 2010-2014,2016 Stan Livitski and others.
>     See file "LICENSE/data-bag.md" for applicable terms,
>      http://data-bag.org to learn more about the project.
> 
>     Oct 31, 2016 7:05:39 PM name.livitski.databag.db.Manager create
>     INFO: Created database jdbc:h2:file:/mnt/databag/databag;LOCK_MODE=1;COMPRESS_LO
>     B=DEFLATE;MAX_LENGTH_INPLACE_LOB=3500
>     Oct 31, 2016 7:05:39 PM name.livitski.databag.cli.Launcher run
>     WARNING: Local replica of shared storage /mnt not found for user@d1.data-bag.org

The [bag][] for shared medium at `/mnt/` is now stored in the `/mnt/databag/`
directory:

	$ ls -l /mnt/databag/

>     total 48
>     -rw-r--r-- 1 user users 47104 2013-01-21 19:05 databag.h2.db

To synchronize files, _data-bag_ needs _both_ a [bag][] and a
[replica][] directory that stores local copies of those files. The [bag][]
remembers locations of its [replicas][] for each machine and user
account combination it is used with. A user can have multiple replicas of the
same bag on any machine, but only one of these replicas is treated as the
[default replica][] for the user's account. The first replica you create on
a machine is designated as its [default replica][] for your account unless
you switch the default to another [replica][].

A directory becomes a [replica][] of your [bag][] when you run _data-bag_
with the [`--local` option][--local] (or its shorthand `-C`) and the
path to that directory. The same option is used to operate on a non-default
[replica][] for your account:

	$ java -jar /mnt/databag.jar -d /mnt/ -C /tmp/demo

If the directory that you designate for a replica does not exist, _data-bag_
will create it for you. If that directory exists, its files are automatically
synchronized, i.e. added to your new [bag][].

> Note that the first several lines of _data-bag_'s output are always
> the same as long as you are using the same executable. These lines contain
> general information about the tool and the project. When running
> _data-bag_ from a script, you may want to omit those lines from
> the output. To do that, add the [`--nobanner` option][--nobanner] to the
> command line.
> In the following examples, we quote the tool's output with that option
> present, even though it is not shown.

>     Jan 21, 2013 8:04:18 PM name.livitski.databag.app.maint.ReplicaManager registerN
>     ewReplica
>     INFO: Created a new replica #1 for user@d1.data-bag.org at /tmp/demo
>     Jan 21, 2013 8:04:18 PM name.livitski.databag.app.sync.SyncService synchronize
>     INFO: Synchronizing replica #1 for user@d1.data-bag.org at /tmp/demo with databa
>     se at /mnt/databag using filter "all" ...

<a name="synchronizing-files"> </a>

### Synchronizing files

Once the [bag][] and the [default replica][] are set up, running
_data-bag_ without arguments in the bag's directory
(or, with the [`-d` option][--medium], in any directory)
will automatically synchronize them:

	$ java -jar /mnt/databag.jar -d /mnt/

>     Jan 21, 2013 8:35:54 PM name.livitski.databag.app.sync.SyncService synchronize
>     INFO: Synchronizing replica #1 for user@d1.data-bag.org at /tmp/demo with databa
>     se at /mnt/databag using filter "all" ...

You can also request synchronization explicitly by entering the
[`--sync` command][--sync] (or its shorthand `-s`). The following command
does the same as above:

	$ java -jar /mnt/databag.jar -d /mnt/ -s

With an explicit `--sync` command, you can also tell _data-bag_ what files it
should synchronize, by appending a [location pattern][pattern] argument to it.

	$ java -jar /mnt/databag.jar -d /mnt/ -s 'doc/*.txt'

This will synchronize only those files in `/tmp/demo/doc` that have suffix
`.txt`.

During synchronization, unmatched files from the [bag][] are copied to the
[replica][] (except for the files deleted in that [replica][]) and unmatched
files from the [replica][] are stored in the [bag][].
Files at the same location relative to both containers are subject to
[version tracking](#version-tracking) and, in some cases,
[conflict resolution](#conflict-resolution).

A typical scenario of everyday _data-bag_ use consists of three steps:

 1. Synchronize a [replica][] to pick up recent changes.
 2. Work with the [replica][], make changes to files.
 3. Synchronize the changed [replica][] when done.

If you know that the [bag][]'s contents haven't changed since you last
synchronized the [replica][], you may skip step 1. 

_Data-bag_ logs the operations that affect the [bag][]'s contents. To review
the log entries, enter the [`--log` command][--log], followed by an optional date or
two dates constraining the time frame of interest. Without arguments, the
command will display all the log entries for your bag. With a single date
argument, the output will cover the period starting at that date. For example,
command 

	$ java -jar /mnt/databag.jar -d /mnt/ --log 2013-01-23

will display the log entries made on or after January, 1st 2013.

<a name="commands-and-options"> </a>

### Commands and options; disabling automatic sync

The arguments that you pass to _data-bag_ on the command line always belong to
a group that begins with a _switch_: a literal string with `--` prefix or a
shorthand that begins with a dash. The switches, including shorthands, are
case-sensitive.

To _data-bag_, every switch is either a [command][] or an [option][]. The
difference between the two is that you can enter multiple [options][] on
a command line, but no more than one [command][]. The [command][] determines
the main action that _data-bag_ will take when it runs. Some options may
conflict with others, and some may not be compatible with the [command][] you
run. In that case you may encounter a warning or error when running _data-bag_.
If you enter two or more commands on the command line, you will get an error,
and none of the commands will run.

For example, the [`--log`][--log] and [`--sync`][--sync] switches shown above
are commands, while [`--medium`][--medium] and [`--local`][--local] are
options. Thus, you can either display the log of
operations, or synchronize the [bag][]; but in both cases you are able to tell
_data-bag_ where your shared medium is mounted. 

As noted above, _data-bag_ runs the synchronization automatically when its
command line has `--medium` and `--local` options and nothing else. In fact,
the program treats `--sync` as the default [command][] and runs it whenever no
other commands are present. In some cases, such as when you are configuring a
[bag][], you may want to skip the automatic synchronization. To do that, add
the [`--nosync` option][--nosync] (shorthand `-N`) to the command line. For example,

	$ java -jar /mnt/databag.jar -d /mnt/ -C /tmp/demo1 -N

will register `/tmp/demo1` as a new replica for the [bag][], but will not
synchronize that directory.

<a name="managing-replicas"> </a>

### Managing replicas

You can establish a [replica][] in a directory, using the [`--local` option][--local]
or its `-C` shorthand. This is how you un-bag your files on a
new machine. If the new replica's directory does not exist, it will be created
for you. However, its parent directory must exist for the operation to succeed.

A user can have multiple replicas of the same bag on any machine. One of these
replicas is designated as the [default replica][] for the user's account.
Commands that work with a replica use the [default replica][] unless there is
a [`--local` option][--local] on the command line. The first replica that a user creates
becomes the [default replica][] for his or her account. To make another
[replica][] the [default replica][] for your account, enter the `--default`
argument after `--local` and respective replica's path:

	$ java -jar /mnt/databag.jar -d /mnt/ -C /tmp/demo1 --default

Note that this command will also synchronize `/tmp/demo1` unless you add the
[`--nosync` option][--nosync].

To find out locations of replicas defined for the current user's account, use
the [`--list` command][--list] (or the shorthand `-l`) followed by the `replicas` keyword:

	$ java -jar /mnt/databag.jar -d /mnt/ -l replicas

The output of this command will look like:

>       /tmp/demo 
>     * /tmp/demo1

The line with an asterisk denotes the current user's default replica.  

To remove information about a replica from the [bag][], log on as that
replica's user and run the `--drop replica` command. The contents of the
dropped replica's directory will remain intact, but **information about the
past operations with that replica will be lost.**

	$ java -jar /mnt/databag.jar -d /mnt/ -C /tmp/demo1 --drop replica

<a name="filtering-files"> </a>

### Filtering files

There are many practical reasons to avoid tracking and synchronizing certain
files, or to synchronize them on different schedules. Some files may be too
large to fit on the shared medium. Others are generated automatically and can
be rebuilt on any machine. Yet others you may not care about or have access
to. Conversely, you may only be interested in tracking files that follow a
certain name pattern, but unable to group them in a separate directory.

To address these concerns, _data-bag_ offers a mechanism of [filters][].
[Filters][] apply to locations of files relative to the [bag][] or
[replica][] that stores them. A [filter][] consists of two sets of
[patterns][]. The first set _(includes)_ limits eligible files to those
matching any of its constituent [patterns][]. The second set
_(excludes)_ removes files matching its [patterns][] from the tentative
list of files. Thus, a file passes the filter when its relative location
matches one or more patterns on the list of includes and none of the patterns
on the list of excludes. As an exception, when the list of includes is empty,
all files that do not match excludes patterns pass the filter.  

[Filters][] are stored in a [bag][], so when you need to apply one of
them, you don't have to re-enter all its components. Each filter has a unique
name within the [bag][]. Filter names are case-insensitive. When choosing a
name for your filter, you may want to use a short descriptive string so you
can remember what it does by looking at the name. Note that filter names listed
in the table below have special meanings to  _data-bag_:  

<table border="1" cellspacing="0" cellpadding="4">
<tr>
<th>Name</th>
<th>Meaning</th>
</tr>
<tr>
<td><code>all</code></td>
<td>
The built-in filter that matches all files. This filter cannot be changed.
</td>
</tr>
<tr>
<td><code>default</code></td>
<td>
User-defined filter that <em>data-bag</em> applies to replicas that do not
have their own default filters when no filter is explicitly selected for an
operation. This filter does not exist in a new <a href="#term-bag">bag</a>
until you create it. If there is no filter named <code>default</code>, and no
default filter is designated for the replica, <em>data-bag</em> falls back to
the built-in filter <code>all</code>.
</td>
</tr>
</table>

The easiest way to define a [filter][] and store it in a [bag][] is to use the
[`--set` option][--set] and place all the filter's [patterns][] on the command line:

	$ java -jar /mnt/databag.jar -d /mnt -F 'Documents and spreadsheets' \
	--set '*.odt:*.ods:*.doc:*.xls' 'Welcome*'

This command will create a [filter][] called _"documents and spreadsheets"_
(the name is not case-sensitive) that matches files with suffixes `.odt`,
`.ods`, `.doc`, and `.xls` in the replica's directory (and none of its
subdirectories), but excludes any files with names beginning with _Welcome_.
Case sensitivity of the filter's [patterns][] depends on the underlying file
system. [POSIX] file systems are usually case-sensitive. Operations that do
no affect any [replicas][] (i.e. work with files in a [bag][] only) perform
case-sensitive pattern matching.

The above command will also synchronize the default replica unless you add the
[`--nosync` option][--nosync]:

>     Jan 25, 2013 12:24:08 AM name.livitski.databag.cli.Launcher setFilter
>     INFO: Updating filter "documents and spreadsheets"
>     Jan 25, 2013 12:24:08 AM name.livitski.databag.app.sync.SyncService synchronize 
>     INFO: Synchronizing replica #1 for user@d1.data-bag.org at /tmp/demo with databa
>     se at /mnt/databag using filter "documents and spreadsheets" ...

Note that the filter name follows the [`--filter` option][--filter], or its
shorthand `-F`. Use that option to select a [filter][] to manipulate, display,
or apply to an operation.

The [`--set` option][--set] expects two arguments: a list of _include_
[patterns][], and a list of _exclude_ [patterns][]. The patterns on each list
are separated by the system-specific path separator string. On
[POSIX][]-compliant systems, that string consists of a colon character, `:`.
When a pattern on a list contains white space,
you have to escape it to make sure the entire list is interpreted
by the shell as a single argument. You may also have to escape the `?` and `*`
characters within patterns to prevent their expansion by the shell. To make
one of the lists empty, you can follow up the `--set` option with an empty-string
argument, if your shell allows that, or use a single path separator string
otherwise. For example, command line

	$ java -jar /mnt/databag.jar -d /mnt -F 'No temp files' --set : '**/*.tmp' -N

will create a filter that excludes all files with suffix `.tmp` anywhere in the
[bag][]'s or [replica][]'s hierarchy.

To apply a named filter to an operation that supports filters, use the
[`--filter` option][--filter], or its shorthand `-F`. You can append the `--invert`
literal as the second argument to `--filter` to make the [filter][] work in reverse,
rejecting files that match it and accepting files that don't. For example,
having defined the prevoius filter, you can list all files with suffix `.tmp`
that are already in the [bag][] by entering the command:

	$ java -jar /mnt/databag.jar -d /mnt -F 'No temp files' --invert -l

To list filters in the [bag][], use the [`--list` command][--list] (or its shorthand
`-l`) with the `filters` keyword:

	$ java -jar /mnt/databag.jar -d /mnt -l filters

Depending on what your default replica is, the output of this command may be:

>     * all                                                                          
>       documents and spreadsheets                                                   
>       no temp files                                                                

Note the asterisk on the first line. It marks the default filter applied to the
current replica. Since our [default replica][] does not have a default filter,
and no filter named `default` exists in the [bag][], this replica will have the
built-in filter `all` applied to it. To change the default filter for a
replica, append the `--default` literal to the [`--filter` option][--filter]: 

	$ java -jar /mnt/databag.jar -d /mnt -F 'No temp files' --default -N

Note that you may want to use the [`--local` option][--local] as well unless you
are configuring the [default replica][]. Now, the `--list` command line shown
above will result in this output:

>       all                                                                          
>       documents and spreadsheets                                                   
>     * no temp files                                                                

_TODO: explain how to display a filter_

_TODO: explain how to save a filter_

_TODO: explain how to load a filter (note that --load is an option)_


### Listing the bag's contents

You can list paths to all files in a [bag][] using the [`--list` command][--list]
(or its shorthand `-l`) followed by the `files` keyword, or without the keyword:

	$ java -jar /mnt/databag.jar -d /mnt -l

>     Jan 25, 2013 11:03:45 AM name.livitski.databag.cli.Launcher listFiles
>     INFO: Applying filter "all"
>     About_these_files.odt 
>     Derivatives_of_Ubuntu.doc 
>     Maxwell's_equations.odt 
>     Payment_schedule.ods 
>     Trigonometric_functions.xls 

The listing will include relative locations of both current and deleted files
matching the current [filter][]. _Data-bag_ sends the diagnostic output (two
lines at the top) to the standard error stream. You can separate it from the
list output by redirecting either or both output streams.

You may add optional arguments to `--list files` to show only files
updated at specific times. In that case, you cannot omit the `files` keyword.
For example,

	$ java -jar /mnt/databag.jar -d /mnt -l files changed 2016-06-22 2016-07-01 

will show you the files that were changed on or after June, 22nd but before
July, 1st 2016. Only files that had their contents changed during that period
will be listed, while files that were simply timestamped or deleted will not.
If you want to include those files as well, enter this command:

	$ java -jar /mnt/databag.jar -d /mnt -l files touched 2016-06-22 2016-07-01

If you want be more precise with the period of file updates, you may enter
the cut-off times (up to a millisecond) along with the date arguments. The
syntax for that is the same as with the [--log][] command. You may also omit
the second date/time argument as you do with [--log][].
Note that _data-bag_ picks up file updates when it
[synchronizes a bag with replicas](#synchronizing-files). Thus, a report on
updated files will be limited in accuracy by the frequency of your bag's
synchronizations.

With the [`--list` command][--list] you can display other data records stored in a
[bag][], such as [replicas][], [filters][], and view detailed
information about a [filter][]. That is done by placing a keyword, such as
`replicas`, `filters`, or `filter`, after the command. A keyword that follows
the [`--list` command][--list] can be typed using letters in any (or both) cases.  

_TODO: example of the --save option with a list command_

<a name="version-tracking"> </a>

### Tracking versions of stored files

When _data-bag_ synchronizes a [replica][], it detects changes made to the
local files. A changed local file is matched against the file at the same
location in the [bag][] as explained [below](#conflict-resolution). If there
is no match, the local file is considered a different [version][] of the bagged
file. This usually happens when someone makes changes to the local file and
saves them. If the clocks on all machines hosting [replicas][] are set
correctly, the changed file will have later modification date than the same
file in the [bag][]. With the default settings, _data-bag_ saves changes made
to the file in the [bag][] as a new [version][] of that file and retains all
its prior [versions][].

Thus, _data-bag_ remembers the [history][] of each file that it stores. Note
that a file's [history][] contains only those [versions][] of the file
that were available when it was synchronized. For instance, if you edit a
document in the morning, save it, then edit it again in the afternoon, and
only then synchronize it, the [history][] of that document's file will **not**
contain a record of the document as it looked during your lunch time. To have
changes to your files recorded separately from later changes, synchronize the
files that you are editing often.

To review the history of a shared file, run the [`--history` command][--history]
(shorthand `-h`) followed by that file's path relative to the replica's root
directory. For example,

	$ java -jar /mnt/databag.jar -d /mnt -h 'About_these_files.odt'

yields this output:

>     Jan 25, 2013 11:54:37 AM name.livitski.databag.cli.Launcher listVersions
>     INFO: Applying filter "all"
>     
>     === File # 1 with name 'About_these_files.odt' ===
>     
>     Version:           Id     Parent                Size Timestamp
>                         1     (none)              181512 2010-03-26 08:21:16
>     (current)           2          1              182192 2013-01-25 11:54:03
>     
>     Found 1 existing, 0 renamed, and 0 deleted file(s)


Note that the file has a [record number][file number] associated with it. These
numbers uniquely identify files in the [bag][]. Also note that each [version][]
record has a [number][version number], too (labeled `Id` in the output). Those
numbers are unique within the file's history.

_TODO: cover the common arguments and options of the `--history` command_

<a name="conflict-resolution"> </a>
 
### Resolving conflicts among replicas

During synchronization, if there is a file at the same location relative to
the [bag][] and the [replica][], the local file's attributes are compared to
those of the prior [versions][] of the bagged file. If there is a match, the
local file is replaced by the most recent [version][] from the bag. If the
local file is newer than all known [versions][] of the bagged file and the
[replica][] has been synchronized to the most recent version of that file in
the [bag][] before, the local file is added to the [bag][] as a new
[version][]. Otherwise, if there is no match, _data-bag_ detects a
[conflict][]. A conflict is a situation of ambiguity with respect to the
order or content of [versions][] in a file's history or detection and
propagation of a file's deletion.

By default, _data-bag_ does not attempt to resolve conflicts and simply
exits with an error message:

>     Jan 25, 2013 18:46:00 AM name.livitski.databag.cli.Launcher run
>     SEVERE: Please specify how to resolve the conflict between local file /tmp/demo1
>     /About_these_files.odt (size = 181875, modified at Fri Jan 25 18:44:53 EST 2013)
>     and version 2 (file=1, base=1, size=182192, modified=2013-01-25 11:54:03.0). The
>     file has a synchronization record of file #1 in replica #2 to version #1

To proceed with synchronization, you must tell _data-bag_ how to resolve the
conflict. Add the [`--default-action` option][--default-action] (or its shorthand `-A`)
followed by one of the keywords from the table below:

<table border="1" cellspacing="0" cellpadding="4">
<tr>
<th>Keyword</th>
<th>Meaning</th>
</tr>
<tr>
<td><code>NONE</code></td>
<td>
Means that no action will be taken to synchronize the affected file. In other
words, with <code>-A NONE</code> switch, files with a conflict are simply
skipped.
</td>
</tr>
<tr>
<td><code>UPDATE</code></td>
<td>
Tells <em>data-bag</em> to make the local file the most recent version of the
bagged file and add it to that file's history as such. The side effect is that
the local file may have its modification time adjusted to follow the
modification time of the conflicting file in the bag.
</td>
</tr>
<tr>
<td><code>DISCARD</code></td>
<td>
Asks <em>data-bag</em> to replace all conflicting local files with recent
versions from the shared medium. <strong>This mode can cause data loss, so use
it with caution.</strong>
</td>
</tr>
</table>

_TODO: describe the granularity of actions and the use of filters and
patterns to address that_

<a name="deleted-files"> </a>

### Deleted files

_Data-bag_ detects files that have been deleted from the replica after it was
last synchronized. During the next synchronization of that replica, those files
are marked deleted in the [bag][]. The deletion will propagate to other
[replicas][] as they are synchronized. Note that a [bag][] retains histories of
its files even after they are marked deleted to allow you to
[restore](#restoring-files) such files later. You can also create a new file in
a [replica][] at the same location as the previously deleted file. The new file
will have a separate [history][] if you synchronize the [replica][] that you
create it in after the old file's deletion and before the new file's creation.

The [`--history` command][--history] with a location argument lists versions of the existing
file and histories of all deleted files at the same relative location. For
example,

_TODO: example with multiple histories for the same name_

Note that deleted file records have their own numbers. Therefore, when you
enter a file's [record number][file number], you will be shown only the history
of that particular file. 

When you rename or move a file within a [replica][], _data-bag_ currently
treats such event as two operations:

 - deletion of the file at its old location, and
 - creation of a file at the new location

In other words, continuity of the file's [history][] is not preserved. There
are plans to implement detection of file renames and moves in future, even
though often such detection cannot be done reliably without user's
intervention.

<a name="restoring-files"> </a>

### Restoring old versions and deleted files

_Data-bag_ allows you to restore older [versions][] of existing files
and the files you deleted after a synchronization. There are two different
[commands][] that restore files, each operating in its own way.

The first method is useful when you need to obtain historic files for a
temporary use. For example, you may want to check how a certain document looked
a week ago, make a copy of it, or compare it with the current [version][]. To
obtain such temporary images of historic files, use the
[`--restore` command][--restore] described in this chapter.

The other method is needed when you decide to discard unwanted changes to your
files after you have synchronized them with a [bag][]. For example, you might
have made a computation in a spreadsheet, saved and synchronized it. A week
later you may realize that the formulas that you used were incorrect, and
decide to start from scratch. To roll back changes that are already stored in
a [bag][] you may want to run the [`--undo` command][--undo], described
[later](#undoing-changes).

Restoration of historic files for a temporary use requires knowledge of two
things: original locations and version numbers of the files being restored.
For simplicity, consider a single file restoration first. If you want to see
how the file `About_these_files.odt` looked before it was modified, and you
[know](#version-tracking) that there are two [versions][] of that file
in the [bag][], you can run [`--restore` command][--restore] (or its shorthand `-r`) as
follows:

	$ java -jar /mnt/databag.jar -d /mnt -r 'About_these_files.odt' --vn 1

This will result in a copy of that file's [version][] with
[number][version number] `1` restored to the default replica in `/tmp/demo`:

>     Jan 25, 2013 8:39:50 PM name.livitski.databag.cli.PointInTimeAbstractCommand res
>     olveFileSpec
>     INFO: Applying filter "no temp files"
>     Jan 25, 2013 8:39:50 PM name.livitski.databag.app.sync.RestoreService restore
>     INFO: Restoring version 1 (file=1, base=0, size=181512, modified=2010-03-26 08:2
>     1:16.0) to /tmp/demo/About_these_files.odt ...

The file's relative location follows the [`-r` command][--restore] on the
command line. The [`--vn` option][--vn] specifies the [version number][] to be
restored. Observe that the historic [version][] is written to its [replica][]
location, replacing the current file.

	$ ls -l /tmp/demo/About_these_files.odt

>     -rw-r--r-- 1 user users 181512 2010-03-26 08:21 /tmp/demo/About_these_files.odt

_Data-bag_ will synchronize the file with the [bag][] before overwriting it, so
that file can be retrieved later. Since [`--restore` command][--restore] performs
temporary restore, the file's old [version][] will be replaced again with the
current one next time you synchronize the [replica][].

If you want to compare the
current version of the file with the original version, or otherwise avoid
confusion between current and historic files, you may tell _data-bag_ to
restore that file elsewhere. To achieve that, add a [`--save` option][--save]
(or its shorthand `-o`) to the command line:

	$ java -jar /mnt/databag.jar -d /mnt -r 'About_these_files.odt' --vn 1 \
	-o /tmp/about.odt

The argument of the [`--save` option][--save] is either an absolute location of the
restored file or a location relative to the current directory. If you restore
a single file into a different location in the current [replica][], _data-bag_
will add it to the [bag][] right away, without the need for synchronization.
However, _data-bag_ will not overwrite any existing files when restoring with
the [`--save` option][--save].

When the above command is run without a [version number][], _data-bag_ restores
the most recent version of the file, i.e.

	$ java -jar /mnt/databag.jar -d /mnt -r 'About_these_files.odt'

will cause the subsequent command

	$ ls -l /tmp/demo/About_these_files.odt

produce this output:

>     -rw-r--r-- 1 user users 182192 2013-01-25 11:54 /tmp/demo/About_these_files.odt

You can also restore the most recent version of a file as of a specific moment
in the past, by adding [`--as-of` option][--as-of] (or its shorthand `-a`). Note that
[`--as-of` option][--as-of] cannot be used with [`--vn`][--vn].

	$ java -jar /mnt/databag.jar -d /mnt -r 'About_these_files.odt' \
	-a 2012-12-31 22:12:12
 
Note that the time argument at the end of this line is optional. If you omit
it, _data-bag_ assumes `00:00:00` (midnight at the beginning of the calendar
day) as the target time.

_TODO: show how to restore deleted files_

_TODO: explain the use of file numbers when restoring files_

When you don't have a fixed [version number][] to restore, you can restore
multiple files with relative locations matching a [pattern][]. You can use the
[`--save` option][--save] with such command if the argument points to an empty directory
that is neither the current [replica][]'s root nor any of its descendants. For
example, commands

	$ mkdir -p ~/Desktop/2012
	$ java -jar /mnt/databag.jar -d /mnt -r '*.odt' -a 2013-01-01 -o ~/Desktop/2012

will result in all files with suffix `.odt` from the root directory of your bag
that had versions modified before the end of 2012 having copies of these
versions restored to the new directory `Desktop/2012` in your the home area of
your user account.

If any of the restored files have to be written to a location within the
current replica, e.g. by following a symbolic link, the operation will fail.

<a name="undoing-changes"> </a>

### Rolling back changes to files

_TODO: describe the undo operation_

_TODO: explain how undo deletes and un-deletes files_

<a name="purging-bags"> </a>

### Purging old versions from a bag

When _data-bag_ updates a [bag][], it retains all deleted files and historical
[versions][] of existing files. Thus, [bags][] tend to grow in size
during each synchronization. To avoid running out of space on the shared
medium, you may occasionally want to purge old versions and deleted files.
To do that, use the [`--purge` command][--purge] followed by a date in `yyyy-mm-dd`
format: 

	$ java -jar /mnt/databag.jar -d /mnt --purge 2012-01-01

The date on the above command line denotes the beginning of a new [epoch][].
To specify the [epoch][] more precisely, you may add a time argument following
the date. The time format is `hh:mm:ss[.f]`, where hour is taken from a 24-hour
clock, and fractions of a second may be omitted:

	$ java -jar /mnt/databag.jar -d /mnt --purge 2013-01-24 22:02:01

When no time is specified, `00:00:00` (midnight at the beginning of the calendar
day) is assumed. 

[Bags][] that were never purged have their [epochs][]
beginning at the earliest modified time of any [version][] they store.
_Data-bag_ is not required to retain [versions][], deleted files, or log
records beyond the current [epoch][], and attempts to delete that data
permanently. Notable exceptions are the current versions of existing files that
haven't been modified since the epoch began, and, in some cases, version
records needed to restore other versions modified during the current [epoch][].
**The purge operation is irreversible, so use it with caution.**

<a name="encrypting-bags"> </a>

### Encrypting your bag

To help you prevent unauthorized access to [bags][], _data-bag_ supports
encryption of data on the shared medium. _Data-bag_ uses symmetric cryptography
to encrypt [bags][] and, currently, applies a single key to each [bag][] in
its entirety. Therefore, you should use long randomized keys for [bag][]
encryption and store them securely.

The _data-bag_'s interface allows you to set up encryption and estblish a key
when you create a [bag][]. Then, you have to feed the same key to the software
every time you run an operation on the encrypted [bag][]. In both cases, you
need to add the [`--encrypt` option][--encrypt] (or the shorthand `-E`) to the command line.

The arguments to the [`--encrypt` option][--encrypt] differ depending on how you provide the
key to _data-bag_:

   - To submit the key via standard input, enter the `stdin` keyword following
   the [`--encrypt` option][--encrypt]. Note that your terminal will echo the key unless you
   redirect the standard input.
   
        $ java -jar /mnt/databag.jar -d /mnt --create -E stdin

   - To enter the key on the terminal without echo, use the `ask` keyword. This
   option requires Java runitme version 6 or newer and cannot be used with
   input or output redirection.

   - To enter the key on the command line, or use a shell variable, append the
   `key` keyword and the key argument or variable to the command line after the
   [`--encrypt` option][--encrypt]. This is the only method that allows you to add line
   separator characters to your key.
   
        $ java -jar /mnt/databag.jar -d /mnt -l -E key "$BAG_KEY"
   
The [`--encrypt` option][--encrypt] is insensitive to the letter case of its keywords.
Given the [`--encrypt` option][--encrypt] without arguments, _data-bag_ defaults to asking
user to enter the key via terminal (as in the `ask` mode) in the environments
supporting that. Where the terminal input is not supported, the software issues
a warning message and falls back to the standard input method.

_Data-bag_ does not accept the space character (code `32`) in the encryption
keys. To use a passphrase for [bag][] encryption _(recommended)_, you need an
alternative way to separate its words. One option is to delimit them with
punctuation marks.

_Data-bag_ currently supports two encryption algorithms: _AES_ and _XTEA_. The
default encryption algorithm for [bags][] is _AES_. To use the alternative
algorithm, append keywords `--cipher xtea` to the [`--encrypt` option][--encrypt]:

	$ java -jar /mnt/databag.jar -d /mnt/teabag --create -E ask --cipher xtea

Although _data-bag_'s interface does not yet allow you to encrypt or decrypt an
existing [bag][], or change the encryption algorithm or key, you can still do
that by calling the database management library embedded in the software
directly:

	$ java -cp /mnt/databag.jar org.h2.tools.ChangeFileEncryption \
	-dir /mnt/teabag/databag/ -db databag -decrypt "password" -cipher XTEA 

<a name="change-encryption"> </a>
For details about the encryption management tool embedded in _data-bag_, please
refer to the the `ChangeFileEncryption` command reference in the
[H2 database](http://h2database.com) documentation, available online at
<http://h2database.com/html/features.html#file_encryption>. You can obtain the
tool's usage summary by running it without arguments:

	$ java -cp /mnt/databag.jar org.h2.tools.ChangeFileEncryption

The directory argument to the `ChangeFileEncryption` tool must point to the
`databag` directory of the medium with your [bag][]. The database argument
must be `databag`, too.


_Data-bag_ command line reference
---------------------------------

<a name="commands-reference"> </a>

### Commands

<h4 id="switch-help">-?, --help</h4><a name="switch-help"> </a>
__Syntax:__ `--help`

Prints the command line syntax summary and exits.

<h4 id="switch-drop">--drop</h4><a name="switch-drop"> </a>
__Syntax:__ `--drop` *type* [ `--force` ]

Removes a record from the [bag][]. The *type*
argument communicates the type of a record to be removed.
Supported types are `REPLICA` and `FILTER`. `REPLICA` type must be
used in conjunction with the [`--local` option][--local] to select the
[replica][] to drop. `FILTER` type requires a [`--filter` option][--filter]
that tells _data-bag_ what [filter][] to drop. Built-in filter `all`
cannot be dropped. If there are replicas that use the filter
being dropped as their default filter, the command will fail
unless followed by the `--force` switch.

<h4 id="switch-history">-h, --history</h4><a name="switch-history"> </a>
__Syntax:__ `--history` [ *location* ]

Lists all [versions][] of a file. The argument is a relative location
of the file in the [bag][]. The location must be exact, which means it
cannot contain [wildcard characters][pattern]. When a location is specified,
_data-bag_ applies the current [filter][] to it. If the location does
not satisfy the [filter][], no [histories][] are displayed. If there were
deleted files with the same name, their [histories][] are listed too.
If you omit the *file* argument, you must enter a [file number][] on the
command line using the [`--fn` option][--fn].

<h4 id="switch-list">-l, --list</h4><a name="switch-list"> </a>
__Syntax:__ `--list` [ *type* [ *predicate* ] ]

Lists items in the [bag][]. The case-insensitive
argument designates the type of items that will be listed.
It can take values `FILES`, `REPLICAS`, `FILTER`, or `FILTERS`. The
default is `FILES`. The output will contain a [header][--nosync] and
may be formatted to accommodate a standard terminal. If you redirect
the output to a file with the [`--save` option][--save], it will
contain neither the header nor the terminal formatting. With the
`FILTER` argument, the output file is formatted to allow loading
it into a [filter][] with the [`--load` option][--load].

When listing `FILES`, you may also enter a predicate upon which
the files will be filtered. This is done in conjunction with matching
files against the current [filter][]. _Data-bag_ currently supports
the following file predicates:

 - `CHANGED` [ *time-frame* ]
 - `TOUCHED` [ *time-frame* ]

Predicates' keywords are case-insensitive.
Time frame arguments are parsed according to the rules of the [--log][] 
command and compared with the modification times of file's versions.
If there are matching versions, a file is listed, otherwise it is
skipped. `CHANGED` picks up only versions that represent a content
change to the file, ignoring simple timestamps and deletion markers.
`TOUCHED` considers all version records, including deletion markers.
Note that _data-bag_ stores new versions when it
[synchronizes a bag with its replica](#synchronizing-files), so some
change records may be missing from bags that are synchronized
infrequently. Since there is no way to determine a file's deletion
time from the file system, deletion markers are timestamped when
deletions are detected during synchronization.

<h4 id="switch-log">--log</h4><a name="switch-log"> </a>
__Syntax:__ `--log` [ *time-frame* ]

Displays the log of operations that might have changed
contents of the [bag][]. Optional *time-frame* arguments
formatted as `yyyy-mm-dd[ hh:mm:ss[.f...]]` specify the
beginning (inclusive) and the end (exclusive) of the log
fragment to print. If only one argument is present, it is
treated as the beginning of the time frame and infinity is
assumed to be the end. Note that the white space between the
date and time parts of each argument must be included in the
argument. You may have to escape or quote that white space
when running _data-bag_ in a shell. If you omit the time part
of an argument, _data-bag_ will assume 00:00:00.0 local time
on the date you enter. Note that [`--purge`][--purge] erases the log
entries beyond the [epoch][].

<h4 id="switch-purge">--purge</h4><a name="switch-purge"> </a>
__Syntax:__ `--purge` *epoch*

[Purges](#purging-bags) the [versions][] of files in the [bag][]
modified before the beginning of an [epoch][]. The *epoch* argument
has the `yyyy-mm-dd` format followed by an optional `hh:mm:ss[.f...]`
part. The optional part is a separate argument on the command line. In
other words, you must not escape the white space between the parts of
the epoch argument. When run with the built-in [filter][] `"all"`, this
command also purges the log of operations with
the bag prior to the new [epoch][].

<h4 id="switch-restore">-r, --restore</h4><a name="switch-restore"> </a>
__Syntax:__ `--restore` [ *file-or-pattern* ]

Restores file(s) from the [bag][]. The argument
following this command must either be the relative location
of a file in a bag, or a relative [location pattern][pattern].
Single-file lookup by name will only succeed if there was
just one file having that name, i.e. there were no [histories][]
of deleted or renamed files with the same name in the
bag. Alternatively, you can specify a file number
using the [`--fn` option][--fn]. To restore a historic version of a
file, enter the [`--vn` option][--vn] with a [version number][] or the
[`--as-of` option][--as-of] with a date. To restore the file to a
different location or under a different name than its
current [replica][], use the [`--save` option][--save] to enter the
intended destination. If the destination is a descendant of the
current replica directory, the restored file will be
automatically added to the bag. When restoring
multiple files, the argument to [`--save` option][--save] should point
to an empty directory that is neither the current replica's
directory nor any of its descendants. Multiple-file restore
to a target directory will fail if any of the restored files
have to be written to a location within the current replica.
If you don't enter a target directory on the command line,
files that match the [pattern][] and the current [filter][] are
restored to their locations in the current [replica][]. The
replica may become ouf-of-sync with bag if
restored versions are not the current ones. When restoring
files matching a pattern, you cannot enter [`--fn`][--fn] or
[`--vn`][--vn] options. To obtain historic versions of files, use the
[`--as-of` option][--as-of] with a date of interest. Without that option,
the files will be restored to their current versions.

<h4 id="switch-sync">-s, --sync</h4><a name="switch-sync"> </a>
__Syntax:__ `--sync` [ *location-pattern* ]

Synchronizes file(s) in the [bag][] with the current
[replica][]. This command runs by default if you have selected
the bag with [`--medium`][--medium], informed _data-bag_ about the
current replica, either using [`--local`][--local] or by designating the
[default replica][], and did not enter any other [command][] on the
command line. When you enter this command explicitly, you may add a
[pattern][] argument to limit the operation to a subset of files within
the replica. You can also synchronize a specific file by entering its
[number][file number] after the [`--fn` option][--fn]. This works
regardless of whether [`--sync`][--sync] is explicitly entered on the
command line. Note that you cannot use the [`--nosync`][--nosync] option
with this command.

<h4 id="switch-undo">-u, --undo</h4><a name="switch-undo"> </a>
__Syntax:__ `--undo` [ *file-or-pattern* ]

Reverts file(s) in the [bag][] to a historic state.
This command retains the undone changes to files in a bag as
branches of those files' version trees. The argument
following this command must either be the relative location
of a file in a bag, or a relative [location pattern][pattern].
Single-file lookup by name will only succeed if there was
just one file having that name, i.e. there were no [histories][]
of deleted or renamed files with the same name in the
bag. Alternatively, you can specify the [file number][]
using the [`--fn` option][--fn]. To return to the file's
[version][] with a certain [version number][version], enter the
[`--vn` option][--vn] with that number. To return to the file's
contents as of a specific date, enter the
[`--as-of` option][--as-of] with that date. When reverting files
matching a [pattern][], you can only use the [`--as-of` option][--as-of]
to select the files' [versions][]. Without that option, the files will
be reverted to the current date. Such operation has no effect on
local files unless their histories have future-dated versions. By
default, `--undo` will synchronize the file(s) matching the name or
pattern (and the effective [filter]), or the numbered file regardless of
the filter, with the current [replica][]. To prevent such synchronization,
use the [`--nosync` option][--nosync].

<a name="options-reference"> </a>
### Options

<h4 id="switch-default-action">-A, --default-action</h4><a name="switch-default-action"> </a>
__Syntax:__ --default-action *action*

Sets the default action to take in case of a
[version conflict][conflict]. Allowed values are `NONE`, `UPDATE`,
and `DISCARD`.

<h4 id="switch-as-of">-a, --as-of</h4><a name="switch-as-of"> </a>
__Syntax:__ `--as-of` [ *date [time]* ]

Specifies the moment in time to look up in files' [histories][].
Use this option with [`--restore`][--restore] to obtain a copy of the file's
data as of a certain time in the past, or with [`--undo`][--undo] to
return a file in the [bag][] to a historic state. Note that time-bound
commands may produce correct results only within a certain range of
dates. For instance, you may not be able to restore a file
to a state beyond the initial synchronization time or beyond the [epoch][] if
[`--purge`][--purge] has been run. Use the [`--log` command][--log] to
determine the feasible date range for a bag. The
argument must be in `yyyy-mm-dd` date format followed by an
optional `hh:mm:ss[.f...]` part. The optional part is a
separate argument on the command line. In other words, you
must not escape the white space between the parts of the
argument.

<h4 id="switch-allow-time-diff">--allow-time-diff</h4><a name="switch-allow-time-diff"> </a>
__Syntax:__ `--allow-time-diff` *threshold*

Sets the difference threshold for files' time stamps to be
considered distinct. Measured in milliseconds. The default
is 3 seconds minus one millisecond.

<h4 id="switch-local">-C, --local</h4><a name="switch-local"> </a>
__Syntax:__ `--local` *path*

Sets the root path of the [replica][] to work with. A user may create
multiple [replicas][] of the same bag and synchronize them one at a
time. Append `--default` to make this replica the [default replica][]
for your user account.

<h4 id="switch-cds">--cds</h4><a name="switch-cds"> </a>
__Syntax:__ `--cds` *percentage*

Adjusts the program's memory utilization allowance. The less memory
_data-bag_ is allowed to use, the more disk space it will need to
store files. This parameter limits the size of a structure describing
differences between versions of any file that _data-bag_ is allowed
to keep in memory. The boundary is set as a percentage or a fraction
of the JVM's maximum heap size. Default value of this parameter is 10%.

<h4 id="switch-compress">--compress</h4><a name="switch-compress"> </a>
__Syntax:__ `--compress` *mode*

Selects a compression algorithm to be used for files stored
in the [bag][]. Supported values are `NO`, `LZF`, and
`DEFLATE`. Defaults to `DEFLATE`. This setting is stored in the
bag and affects future invocations. It does not change
the format of existing data in the bag.

<h4 id="switch-create">--create</h4><a name="switch-create"> </a>
__Syntax:__ `--create`

Asks _data-bag_ to create a new [bag][]. Use the [`--medium` option][--medium]
to choose the new bag's location. To have the bag encrypted, add the
[`--encrypt` option][--encrypt].

<h4 id="switch-medium">-d, --medium</h4><a name="switch-medium"> </a>
__Syntax:__ `--medium` *root [path]*

Points to a medium or directory containing the [bag][]. The default is
current directory. Optional *path* argument points to a
subdirectory on the selected medium if it stores multiple bags.

<h4 id="switch-dcs">--dcs</h4><a name="switch-dcs"> </a>
__Syntax:__ `--dcs` *percentage*

Limits the amount of data that _data-bag_ will have to read when it restores a
file. A lower limit reduces the time it will take to synchronize and retore
files at the expense of additional storage space used by the [bag][].
The parameter is the maximum total size of all incremental differences between
the complete image of a file and any new [version][] stored in the [bag][].
Once _data-bag_ exceeds that limit, it stores the new version of a file in its
entirety. The boundary is set as a percentage or a fraction of the file's size.
The default value of this parameter is 50%.

<h4 id="switch-encrypt">-E, --encrypt</h4><a name="switch-encrypt"> </a>
__Syntax:__ `--encrypt` [ *key-source* ... ] [ `--cipher AES` | `--cipher XTEA` ]

Tells _data-bag_ to use encryption when creating or opening
the [bag][]. To enable encryption of a bag, use this option when
you create it with [`--create` switch][--create]. Once a bag is encrypted,
the key and cipher remain the same. You have to include
[`--encrypt` option][--encrypt] with the correct key and cipher every time
you use that bag. To change encryption parameters, use the
[`org.h2.tools.ChangeFileEncryption` utility](#change-encryption) included
with the _data-bag_ distribution. That utility also allows you to
encrypt or decrypt an existing bag. You can place the
encryption key on the command line, have it read from
standard input, or enter it interactively when _data-bag_
starts. An optional argument that follows [`--encrypt`][--encrypt] selects
an encryption key or its source. If that argument is the
word `key`, _data-bag_ will use the next command line argument
as the key. If you enter the `ask` string as the argument,
_data-bag_ will attempt to ask you for password interactively.
That only works with Java 6 or newer when _data-bag_ is run
from a shell without input or output redirection. Finally,
you may have _data-bag_  read the key from standard input by
entering `stdin` argument. If you do that, your input may
be shown on screen. By default, _data-bag_ will try to use the
console and fall back to the standard input if the console
is unavailable. Regardless of how _data-bag_ obtains the
encryption key, it will not accept keys that contain a space
character (ASCII 32). If the password is entered
interactively or read from the standard input, it cannot
contain end-of-line sequences either. You can append the `--cipher`
switch to [`--encrypt`][--encrypt] to select an encryption
algorithm. Supported algorithms are `AES` and `XTEA`. The
default cipher is `AES`.

<h4 id="switch-filter">-F, --filter</h4><a name="switch-filter"> </a>
__Syntax:__ `--filter` *name* [ `--default` | `--invert` ]

Selects a [filter][] to apply to the set of files before
performing the requested command. Files that satisfy the
filter will be processed, while those that don't will be
ignored. Filters apply to both local files and files in a bag.
You can add the `--invert` modifier following the filter name to
reverse the filter's effect. You can designate a default
filter for the current [replica][] that will apply when no other
filter is selected. You do that by entering the `--default`
modifier after the filter name. Replicas that do not have a
default filter assigned will use the filter named `default`,
if it exists, or the built-in filter `all` otherwise. Filter
option is also used to designate a filter to load, display,
save, or delete, when applicable.

<h4 id="switch-fn">--fn</h4><a name="switch-fn"> </a>
__Syntax:__ `--fn` *file-id*

Chooses a file in a bag by its [number][file number]. Use this option with
commands like [`--history`][--history] or [`--restore`][--restore] to
resolve ambiguity among the file records. When a file is specified
by number, normal [filtering][filter] rules are ignored during the
file lookup.

<h4 id="switch-load">--load</h4><a name="switch-load"> </a>
__Syntax:__ `--load` *from-file*

Loads a [filter][] definition from a file. Use it in conjunction
with [`--filter` option][--filter] that specifies the name of a filter to
load. A file name must follow the `--load` option and point to
a file with a valid [filter][] definition. Filter definition files are
created by running [`--list filter`][--list] command with the
[`--save` option][--save]. Note that you cannot load the built-in filter
`all`, but you can load the filter named `default`.

<h4 id="switch-lob-size">--lob-size</h4><a name="switch-lob-size"> </a>
__Syntax:__ `--lob-size` *bytes*

Adjusts the storage policy that _data-bag_ applies to its binary data. 
The parameter is the maximum size of a binary object, such as contents of
a file, that triggers its storage in a separate file on the medium containing
the [bag][]. Smaller objects are stored in the bag's main file. The default
threshold is 3500 bytes. This setting is stored in the bag and affects future
invocations. It does not change the storage strategy for existing data in the bag.

<h4 id="switch-nosync">-N, --nosync</h4><a name="switch-nosync"> </a>
__Syntax:__ `--nosync`

Disables automatic synchronization of the current [replica][].
Use this option when you want to do additional setup before
using the replica, or to change settings without
synchronizing.

<h4 id="switch-nobanner">--nobanner</h4><a name="switch-nobanner"> </a>
__Syntax:__ `--nobanner`

Instructs _data-bag_ to omit the header from its output. This option
simplifies the output parsing when running _data-bag_ from a script. 

<h4 id="switch-save">-o, --save</h4><a name="switch-save"> </a>
__Syntax:__ `--save` *file*

Writes the program's output to a file. When used in conjunction with
[`--restore` command][--restore], this option causes _data-bag_ to
restore files to locations other than their current [replicas][].
With [commands][] that display lists or other information, this
option redirects the output and suppresses some formatting. With
[`--list filter`][--list], this option instructs _data-bag_ to create
a file that you can later load into a [filter][] using the
[`--load` option][--load].

<h4 id="switch-set">--set</h4><a name="switch-set"> </a>
__Syntax:__ `--set` *include exclude*

Updates a [filter][] definition from the command line. Use it in
conjunction with the [`--filter` option][--filter] that specifies the
name of a filter to change. Note that you cannot change the built-in
filter `all`, but you can change the filter named `default`.
There must be two arguments following this option. First
argument is expected to list the [location patterns][pattern] to
include in filtered results, while second argument should
list the patterns to exclude. Both lists must use
system-dependent path delimiter (for example, `:` on Unix
and Mac, or `;` on Windows) to separate their elements.
Lists that contain spaces must be properly escaped to
prevent the operating system from treating them as multiple
arguments. To omit one of the lists, use either an empty
argument or a single path delimiter. If the inclusion list
is omitted or empty, _data-bag_ implies an include-all
pattern.

<h4 id="switch-upgrade-db">--upgrade-db</h4><a name="switch-upgrade-db"> </a>
__Syntax:__ `--upgrade-db`

Enables schema evolution for [bags][] created by previous
versions of _data-bag_. Please remember to back up your
bag before using this option. That will help you
recover from problems that you may encounter during the upgrade.

<h4 id="switch-verbose">-v, --verbose</h4><a name="switch-verbose"> </a>
__Syntax:__ `--verbose` [ *level* ]

Runs in verbose mode, logging additional status information. The *level*
argument is optional. `-vv` makes the _data-bag_ run in the debug mode.

<h4 id="switch-vn">--vn</h4><a name="switch-vn"> </a>
__Syntax:__ `--vn` *version-id*

Selects a file's [version] by its [number][version number]. Use this
option with [`--restore`][--restore] to restore an older version of a file.


Concepts and terms used in this manual
--------------------------------------

<dl>

<dt id="term-bag"><a name="term-bag"> </a>Bag</dt>
<dd>a directory containing a database with histories of certain files
maintained by the <em>data-bag</em> software, usually stored on a
shared medium.</dd>

<dt id="term-command"><a name="term-command"> </a>Command</dt>
<dd>a group of <em>data-bag</em> arguments that begins with a certain literal
string or a shorthand string. The strings that begin <em>data-bag</em>'s
commands on a command line, including shorthands, are listed in the
<a href="#commands-reference">commands reference</a>. Unlike
<a href="#option">options</a>, commands are mutually exclusive, i.e. you
cannot enter more than one command on a command line.</dd>

<dt id="term-conflict"><a name="term-conflict"> </a>Conflict of versions</dt>
<dd>a situation of ambiguity when <em>data-bag</em> operating on a
<a href="#term-bag">bag</a> and a <a href="#term-replica">replica</a> requires
a <a href="#conflict-resolution">user's intervention</a> to prevent the loss of
meaningful data.</dd>

<dt id="term-default-replica"><a name="term-default-replica"> </a>Default replica</dt>
<dd>a <a href="#term-replica">replica</a> used by operations with local files
under a certain user account on a certain machine unless another replica is
explicitly selected for the operation.</dd>

<dt id="term-epoch"><a name="term-epoch"> </a>Epoch</dt>
<dd>the time period that began at a certain moment depending on the
<a href="#term-bag">bag</a>'s parameters and history and continues at present.
A <a href="#term-bag">bag</a> is not required to retain artifacts that are
older than its epoch. The epoch can be reset for a <a href="#term-bag">bag</a>
by running the <a href="#switch-purge"><code>--purge</code> command</a>.
</dd>

<dt id="term-file-number"><a name="term-file-number"> </a>File number</dt>
<dd>a unique number associated with each file's record in a
<a href="#term-bag">bag</a>.</dd>

<dt id="term-filter"><a name="term-filter"> </a>Filter</dt>
<dd>a group of rules that apply to locations of files relative to the
<a href="#term-bag">bag</a> or <a href="#term-replica">replica</a> that stores
them and determines eligibility of each file for an operation. The components
and application of filters are explained in the
<a href="#filtering-files">Filtering files</a> chapter.
</dd>

<dt id="term-history-file"><a name="term-history-file"> </a>History of a file</dt>
<dd>a set of <a href="#term-version">version</a> records of a file in a
<a href="#term-bag">bag</a> organized as a tree.</dd>

<dt id="term-pattern"><a name="term-pattern"> </a>Location pattern</dt>
<dd>a string of characters that can be matched against paths to files relative
to a <a href="#term-bag">bag</a> or replica's root directory. <em>Data-bag</em>
uses the <a href="http://ant.apache.org/manual/dirtasks.html#patterns">Apache
Ant syntax for patterns</a>, which is similar to <dfn>Unix globbing</dfn>
and allows to match files across directory levels.</dd>

<dt id="term-option"><a name="term-option"> </a>Option</dt>
<dd>a group of <em>data-bag</em> arguments that begins with a certain literal
string or a shorthand string. The strings that begin <em>data-bag</em>'s
options on a command line, including shorthands, are listed in the
<a href="#options-reference">options reference</a>. Options can be combined on
a command line with <a href="#term-command">commands</a> and other options.</dd>

<dt id="term-replica"><a name="term-replica"> </a>Replica</dt>
<dd>a directory containing local copies of files tracked and synchronized with
a certain <a href="#term-bag">bag</a> by the <em>data-bag</em> software. The
<a href="#term-bag">bag</a> remembers locations of replicas for each machine
and user account combination it is used with.</dd>

<dt id="term-version-number"><a name="term-version-number"> </a>Version number</dt>
<dd>a number associated with the <a href="#term-version">version of a
file</a>. Version numbers are unique within the file's history.</dd>

<dt id="term-version"><a name="term-version"> </a>Version of a file</dt>
<dd>a record of the file's contents and attributes made at a certain moment in
time, such that: (i) the file is added to the bag before its contents or
attributes change or the file is deleted; and (ii) the file is synchronized at
least once while having these contents and attributes; and (iii) the file's
contents or attributes are different from those of the other versions of the
same file.</dd>

<!--dt id="term-___"><a name="term-___"> </a>...</dt>
<dd><b>TODO:</b> <i>Add terms to this list, in alphabetical order, as they
are encountered</i></dd-->

</dl>


Your feedback and suggestions
-----------------------------

You are welcome to submit feedback as well as your suggestions about the
software. If you would like to contribute to the project, we are looking
forward to working with you!

You can send a message to the project's team via the
[Contact page](http://www.livitski.name/contact) at <http://www.livitski.name/>
or via the [project's page on GitHub](http://data-bag.github.com/).

**Thank you for using _data-bag_!**

 [README]: https://github.com/data-bag/code/blob/master/README.md "README file"
 [NOTICE]: https://github.com/data-bag/code/blob/master/NOTICE.md "NOTICE file"
 [openjdk]: http://openjdk.java.net/install/index.html "OpenJDK packages"
 [jre]: http://java.com/en/download/index.jsp "Oracle Java Runtime downloads"
 [POSIX]: http://en.wikipedia.org/wiki/POSIX#Mostly_POSIX-compliant "Mostly POSIX-compliant systems"
 
 [bag]: #term-bag "Concepts and terms: bag"
 [bags]: #term-bag "Concepts and terms: bag"
 [conflict]: #term-conflict "Concepts and terms: conflict of versions"
 [conflicts]: #term-conflict "Concepts and terms: conflict of versions"
 [command]: #term-command "Concepts and terms: command"
 [commands]: #term-command "Concepts and terms: command"
 [default replica]: #term-default-replica "Concepts and terms: default replica"
 [epoch]: #term-epoch "Concepts and terms: epoch"
 [epochs]: #term-epoch "Concepts and terms: epoch"
 [file number]: #term-file-number "Concepts and terms: file number"
 [filter]: #term-filter "Concepts and terms: filter"
 [filters]: #term-filter "Concepts and terms: filter"
 [history]: #term-history "Concepts and terms: history"
 [histories]: #term-history "Concepts and terms: history"
 [option]: #term-option "Concepts and terms: option"
 [options]: #term-option "Concepts and terms: option"
 [pattern]: #term-pattern "Concepts and terms: location pattern"
 [patterns]: #term-pattern "Concepts and terms: location pattern"
 [replica]: #term-replica "Concepts and terms: replica"
 [replicas]: #term-replica "Concepts and terms: replica"
 [version]: #term-version "Concepts and terms: version"
 [versions]: #term-version "Concepts and terms: version"
 [version number]: #term-version-number "Concepts and terms: version number"
 [--default-action]: #switch-default-action "--default-action switch"
 [--as-of]: #switch-as-of "--as-of switch"
 [--allow-time-diff]: #switch-allow-time-diff "--allow-time-diff switch"
 [--local]: #switch-local "--local switch"
 [--cds]: #switch-cds "--cds switch"
 [--compress]: #switch-compress "--compress switch"
 [--create]: #switch-create "--create switch"
 [--medium]: #switch-medium "--medium switch"
 [--dcs]: #switch-dcs "--dcs switch"
 [--encrypt]: #switch-encrypt "--encrypt switch"
 [--filter]: #switch-filter "--filter switch"
 [--fn]: #switch-fn "--fn switch"
 [--load]: #switch-load "--load switch"
 [--lob-size]: #switch-lob-size "--lob-size switch"
 [--nosync]: #switch-nosync "--nosync switch"
 [--nobanner]: #switch-nobanner "--nobanner switch"
 [--save]: #switch-save "--save switch"
 [--set]: #switch-set "--set switch"
 [--upgrade-db]: #switch-upgrade-db "--upgrade-db switch"
 [--verbose]: #switch-verbose "--verbose switch"
 [--vn]: #switch-vn "--vn switch"
 [--help]: #switch-help "--help switch"
 [--drop]: #switch-drop "--drop switch"
 [--history]: #switch-history "--history switch"
 [--list]: #switch-list "--list switch"
 [--log]: #switch-log "--log switch"
 [--purge]: #switch-purge "--purge switch"
 [--restore]: #switch-restore "--restore switch"
 [--sync]: #switch-sync "--sync switch"
 [--undo]: #switch-undo "--undo switch"
 
