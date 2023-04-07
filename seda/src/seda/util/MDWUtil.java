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


package seda.util;

import java.text.DecimalFormat;

/**
 * This class provides some generic utility functions.
 * 
 * @author Matt Welsh
 */
public class MDWUtil {

  static boolean nativeLibraryLoaded = false;
  static Object nativeLibraryLoadLock = new Object();
  static void loadNativeLibrary() {
    synchronized (nativeLibraryLoadLock) {
      if (!nativeLibraryLoaded) {
	try {
	  System.loadLibrary("MDWUtil");
	  nativeLibraryLoaded = true;
	} catch (Exception e) {
	  System.err.println("Cannot load MDWUtil shared library");
	}
      }
    }
  }

  private static DecimalFormat df;

  static {
    loadNativeLibrary();
    df = new DecimalFormat();
    df.applyPattern("#.####");
  }

  /**
   * Returns the current time in microseconds.
   */
  public static native long currentTimeUsec();

  /**
   * Cause the current thread to sleep for the given number of
   * microseconds. Returns immediately if the thread is interrupted, but
   * does not throw an exception.
   */
  public static native void usleep(long delay);

  /**
   * Format decimals to 4 digits only
   */
  public static String format(double val) {
    return new String(df.format(val));
  }

  /**
   * Cause the current thread to sleep for the given number of
   * milliseconds. Returns immediately if the thread is interrupted, but
   * does not throw an exception.
   */
  public static void sleep(long delay) {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException ie) {
      // Ignore
    }
  }

}
