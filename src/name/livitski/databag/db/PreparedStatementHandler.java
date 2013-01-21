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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Implements the standard pattern of JDBC prepared statement usage.
 */
public abstract class PreparedStatementHandler extends StatementHandler
{
 public PreparedStatementHandler(Manager mgr, String sql)
 {
  super(mgr);
  this.sql = sql;
 }

 protected void handleResults(ResultSet rs)
 	throws SQLException, DBException
 {
 }

 protected void handleUpdate(PreparedStatement stmt)
	throws DBException, SQLException
 {
  handleUpdate(stmt.getUpdateCount());
 }

 protected void handleUpdate(int count)
	throws DBException
 {
  if (0 == count)
   noMatchOnUpdate();
 }

 protected void noMatchOnUpdate()
	throws DBException
 {
 }

 protected void bindParameters(PreparedStatement stmt)
 	throws SQLException
 {
 }


 protected PreparedStatement createStatement()
 	throws SQLException
 {
  log().finest(sql);
  PreparedStatement stmt = getJdbc().prepareStatement(sql);
  bindParameters(stmt);
  return stmt;
 }

 @Override
 protected void handleStatement(Statement gstmt)
 	throws SQLException, DBException
 {
  PreparedStatement stmt = (PreparedStatement)gstmt;
  if (stmt.execute())
   handleResults(stmt.getResultSet());
  else
   handleUpdate(stmt);
 }

 protected String sql;
}
