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

package seda.sandStorm.internal;

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.StageIF;
import seda.sandStorm.main.SandstormConfig;

import java.io.IOException;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * ConfigData is used to pass configuration arguments into various
 * components. 
 */
public class ConfigData implements ConfigDataIF {
  private static final boolean DEBUG = false;

  private Hashtable vals;
  private ManagerIF mgr;
  private StageIF stage;

  /**
   * Create a ConfigData with the given manager and no argument list.
   */
  public ConfigData(ManagerIF mgr) {
    this.mgr = mgr;
    this.vals = new Hashtable(1);
  }

  /**
   * Create a ConfigData with the given manager and argument list.
   */
  public ConfigData(ManagerIF mgr, Hashtable args) {
    this.mgr = mgr;
    this.vals = args;
    if (vals == null) vals = new Hashtable(1);
  }

  /**
   * Create a ConfigData with the given manager and argument list,
   * specified as an array of strings of the form "key=value".
   *
   * @throws IOException If any of the strings to not match the 
   *   pattern "key=value".
   */
  public ConfigData(ManagerIF mgr, String args[]) throws IOException {
    this.mgr = mgr;
    this.vals = stringArrayToHT(args);
    if (vals == null) vals = new Hashtable(1);
  }

  /**
   * Returns true if the given key is set.
   */
  public boolean contains(String key) {
    if (vals.get(key) != null) return true;
    else return false;
  }

  /**
   * Get the string value corresponding to the given key.
   * Returns null if not set.
   */
  public String getString(String key) {
    return (String)vals.get(key);
  }

  /**
   * Get the integer value corresponding to the given key.
   * Returns -1 if not set or if the value is not an integer.
   */
  public int getInt(String key) {
    String val = (String)vals.get(key);
    if (val == null) return -1;
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
  
  /**
   * Get the double value corresponding to the given key.
   * Returns -1.0 if not set or if the value is not a double.
   */
  public double getDouble(String key) {
    String val = (String)vals.get(key);
    if (val == null) return -1;
    try {
      return new Double(val).doubleValue();
    } catch (NumberFormatException e) {
      return -1.0;
    }
  }

  /**
   * Get the boolean value corresponding to the given key.
   * Returns false if not set.
   */
  public boolean getBoolean(String key) {
    String val = (String)vals.get(key);
    if (val == null) return false;
    else if (val.equals("true") || val.equals("TRUE")) return true;
    else return false;
  }

  /**
   * Get the string list value corresponding to the given key.
   * Returns null if not set.
   */
  public String[] getStringList(String key) {
    String ret[];
    String val = (String)vals.get(key);
    if (val == null) return null;
    StringTokenizer st = new StringTokenizer(val, SandstormConfig.LIST_ELEMENT_DELIMITER);
    Vector v = new Vector(1);
    while (st.hasMoreElements()) {
      v.addElement(st.nextElement());
    }
    ret = new String[v.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = (String)v.elementAt(i);
    }
    return ret;
  }

  /**
   * Set the given key to the given string value.
   */
  public void setString(String key, String val) {
    vals.put(key, val);
  }

  /**
   * Set the given key to the given integer value.
   */
  public void setInt(String key, int val) {
    vals.put(key, Integer.toString(val));
  }

  /**
   * Set the given key to the given double value.
   */
  public void setDouble(String key, double val) {
    vals.put(key, Double.toString(val));
  }

  /**
   * Set the given key to the given boolean value.
   */
  public void setBoolean(String key, boolean val) {
    vals.put(key, (val == true)?"true":"false");
  }

  /**
   * Set the given key to the given string list value.
   */
  public void setStringList(String key, String valarr[]) {
    String s = "";
    for (int i = 0; i < valarr.length; i++) {
      s += valarr[i];
      if (i != valarr.length-1) s += SandstormConfig.LIST_ELEMENT_DELIMITER;
    }
    vals.put(key, s);
  }

  /**
   * Return the local manager.
   */
  public ManagerIF getManager() {
    return mgr;
  }

  /**
   * Return the stage for this ConfigData.
   */
  public StageIF getStage() {
    return stage;
  }

  // Used to set stage after creating wrapper
  public void setStage(StageIF stage) {
    this.stage = stage;
  }

  // Used to reset manager after creating stage 
  // (for proxying the manager)
  void setManager(ManagerIF mgr) {
    this.mgr = mgr;
  }

  // Convert an array of "key=value" strings to a Hashtable
  private Hashtable stringArrayToHT(String arr[]) throws IOException {
    if (arr == null) return null;
    Hashtable ht = new Hashtable(1);
    for (int i = 0; i < arr.length; i++) {
      StringTokenizer st = new StringTokenizer(arr[i], "=");
      String key;
      String val;
      try {
	key = st.nextToken();
	val = st.nextToken();
	while (st.hasMoreTokens()) val += "="+st.nextToken();
      } catch (NoSuchElementException e) {
	throw new IOException("Could not convert string '"+arr[i]+"' to key=value pair");
      }
      ht.put(key, val);
    }
    return ht;
  }

}
