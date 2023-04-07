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


package seda.sandStorm.main;

import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.internal.SystemManagerIF;
import seda.sandStorm.internal.sandStormMgr;

/**
 * This is the top-level class which acts as the "wrapper" and 
 * external interface to the Sandstorm runtime. By creating a
 * Sandstorm object one can embed a Sandstorm system in another
 * application. If you wish to run a standalone Sandstorm, this
 * can be done from the commandline using the
 * <tt>seda.sandStorm.main.Main</tt> class.
 * 
 * <p>In general it is a good idea to have just one Sandstorm instance 
 * per JVM; multiple instances may interfere with one another in terms 
 * of resource allocation and thread scheduling.
 *
 * @author Matt Welsh
 * @see Main
 * @see SandstormConfig
 */
public class Sandstorm {

  private sandStormMgr mgr;   
  private static Sandstorm globalSandstorm = null;

  /**
   * Create a new Sandstorm with the default configuration and no
   * initial stages.
   */
  public Sandstorm() throws Exception {
    this(new SandstormConfig());
  }

  /**
   * Create a new Sandstorm, reading the configuration from the given
   * file.
   */
  public Sandstorm(String fname) throws Exception {
    this(new SandstormConfig(fname));
  }

  /**
   * Create a new Sandstorm with the given configuration.
   */
  public Sandstorm(SandstormConfig config) throws Exception {
    if (globalSandstorm != null) {
      throw new RuntimeException("Sandstorm: Error: Only one Sandstorm instance can be running at a given time.");
    }
    globalSandstorm = this;
    mgr = new sandStormMgr(config);
    mgr.start();
  }

  /**
   * Return a handler to the ManagerIF for the Sandstorm instance.
   * This interface allows one to create and obtain handles to stages.
   */
  public ManagerIF getManager() {
    return mgr;
  }

  /**
   * Return a handle to the SystemManagerIF for the Sandstorm instance.
   * This interface allows one to create stages and thread managers.
   */
  public SystemManagerIF getSystemManager() {
    return mgr;
  }

  /**
   * Returns the currently-running Sandstorm instance, if any.
   * Returns null if no Sandstorm is currently running.
   */
  public static Sandstorm getSandstorm() {
    return globalSandstorm;
  }

}

