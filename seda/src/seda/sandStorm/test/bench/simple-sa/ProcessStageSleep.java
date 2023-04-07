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

import seda.sandStorm.api.ConfigDataIF;

public class ProcessStageSleep extends ProcessStage {

  private static final boolean DEBUG = false;

  private int SLEEP_TIME;

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    SLEEP_TIME = config.getInt("sleep_time");
    if (SLEEP_TIME == -1) { 
      throw new Exception("Must specify sleep_time");
    }
    System.err.println(config.getStage().getName()+": Started, sleep_time="+SLEEP_TIME);
  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);

    if (SLEEP_TIME == 0) return;

    try {
      Thread.currentThread().sleep(SLEEP_TIME);
    } catch (Exception e) {
      // Ignore
    }
  }
}

