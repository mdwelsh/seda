package seda.sandStorm.api;

import java.util.Enumeration;

/**
 * @version 	1.0
 * @author Zsombor Gegesy
 */
public interface SandstormConfigIF {

  /**
   * Return the configuration option associated with the given key
   * as a String. Returns null if not set.
   */
  public String getString(String key);

  /**
   * Return the configuration option associated with the given key
   * as a String. Returns default if not set.
   */
  public String getString(String key, String defaultval);

  /**
   * Return the configuration option associated with the given key
   * as a boolean. Returns false if not set.
   */
  public boolean getBoolean(String key);

  /**
   * Return the configuration option associated with the given key
   * as a boolean. Returns default if not set.
   */
  public boolean getBoolean(String key, boolean defaultval);

  /**
   * Return the configuration option associated with the given key
   * as an int. Returns -1 if not set or if the value of the key cannot
   * be expressed as an int.
   */
  public int getInt(String key);

  /**
   * Return the configuration option associated with the given key
   * as an int. Returns default if not set or if the value of the
   * key cannot be expressed as an int.
   */
  public int getInt(String key, int defaultval);

  /**
   * Return the configuration option associated with the given key
   * as a double. Returns -1 if not set or if the value of the key cannot
   * be expressed as a double.
   */
  public double getDouble(String key);

  /**
   * Return the configuration option associated with the given key
   * as a double. Returns default if not set or if the value of the
   * key cannot be expressed as a double.
   */
  public double getDouble(String key, double defaultval);

  /**
   * Return an Enumeration of the stages specified by this SandstormConfig.
   */
  public Enumeration getStages();

  /**
   * Return an enumeration of the keys matching the given prefix.
   * A given key maps onto a set of child keys if it ends in a
   * "." character (that is, it is an internal node within the tree).
   * A key not ending in "." is a terminal node and maps onto a
   * value that may be obtained using getString, getInt, or getDouble.
   */
  public Enumeration getKeys(String prefix);

  /**
   * Return an enumeration of the top-level keys in this configuration.
   */
  public Enumeration getKeys() ;

}
