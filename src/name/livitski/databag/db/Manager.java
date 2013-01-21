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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import name.livitski.tools.Logging;

/**
 * Manages access to the embedded database.
 * This class is not thread-safe and should not be used concurrently.
 */
public class Manager extends Logging
{
 /**
  * Looks up DAO object of a particular class associated with this connection.
  * Instantiates and registers such object if necessary. This method will
  * fail if the new DAO has to initialize its schema within an
  * {@link #isTransactionActive() active transaction}. To avoid such
  * failures, prepare the schema for all DAO classes used within a
  * transaction by calling this method for them before that transaction starts. 
  * @param clazz the DAO class to look for
  * @return an object of requested class associated with this connection
  * @throws DBException if there is no open connection or 
  * there is a problem initializing database schema
  */
 @SuppressWarnings("unchecked")
 public <D extends AbstractDAO> D findDAO(Class<D> clazz)
 	throws DBException
 {
  if (null == jdbc)
   throw new DBException("Cannot attach DAO to " + this + ": database not open");
  D dao = (D)daoMap.get(clazz);
  if (null == dao)
  {
    try
    {
     Constructor<?> ctr = clazz.getDeclaredConstructor(this.getClass());
     ctr.setAccessible(true);
     dao = (D) ctr.newInstance(this);
     for (Class<? extends AbstractDAO> dep : dao.dependencies())
      findDAO(dep);
     dao.updateSchema();
     daoMap.put(clazz, dao);
    } catch (NoSuchMethodException e)
    {
     throw new ExceptionInInitializerError("DAO " + clazz
       + " does not have a single argument constructor that accepts a "
       + getClass().getName());
    } catch (InstantiationException e)
    {
     throw new ExceptionInInitializerError("DAO " + clazz + " is abstract");
    } catch (IllegalAccessException e)
    {
     throw (Error) new ExceptionInInitializerError("No access to DAO " + clazz
       + " constructor").initCause(e);
    } catch (InvocationTargetException e)
    {
     throw (Error) new ExceptionInInitializerError(
       "Exception in constructor of DAO " + clazz).initCause(e
       .getTargetException());
    }
  }
  return dao;
 }

 /**
  * Begins an atomic transaction in the database. Returned object
  * represents an {@link Transaction#isActive() active transaction}.
  * @return new transaction handle
  * @throws DBException if there is an error accessing the database or
  * starting transaction
  */
 public  Transaction beginTransaction()
	throws DBException
 {
  if (null == recentTxn)
   try
   {
    log().finest("SET AUTOCOMMIT OFF");
    getJdbc().setAutoCommit(false);
    recentTxn = new Transaction(this);
   }
   catch (SQLException e)
   {
    throw new DBException("Could not disable implicit transactions", e);
   }
  else
   try
   {
    recentTxn = recentTxn.nestedTransaction();
   }
   catch (SQLException e)
   {
    throw new DBException("Could not begin a nested transaction", e);
   }
  return recentTxn;
 }

 /**
  * Tells whether there is an active transaction with this manager.
  * @see #beginTransaction()
  */
 public boolean isTransactionActive()
 {
  return null != getActiveTransaction();
 }

 public void open()
 	throws DBException
 {
  if (null != jdbc) return;
  if (!location.isDirectory())
   throw new DBException("There is no valid " + this);
  Throwable status = new RuntimeException("No database names have been configured");
  Logger log = log();
  for (int legacyIndex = 0; DB_NAMES.length > legacyIndex; )
  {
   String url = baseURL(false, legacyIndex) + MUSTEXIST_SUFFIX;
   boolean isLast = DB_NAMES.length <= ++legacyIndex;
   log.finer("Trying database at \"" + url + "\" ...");
   try
   {
    establishConnection(url, !isLast);
    status = null;
    break;
   }
   catch (SQLException fail)
   {
    if (org.h2.constant.ErrorCode.DATABASE_NOT_FOUND_1 != fail.getErrorCode())
    {
     status = fail;
     if (!isLast)
      clearPassword();
     break;
    }
    else if (!(status instanceof SQLException))
    {
     status = fail;
    }
   }
  }
  if (null != status)
   throw new DBException("Open failed for " + this, status);
 }

 public void create()
 	throws DBException
 {
  if (null != jdbc)
   throw new IllegalStateException("Cannot re-create open " + this);
  if (location.exists())
   throw new DBException("Directory or file " + location + " already exists, cannot overwrite");
  if (!location.mkdir())
   throw new DBException("Create failed for database directory " + location);
  String url = baseURL(true, 0);
  final Logger log = log();
  try
  {
   establishConnection(url, false);
   log.info("Created database " + url);
  }
  catch (SQLException fail)
  {
   throw new DBException("Create failed for " + this + " (" + url + ')', fail);
  }
 }

 public java.io.File getLocation()
 {
  return location;
 }

 public void setLocation(java.io.File location)
 {
  if (null != jdbc)
   throw new IllegalStateException("Cannot change location of an open " + this);
  this.location = location;
 }
 
 public String getCompressionType()
 {
  return null == compressionType ? DEFAULT_COMPRESSION_TYPE : compressionType;
 }

 public void setCompressionType(String compressionType)
 {
  this.compressionType = compressionType;
 }

 public int getInPlaceLobThreshold()
 {
  return 0 > inPlaceLobThreshold ? DEFAULT_LOB_THRESHOLD : inPlaceLobThreshold;
 }

 public void setInPlaceLobThreshold(int inPlaceLobThreshold)
 {
  this.inPlaceLobThreshold = inPlaceLobThreshold;
 }

 /**
  * Tells whether or not this manager allows its DAOs to upgrade the
  * database schema.
  * @see #setSchemaEvolutionAllowed(boolean)
  */
 public boolean isSchemaEvolutionAllowed()
 {
  return schemaEvolutionAllowed;
 }

 /**
  * Enables or disables {@link AbstractDAO#updateSchema() schema evolution}
  * by this database manager's DAOs. 
  */
 public void setSchemaEvolutionAllowed(boolean schemaEvolutionAllowed)
 {
  this.schemaEvolutionAllowed = schemaEvolutionAllowed;
 }

 /**
  * Returns the algorithm this database is encrypted with if
  * {@link #isEncryptionEnabled() encryption is enabled}.
  * @return the cipher (encryption algorithm) name
  */
 public String getCipher()
 {
  return null == cipher ? DEFAULT_CIPHER : cipher;
 }

 /**
  * Changes the algorithm used to encrypt this database.
  * By default, AES algorithm is used if 
  * {@link #isEncryptionEnabled() encryption is enabled}.
  * You must call this method before {@link #create() creating}
  * or {@link #open() opening} a database.
  * @param cipher the algorithm name to set, <code>"AES"</code>
  * or <code>"XTEA"</code>
  */
 public void setCipher(String cipher)
 {
  if (null != jdbc)
   throw new IllegalStateException("The database is already open");
  this.cipher = cipher;
 }

 /**
  * Tells whether encryption is enabled for this database.
  * To enable or disable encryption, call {@link #setEncryption(char[])}
  * before {@link #create() creating} or {@link #open() opening} a database.
  */
 public boolean isEncryptionEnabled()
 {
  return null != encryptionPassword;
 }

 /**
  * Enables or disables encryption and assigns the password for
  * database access. You must call this method before
  * {@link #create() creating} or {@link #open() opening} a database.
  * If either of these operations fails, the password is erased.
  * You will have to set it again before retrying.
  * @param encryptionPassword the password to encrypt and access
  * the database with or <code>null</code> to disable encryption.
  * Note that this method makes a copy of the argument and holds
  * it until you connect to the database. 
  */
 public void setEncryption(char[] encryptionPassword)
 {
  if (null != jdbc)
   throw new IllegalStateException("The database is already open");
  if (null == encryptionPassword)
  {
   // no encryption - clear prior password and set to null
   if (null != this.encryptionPassword)
    Arrays.fill(this.encryptionPassword, '\0');
   this.encryptionPassword = null;
  }
  else if (0 == encryptionPassword.length)
   throw new IllegalArgumentException("Encrypted database cannot have an empty password.");
  else
  {
   // encryption on - copy the password, make sure there are no spaces, and append a space
   final int length = encryptionPassword.length;
   this.encryptionPassword = new char[length];
   int errorPos = copyPassword(this.encryptionPassword, encryptionPassword);
   if (0 <= errorPos)
   {
    Arrays.fill(this.encryptionPassword, '\0');
    this.encryptionPassword = new char[0];
    throw new IllegalArgumentException(
      "Character code " + (int)encryptionPassword[errorPos]
	+ " is not allowed in a password, but found at position " + errorPos);
   }
  }
 }

 public void close()
 	throws DBException
 {
  if (null != jdbc)
   try
   {
    jdbc.close();
    jdbc = null;
   } catch (SQLException e)
   {
    throw new DBException("Close failed for " + this, e);
   }
 }

 @Override
 public String toString()
 {
  return (isEncryptionEnabled() ? "encrypted " : "") + "database at " + location;
 }

 public Manager()
 {
  try { Class.forName(DRIVER); }
  catch (ClassNotFoundException noclass)
  {
   throw new IllegalStateException("Missing dependency class " + DRIVER
     + ". Please check your application configuration");
  }
 }

 public static String currentAndLegacyDBNamesSQLSchemaList()
 {
  StringBuilder buf = new StringBuilder();
  for (String name : DB_NAMES)
  {
   if (0 < buf.length())
    buf.append(", ");
   buf.append('\'').append(name.toUpperCase()).append('\'');
  }
  return buf.toString();
 }

 public static final int DEFAULT_LOB_THRESHOLD = 3500;
 public static final String DEFAULT_COMPRESSION_TYPE = "DEFLATE";
 public static final String DEFAULT_CIPHER = "AES";
 public static final String DB_NAMES[] = {
  "databag", // the current name is always first, legacy names follow
  "tote"
 };
 public static final String DEFAULT_SCHEMA= "PUBLIC";

 protected final Connection getJdbc()
 {
  return jdbc;
 }

 @Override
 protected Logger log()
 {
  return super.log();
 }

 protected Transaction getActiveTransaction()
 {
  return recentTxn;
 }

 protected void unwindTransaction(final Transaction level)
 	throws DBException
 {
  if (null == level)
   recentTxn = null;
  else
  {
   if (!level.contains(recentTxn))
    throw new DBException("Cannot unwind to inactive " + level);
   recentTxn = level;
  }
 }

 protected boolean isTransactionActive(Transaction txn)
 {
  return txn.contains(recentTxn);
 }

 protected void txnToCache(Transaction txn, boolean commit)
 	throws DBException
 {
  for (AbstractDAO dao : daoMap.values())
   if (dao instanceof CachingDAO<?, ?>)
    ((CachingDAO<?, ?>)dao).doneTxn(txn, commit);
 }

 static final String DRIVER = "org.h2.Driver";
 static final String URL_PREFIX = "jdbc:h2:file:";
 static final String SERIALIZABLE_SUFFIX = ";LOCK_MODE=1";
 static final String MUSTEXIST_SUFFIX = ";IFEXISTS=TRUE";
 static final String INPLACE_LOB_LENGTH_SUFFIX = ";MAX_LENGTH_INPLACE_LOB=";
 static final String COMPRESS_LOB_SUFFIX = ";COMPRESS_LOB=";
 static final String ENCRYPT_SUFFIX = ";CIPHER=";

 private String baseURL(boolean create, int legacyVersion)
 {
  StringBuilder base = new StringBuilder(1024)
   .append(URL_PREFIX)
   .append(location.getAbsolutePath()).append(java.io.File.separator).append(DB_NAMES[legacyVersion])
   .append(SERIALIZABLE_SUFFIX);
  if (create || null != compressionType)
   base.append(COMPRESS_LOB_SUFFIX).append(getCompressionType());
  if (create || 0 <= inPlaceLobThreshold)
   base.append(INPLACE_LOB_LENGTH_SUFFIX).append(getInPlaceLobThreshold());
  if (isEncryptionEnabled())
   base.append(ENCRYPT_SUFFIX).append(getCipher());
  return base.toString();
 }

 private void establishConnection(String url, boolean willRetryOnFailure) throws SQLException
 {
  if (!isEncryptionEnabled())
   jdbc = DriverManager.getConnection(url);
  else if (0 == encryptionPassword.length)
   throw new IllegalStateException(
     "The password is no longer available. Please set the password again before reconnecting.");
  else
  {
   Throwable status = null;
   final int length = encryptionPassword.length;
   final char[] passwordCopy = new char[length + 1];
   try
   {
    int errorPos = copyPassword(passwordCopy, encryptionPassword);
    if (0 <= errorPos)
     throw new IllegalArgumentException(
       "Character code " + (int)encryptionPassword[errorPos]
 	+ " is not allowed in a password, but found at position " + errorPos);
    passwordCopy[length] = ' ';
    // prepare the file password
    Properties properties = new Properties();
    // this violates the Properties contract, but does not leave copies of password in memory 
    properties.put("password", passwordCopy);
    jdbc = DriverManager.getConnection(url, properties);
   }
   catch (Throwable failure)
   {
    status = failure;
    if (failure instanceof Error)
     throw (Error)failure;
    if (failure instanceof SQLException)
     throw (SQLException)failure;
    if (failure instanceof RuntimeException)
     throw (RuntimeException)failure;    
    throw new Error("Unexpected object type thrown while connecting to \"" + url + '"', failure);    
   }
   finally
   {
    Arrays.fill(passwordCopy, '\0');
    if (!willRetryOnFailure || null == status)
     clearPassword();
   }
  }
 }

 private void clearPassword()
 {
  if (null != encryptionPassword)
   Arrays.fill(encryptionPassword, '\0');
  encryptionPassword = new char[0];
 }

 /**
  * @return the index of a character that violated the password's constraints,
  * or a negative value on success
  */
 private int copyPassword(char[] buffer, char[] encryptionPassword)
 {
  final int length = encryptionPassword.length;
  char c;
  for (int i = length; 0 < i;)
  {
   c = encryptionPassword[--i]; 
   if (' ' == c)
    return i;
   else
    buffer[i] = c;
  }
  c = ' ';
  return -1;
 }

 private boolean schemaEvolutionAllowed;
 private String compressionType;
 private String cipher;
 private char[] encryptionPassword;
 private int inPlaceLobThreshold = -1;
 private java.io.File location;
 private Connection jdbc;
 private Transaction recentTxn;
 private Map<Class<? extends AbstractDAO>, AbstractDAO> daoMap
	= new HashMap<Class<? extends AbstractDAO>, AbstractDAO>();
}
