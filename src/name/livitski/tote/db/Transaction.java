/**
 *  Copyright (C) 2010-2012 Konstantin Livitski
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the Tote Project License.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Tote Project License for more details.
 *
 *  You should find a copy of the Tote Project License in the "tote.txt" file
 *  in the LICENSE directory of this package or repository.  If not, see
 *  <http://www.livitski.name/projects/tote/license>. If you have any
 *  questions or concerns, contact me at <http://www.livitski.name/contact>. 
 */
    
package name.livitski.tote.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.logging.Logger;

/**
 * Controls a transaction within a {@link Manager Tote database}.
 * Instances of this class are obtained from
 * {@link Manager#beginTransaction()} the database manager.
 * {@link #isActive() Active} transaction represented by an
 * instance of this class can be {@link #commit() committed}
 * or {@link #abort() aborted}. Transactions can be nested,
 * and method {@link #contains(Transaction)} allows callers
 * to test whether another transaction is nested within this
 * one, either directly or via other nested transactions.
 */
public class Transaction
{
 /**
  * Commits this transaction. Transaction becomes inactive once
  * committed.
  * @throws DBException if the transaction is not active or there
  * is an error accessing database or updating cache
  * @see #isActive()
  */
 public void commit()
 	throws DBException
 {
   mgr.unwindTransaction(parent);
   if (null == parent)
   {
    try
    {
     log().finest("COMMIT");
     getJdbc().commit();
     mgr.txnToCache(this, true);
    } catch (SQLException e)
    {
     throw new DBException("Could not commit the transaction", e);
    }
    try
    {
     getJdbc().setAutoCommit(true);
    }
    catch (SQLException e)
    {
     throw new DBException("Could not enable implicit transactions", e);
    }
   }
   else
    try
    {
     log().finest("Releasing " + this);
     getJdbc().releaseSavepoint(savepoint);
     // do not update cache since enclosing transaction may still abort
    }
    catch (SQLException e)
    {
     throw new DBException("Could not release " + this, e);
    }
 }

 /**
  * Aborts this transaction. Transaction becomes inactive once
  * aborted.
  * @throws DBException if the transaction is not active or there
  * is an error accessing database or updating cache
  * @see #isActive()
  */
 public void abort()
	throws DBException
 {
   mgr.unwindTransaction(parent);
   mgr.txnToCache(this, false);
   if (null == parent)
   {
    try
    {
     log().finest("ROLLBACK");
     getJdbc().rollback();
    } catch (SQLException e)
    {
     throw new DBException("Could not roll back " + this, e);
    }
    try
    {
     getJdbc().setAutoCommit(true);
    } catch (SQLException e)
    {
     throw new DBException("Could not enable implicit transactions", e);
    }
   }
   else
   {
    try
    {
     log().finest("Aborting " + this);
     getJdbc().rollback(savepoint);
    }
    catch (SQLException e)
    {
     throw new DBException("Could not roll back " + this, e);
    }
   }
 }

 /**
  * Tells whether this object represents an active transaction.
  * Transaction must be active to get
  * {@link #commit() committed} or {@link #abort() aborted}. 
  */
 public boolean isActive()
 {
  return mgr.isTransactionActive(this);
 }

 /**
  * Tests whether another transaction is nested within this
  * one, either directly or via other nested transactions.
  * Also returns true if the argument represents the
  * same transaction.
  * @param other a transaction to test for nesting
  * @return <code>true</code> if the argument represents
  * this transaction or a nested transaction
  */
 public boolean contains(Transaction other)
 {
  for (Transaction context = other; null != context; context = context.parent)
   if (context == this)
    return true;
  return false;
 }

 @Override
 public String toString()
 {
  if (null == parent)
   return "explicit transaction in " + mgr;
  else
  try
  {
   return "nested transaction " + savepoint.getSavepointId() + " in " + mgr;
  }
  catch (SQLException e)
  {
   return "unknown nested transaction in " + mgr;
  }
 }

 protected Transaction nestedTransaction()
 	throws SQLException
 {
  Savepoint savepoint = getJdbc().setSavepoint();
  log().finest("Began " + this);
  return new Transaction(mgr, savepoint, this);
 }

 protected final Transaction getParent()
 {
  return parent;
 }

 protected final Connection getJdbc()
 {
  return mgr.getJdbc();
 }

 protected final Logger log()
 {
  return mgr.log();
 }

 /**
  * Creates a transaction object without a savepoint to
  * denote a transaction that is not nested.
  */
 protected Transaction(Manager mgr)
 {
  this.mgr = mgr;
 }

 private Transaction(Manager mgr, Savepoint savepoint, Transaction parent)
 {
  this.savepoint = savepoint;
  this.parent = parent;
  this.mgr = mgr;
 }

 private Savepoint savepoint;
 private Transaction parent;
 private Manager mgr; 
}
