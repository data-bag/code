/**
 *  Copyright 2010-2013 Konstantin Livitski
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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the standard pattern of JDBC statement usage.
 */
public abstract class StatementHandler
{
 protected abstract void handleStatement(Statement stmt) 
	throws SQLException, DBException;

 protected abstract String legend();

 public void execute()
 	throws DBException
 {
  Statement stmt = null;
  try
  {
   stmt = createStatement();
   handleStatement(stmt);
  }
  catch (SQLException fail)
  {
   if (null != fail.getSQLState() && fail.getSQLState().startsWith("23"))
    throw new ConstraintViolationException(fail.getMessage(), legend(), fail);
   throw new DBException("Error " + legend() + " in " + mgr, fail);
  }
  finally
  {
   if (null != stmt)
    try {
     close(stmt);
    }
    catch (SQLException thrown)
    {
     log().log(Level.FINE, "Error closing statement handle after " + legend() + " in " + mgr, thrown);
    }
  }
 }

 @Override
 public String toString()
 {
  return "statement for " + legend();
 }

 public StatementHandler(Manager mgr)
 {
  super();
  this.mgr = mgr;
 }

 protected void close(Statement stmt) throws SQLException
 {
  stmt.close();
 }

 protected Statement createStatement() throws SQLException
 {
  return getJdbc().createStatement();
 }

 protected final Logger log()
 {
  return mgr.log();
 }

 protected final Connection getJdbc()
 {
  return mgr.getJdbc();
 }

 protected Manager mgr;
}
