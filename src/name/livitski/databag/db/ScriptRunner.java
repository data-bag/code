/**
 *  Copyright 2010-2014 Stan Livitski
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
    
package name.livitski.databag.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

public class ScriptRunner extends StatementHandler
{
 public ScriptRunner(Manager mgr, Object[] script, String legend)
 {
  super(mgr);
  this.script = script;
  this.legend = legend;
 }

 @Override
 protected void handleStatement(Statement stmt)
 	throws SQLException, DBException
 {
  this.stmt = stmt;
  runScript(Arrays.asList(script));
  this.stmt = null;
 }

 private void runScript(Iterable<?> script) throws SQLException, DBException
 {
  for (Object item : script)
  {
   if (item instanceof String)
   {
    String sql = (String)item;
    log().finest(sql);
    stmt.execute(sql);
   }
   else if (item instanceof StatementHandler && this != item)
    ((StatementHandler)item).execute();
   else if (item instanceof Iterable<?>)
    runScript((Iterable<?>)item);
   else if (item instanceof Object[])
    runScript(Arrays.asList((Object[])item));
   else
    throw new IllegalArgumentException(item + (null == item ? "" : " of " + item.getClass()));
  }
 }

 @Override
 protected String legend()
 {
  return legend;
 }

 protected String legend;
 private Statement stmt;
 private final Object[] script;
}