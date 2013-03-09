/**
 *  Copyright 2013 Konstantin Livitski
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the Data-bag Project License.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Data-bag Project License for more details.
 *
 *  You should find a copy of the Data-bag Project License in the
 *  `data-bag.md` file in the `LICENSE` directory
 *  of this package or repository.  If not, see
 *  <http://www.livitski.name/projects/data-bag/license>. If you have any
 *  questions or concerns, contact the project's maintainers at
 *  <http://www.livitski.name/contact>. 
 */
package name.livitski.databag.cli;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.MissingResourceException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Parses the application's command line and displays the
 * summary of command line syntax.
 */
public class Syntax
{
 public CommandLine parseCommandLine(String[] args)
 	throws ParseException
 {
  CommandLineParser parser = new GnuParser();
  return parser.parse(OPTIONS, args);
 }

 @SuppressWarnings("unchecked")
 public void usage(PrintStream out)
 {
  PrintWriter pw = new PrintWriter(out, true);
  pw.println(getMessage("USAGE"));
  pw.println();
  pw.println("    " + getMessage("SYNTAX"));
  pw.println();
  pw.println(getMessage("COMMANDS"));
  pw.println();
  SortedSet<Option> commands = new TreeSet<Option>(OPTION_COMPARATOR);  
  commands.addAll(COMMAND_OPTION_GROUP.getOptions());
  listOptions(commands, pw);
  pw.println(getMessage("OPTIONS"));
  pw.println();
  SortedSet<Option> options = new TreeSet<Option>(OPTION_COMPARATOR);
  options.addAll(OPTIONS.getOptions());
  options.removeAll(commands);
  listOptions(options, pw);
  getHelpFormatter().printWrapped(pw, OUTPUT_WIDTH, getMessage("MORE"));
  pw.println();
  pw.close();
 }

 public Syntax withResources(Resources resources)
 {
  this.resources = resources;
  return this;
 }

 /**
  * Diagnostic entry point for the build process to check that all
  * usage strings that describe the application's syntax are present
  * in <code>usage.resources</code>.
  * Absent resources cause a <code>System.err</code> message
  * and a non-zero exit code.
  * @param args this method ignores its argument
  */
 @SuppressWarnings("unchecked")
 public static void main(String[] args)
 {
  PrintStream out = System.err;
  Resources resources = new Resources();
  final Class<Syntax> clazz = Syntax.class;
  String id = null;
  String legend = null;
  for (Option option : (Collection<Option>)OPTIONS.getOptions())
  {
   try
   {
    id = getOptionId(option);
    legend = resources.getString(USAGE_BUNDLE, clazz, id).trim();
    if (legend.indexOf('.') < legend.length() - 1)
    {
     out.printf(
       "Description of option %s must be a single sentence ending with a period. Got:%n\"%s\"%n",
       id, legend);
     System.exit(5);
    }
    if (option.hasArg())
     resources.getString(USAGE_BUNDLE, clazz, "arg" + id);
    legend = null;
   }
   catch (MissingResourceException missing)
   {
    if (null == legend)
    {
     out.printf(
       "Option \"%s\" does not have a description string in the resource bundle \"%s\"%n",
       id, USAGE_BUNDLE
     );
     missing.printStackTrace(out);
     System.exit(1);
    }
    out.printf(
      "Required argument spec \"%s\" is missing from the resource bundle \"%s\"%n",
      getArgumentId(option), USAGE_BUNDLE
    );
    missing.printStackTrace(out);
    System.exit(2);
   }
   catch (Exception problem)
   {
    out.printf(
      "Error while verifying option \"%s\" in the resource bundle \"%s\"%n",
      id, USAGE_BUNDLE
    );
    problem.printStackTrace(out);
    System.exit(-1);   
   }
  }
 }

 protected void listOptions(Collection<Option> options, PrintWriter out)
 {
  HelpFormatter formatter = getHelpFormatter();
  for (Option option : options)
  {
   out.printf("    %s%s%n", formatOptionHeader(option), formatArguments(option));
   String summary = "## NO DESCRIPTION PROVIDED ##";
   try
   {
    String id = getOptionId(option);
    summary = getResources().getString(USAGE_BUNDLE, getClass(), id);
   }
   catch (MissingResourceException missing) {}
   out.print("        ");
   formatter.printWrapped(out, OUTPUT_WIDTH - 8, 8, summary);
  }
  if (!options.isEmpty())
   out.println();
 }

 protected static String getOptionId(Option option)
 {
  return option.hasLongOpt() ? LONG_OPT_PREFIX + option.getLongOpt() : OPT_PREFIX + option.getOpt();
 }

 protected static String getArgumentId(Option option)
 {
  return "arg" + getOptionId(option);
 }

 protected String formatArguments(Option option)
 {
  if (!option.hasArg())
   return "";
  else
  {
   String arg = "#unknown#";
   try
   {
    String id = getArgumentId(option);
    arg = getResources().getString(USAGE_BUNDLE, getClass(), id);
   }
   catch (MissingResourceException missing) {}
   return ' ' + arg;
  }
 }

 protected static String formatOptionHeader(Option option)
 {
  String title;
  if (null == option.getOpt())
   title = LONG_OPT_PREFIX + option.getLongOpt();
  else
   title = OPT_PREFIX + option.getOpt() 
   + (option.hasLongOpt() ? ", " + LONG_OPT_PREFIX + option.getLongOpt() : "");
  return title;
 }

 protected Resources getResources()
 {
  return resources;
 }

 protected HelpFormatter getHelpFormatter()
 {
  if (null == formatter)
   formatter = new HelpFormatter();
  return formatter;
 }

 protected static class OptionComparator implements Comparator<Option>
 {
  final private String getKey(Option option)
  {
      String opt = option.getOpt();
      return opt == null ? option.getLongOpt() : opt;
  }

  public int compare(Option opt1, Option opt2)
  {
   return getKey(opt1).compareTo(getKey(opt2));
  }
 }

 protected static final String USAGE_BUNDLE = "usage";
 protected static final OptionComparator OPTION_COMPARATOR = new OptionComparator();
 protected static final String LONG_OPT_PREFIX = "--";
 protected static final String OPT_PREFIX = "-";
 protected static final int OUTPUT_WIDTH = 80;

 private String getMessage(String id)
 {
  return getResources().getMessage(Syntax.class, id);
 }

 private Resources resources;
 private HelpFormatter formatter;

 protected static final String HELP_COMMAND = "help"; // -?

 protected static final String DROP_COMMAND = "drop";

 protected static final String LIST_COMMAND = "list"; // -l

 protected static final String HISTORY_COMMAND = "history"; // -h

 protected static final String LOG_COMMAND = "log";

 protected static final String PURGE_COMMAND = "purge";

 protected static final String RESTORE_COMMAND = "restore"; // -r

 protected static final String UNDO_COMMAND = "undo"; // -u

 protected static final String SYNC_COMMAND = "sync"; // -s

 protected static final String NOSYNC_OPTION = "nosync"; // -N

 protected static final String CREATE_OPTION = "create";

 protected static final String ENCRYPT_OPTION = "encrypt"; // -E

 protected static final String NOBANNER_OPTION = "nobanner";

 protected static final String LOCAL_OPTION = "local"; // -C

 // an argument to ENCRYPT_OPTION
 protected static final String CIPHER_OPTION = "--cipher";

 // an argument to LOCAL_OPTION
 protected static final String DEFAULT_OPTION = "--default";

 // an argument to FILTER_OPTION
 protected static final String INVERT_OPTION = "--invert";

 // an argument to DROP FILTER command
 protected static final String FORCE_OPTION = "--force";

 protected static final String COMPRESSION_OPTION = "compress";

 protected static final String LOB_SIZE_OPTION = "lob-size";

 protected static final String MEDIUM_OPTION = "medium"; // -d

 protected static final String FILE_ID_OPTION = "fn";

 protected static final String VERSION_ID_OPTION = "vn";

 protected static final String AS_OF_OPTION = "as-of"; // -a

 protected static final String FILTER_OPTION = "filter"; // -F

 protected static final String SAVE_OPTION = "save"; // -o

 protected static final String LOAD_OPTION = "load";

 protected static final String SET_OPTION = "set";

 protected static final String DEFAULT_ACTION_OPTION = "default-action"; // -A

 protected static final String VERBOSE_OPTION = "verbose"; // -v

 protected static final String ALLOWED_TIMESTAMP_DISCREPANCY_OPTION = "allow-time-diff";

 protected static final String CUMULATIVE_DELTA_SIZE_OPTION = "cds";

 protected static final String DELTA_CHAIN_SIZE_OPTION = "dcs";

 protected static final String SCHEMA_EVOLUTION_OPTION = "upgrade-db";

 /**
  * NOTE: DO NOT add commands' descriptions here. Place them in the
  * <code>usage.properties</code> resource file instead. All argument names MUST BE EMPTY.
  */
 @SuppressWarnings("static-access")
 private static final OptionGroup COMMAND_OPTION_GROUP = new OptionGroup()

   .addOption(
     OptionBuilder.withLongOpt(PURGE_COMMAND)
     .hasOptionalArgs(2).withArgName("").create())

   .addOption(
     OptionBuilder
       .withLongOpt(DROP_COMMAND)
       .hasOptionalArgs()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder.withLongOpt(LIST_COMMAND).hasOptionalArg()
     	.withArgName("")
     	.create('l'))

   .addOption(
     OptionBuilder
       .withLongOpt(HISTORY_COMMAND)
       .hasOptionalArg()
       .withArgName("")
       .create('h'))

   .addOption(
     OptionBuilder
     .withLongOpt(RESTORE_COMMAND)
     .hasOptionalArg()
     .withArgName("")
     .create('r'))

   .addOption(
     OptionBuilder
     .withLongOpt(UNDO_COMMAND)
     .hasOptionalArg()
     .withArgName("")
     .create('u'))

   .addOption(
     OptionBuilder
     .withLongOpt(SYNC_COMMAND)
     .hasOptionalArg()
     .withArgName("")
     .create('s'))

   .addOption(
     OptionBuilder.withLongOpt(LOG_COMMAND).hasOptionalArgs(2)
     .withArgName("").create())

   .addOption(
     OptionBuilder.withLongOpt(HELP_COMMAND).create('?'));

 /**
  * NOTE: DO NOT add option descriptions here. Place them in the
  * <code>usage.properties</code> resource file instead. All argument names MUST BE EMPTY.
  */
 @SuppressWarnings("static-access")
 private static final Options OPTIONS = new Options()

   .addOption(
     OptionBuilder.withLongOpt(CREATE_OPTION).create())

   .addOption(
     OptionBuilder.withLongOpt(SAVE_OPTION).hasArg().withArgName("")
       .create('o'))

   .addOption(
     OptionBuilder.withLongOpt(NOSYNC_OPTION).create('N'))

   .addOption(
     // TODO: consider implementing:
//	 + " The 'file' argument value followed by a file"
//	 + " name tells data-bag to read the encryption key from that file. Since the encryption key"
//	 + " is read as plain text, you have to make sure that access to that file is restricted."
//	 + " You may want to store the key file in an encrypted format if your system supports that."
//	 + " Data-bag will use the entire file as a password string, including all end-of-line sequences"
//	 + " in it."
     OptionBuilder
       .withLongOpt(ENCRYPT_OPTION)
       .hasOptionalArgs()
       .withArgName("")
       .create('E'))

   .addOption(
     OptionBuilder
       .withLongOpt(FILTER_OPTION)
       .hasOptionalArgs(2)
       .withArgName("")
       .create('F'))

   .addOption(
     OptionBuilder.withLongOpt(VERBOSE_OPTION).hasOptionalArg()
     .withArgName("").create('v'))

   .addOption(
     OptionBuilder.withLongOpt(FILE_ID_OPTION).hasArg().withArgName("")
       .withType(Number.class).create())

   .addOption(
     OptionBuilder.withLongOpt(VERSION_ID_OPTION).hasArg().withArgName("")
       .withType(Number.class).create())

   .addOption(
     OptionBuilder.withLongOpt(AS_OF_OPTION).hasOptionalArgs(2)
     	.withArgName("").create('a'))

   .addOption(
     OptionBuilder
       .withLongOpt(LOAD_OPTION)
       .hasArg()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(SET_OPTION)
       .hasArgs(2)
       .withArgName("")
       .create())

   .addOption(OptionBuilder.withLongOpt(NOBANNER_OPTION).create())

   .addOption(
     OptionBuilder.withLongOpt(MEDIUM_OPTION).withArgName("").hasOptionalArgs(2).create('d'))

   .addOption(
     OptionBuilder.withLongOpt(DEFAULT_ACTION_OPTION).hasArg()
     .withArgName("").create('A'))

   .addOption(
     OptionBuilder.withLongOpt(LOCAL_OPTION).hasOptionalArgs(2)
     .withArgName("").create('C'))

   .addOption(
     OptionBuilder
       .withLongOpt(ALLOWED_TIMESTAMP_DISCREPANCY_OPTION)
       .hasArg()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(CUMULATIVE_DELTA_SIZE_OPTION)
       .hasArg()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(DELTA_CHAIN_SIZE_OPTION)
       .hasArg()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(COMPRESSION_OPTION)
       .hasArg()
       .withArgName("")
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(LOB_SIZE_OPTION)
       .hasArg()
       .withArgName("")
       .withType(Number.class)
       .create())

   .addOption(
     OptionBuilder
       .withLongOpt(SCHEMA_EVOLUTION_OPTION)
       .create())

   .addOptionGroup(COMMAND_OPTION_GROUP);
}
