/* 
 * Copyright (c) 2001 by Matt Welsh and The Regents of the University of 
 * California. All rights reserved.
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without written agreement is
 * hereby granted, provided that the above copyright notice and the following
 * two paragraphs appear in all copies of this software.
 * 
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
 * OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
 * CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Matt Welsh <mdw@cs.berkeley.edu>
 * 
 */

package seda.sandStorm.api;

/**
 * ConfigDataIF is used to pass configuration arguments to stages.
 * When a stage is initialized, a ConfigDataIF is passed to its
 * 'init()' method.
 *
 * @author Matt Welsh
 */
public interface ConfigDataIF {

  /**
   * The default value for a string key with no other specified value.
   */
  public static final String SET = "set";

  /**
   * Returns true if the given key is set in the configuration.
   */
  public boolean contains(String key);

  /**
   * Get the string value corresponding to the given configuration key.
   * This is the basic way for a stage to retrieve its initialization
   * arguments. Returns null if not set.
   */
  public String getString(String key);

  /**
   * Get the integer value corresponding to the given configuration key.
   * This is the basic way for a stage to retrieve its initialization
   * arguments. Returns -1 if not set or if the value is not an integer.
   */
  public int getInt(String key);

  /**
   * Get the double value corresponding to the given configuration key.
   * This is the basic way for a stage to retrieve its initialization
   * arguments. Returns -1.0 if not set or if the value is not a double.
   */
  public double getDouble(String key);

  /**
   * Get the boolean value corresponding to the given configuration key.
   * This is the basic way for a stage to retrieve its initialization
   * arguments. Returns false if not set.
   */
  public boolean getBoolean(String key);

  /**
   * Get the value corresponding to the given configuration key as a
   * list of Strings. Returns null if not set.
   */
  public String[] getStringList(String key);

  /**
   * Set the given configuration key to the given string value.
   */
  public void setString(String key, String val);

  /**
   * Set the given configuration key to the given integer value.
   */
  public void setInt(String key, int val);

  /**
   * Set the given configuration key to the given double value.
   */
  public void setDouble(String key, double val);

  /**
   * Set the given configuration key to the given boolean value.
   */
  public void setBoolean(String key, boolean val);

  /**
   * Set the value corresponding to the given configuration key as a
   * list of Strings. 
   */
  public void setStringList(String key, String values[]);

  /**
   * Return a handle to the system manager.
   * The system manager can (among other things) be used to access
   * other stages in the system.
   *
   * @see ManagerIF
   */
  public ManagerIF getManager();

  /**
   * Return the StageIF for this stage.
   * The StageIF can be used (among other things) to access the
   * event queues for this stage.
   *
   * @see StageIF
   */
  public StageIF getStage();

  /**
   * Used to set the StageIF when initializing a ConfigDataIF.
   * This is an internal interface and not for use by applications. 
   */
  public void setStage(StageIF stage);

}
