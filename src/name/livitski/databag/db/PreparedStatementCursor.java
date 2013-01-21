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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Implements a {@link Cursor} backed by results of a
 * prepared statement execution.
 */
public abstract class PreparedStatementCursor<T> extends
  PreparedStatementHandler implements Cursor<T>
{
 protected abstract T loadInstance(ResultSet results) throws SQLException, DBException;

 public T next() throws DBException
 {
  try
  {
   if (results.next())
    return loadInstance(results);
   else
    return null;
  } catch (SQLException e)
  {
   throw new DBException("Error " + legend(), e);
  }
 }

 public void close() throws DBException
 {
  if (null != savedStmt)
   try
   {
    savedStmt.close();
   }
   catch (SQLException e)
   {
    throw new DBException("Error closing the cursor after " + legend(), e);
   }
   finally
   {
    results = null;
    savedStmt = null;
   }
 }

 public PreparedStatementCursor(Manager mgr, String sql)
 {
  super(mgr, sql);
 }

 @Override
 public String toString()
 {
  return "cursor for " + legend();
 }

 @Override
 protected void handleResults(ResultSet rs)
 {
  results = rs;
 }

 @Override
 protected void close(Statement stmt) throws SQLException
 {
  if (null != savedStmt)
   try { savedStmt.close(); } catch (SQLException ignored) {}
  savedStmt = stmt;
 }

 private ResultSet results;
 private Statement savedStmt;
}
