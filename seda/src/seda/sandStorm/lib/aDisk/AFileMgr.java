/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
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

package seda.sandStorm.lib.aDisk;

import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.internal.SystemManagerIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.main.Sandstorm;
import seda.sandStorm.main.SandstormConfig;

/**
 * The AFileMgr is an internal class used to provide an interface between
 * the Sandstorm runtime and the aDisk library. Applications should not
 * make use of this class.
 *
 * @author Matt Welsh
 */
public class AFileMgr {

  private static final boolean DEBUG = false;

  static final int THREADPOOL_IMPL = 0;
  private static int IMPL_TO_USE;

  private static ThreadManagerIF aFileTM;
  private static boolean initialized = false;
  private static Object init_lock = new Object();

  static {
    // Eventually test for JAIO 
    IMPL_TO_USE = THREADPOOL_IMPL;
  }

  /**
   * Called at startup time by the Sandstorm runtime.
   */
  public static void initialize(ManagerIF mgr, SystemManagerIF sysmgr) throws Exception {
    synchronized (init_lock) {
      switch (IMPL_TO_USE) {
	case THREADPOOL_IMPL:
	  // XXX Could replace with a TPSThreadManager - but need to augment 
	  // TPSTM to start with an initial number of threads per stage
	  //
	  // We also want the threads to poll across the event queues
	  // for each file (going to sleep if none of the event queues have
	  // events) which is closer to the TPPTM behavior. I think it's easier 
	  // to do this with a separate thread manager rather than bastardizing
	  // an existing one.
	  aFileTM = new AFileTPTM(mgr, sysmgr);
	  break;
	default:
	  throw new LinkageError("Error: AFileMgr has bad value for IMPL_TO_USE; this is a bug - please contact <mdw@cs.berkeley.edu>");
      }
      initialized = true;
    }
  }

  /**
   * Called when initialized in standalone mode.
   */
  static synchronized void initialize() {
    synchronized(init_lock) {
      if (initialized) return;
      Sandstorm ss = Sandstorm.getSandstorm();
      if (ss != null) {
	// There is a Sandstorm running, but we weren't initialized 
	// at startup time
	try {
	  initialize(ss.getManager(), ss.getSystemManager());
	} catch (Exception e) {
	  System.err.println("Warning: AFileMgr.initialize() got exception: "+e);
	}
      } else {
	// No Sandstorm running yet, so create one
	try {
	  SandstormConfig cfg = new SandstormConfig();
	  cfg.putBoolean("global.profile.enable", false);
	  ss = new Sandstorm(cfg);
	} catch (Exception e) {
	  System.err.println("AFileMgr: Warning: Initialization failed: "+e);
	  e.printStackTrace();
	  return;
	}
      }
    }
  }

  /**
   * Return the code for the implementation being used.
   */
  static int getImpl() {
    return IMPL_TO_USE;
  }

  /**
   * Return the ThreadManagerIF corresponding to the chosen implementation.
   */
  static ThreadManagerIF getTM() {
    return aFileTM;
  }

}

