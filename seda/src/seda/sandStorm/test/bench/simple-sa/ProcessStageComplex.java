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

import java.util.Random;

public class ProcessStageComplex extends ProcessStage {

  private static final boolean DEBUG = false;

  private static final int BUFFER_SIZE = 64*1024;
  private static final int NUM_INIT_MSGS = 1000;
  private static final double CRIT_PROB = 0.5;
  private static final boolean NEW_ARRAY_EACH_TIME = false;

  private int NUM_LOOPS;
  private Object lock = new Object();
  private boolean locked = false;
  private Random rand = new Random();
  private int arr[] = new int[BUFFER_SIZE];

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    NUM_LOOPS = config.getInt("num_loops");
    if (NUM_LOOPS == -1) { 
      throw new Exception("Must specify num_loops");
    }
    System.err.println(config.getStage().getName()+": Started, num_loops="+NUM_LOOPS);

    for (int i = 0; i < NUM_INIT_MSGS; i++) {
      Message m = new Message(0, Message.STATUS_OK, mysink, null);
      m.send();
    }

  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);

    mysink.enqueue_lossy(msg);

    long t1, t2;

    t1 = System.currentTimeMillis();
    int n = 0;

    boolean enterCrit = false;

    if (rand.nextDouble() <= CRIT_PROB) {
      enterCrit = true;
    }

    if (enterCrit) {
      // Enter critical section
      synchronized (lock) {
	while (locked == true) {
	  try {
	    lock.wait();
	  } catch (Exception e) {
	    // Ignore
	  }
	}
	locked = true;
      }
    }

    // Do work
    if (NEW_ARRAY_EACH_TIME) arr = new int[BUFFER_SIZE];
    Random r = new Random();
    for (int x = 0; x < NUM_LOOPS; x++) {
      arr[n] = r.nextInt();
      n++; if (n == BUFFER_SIZE) n = 0;
    }

    if (enterCrit) {
      synchronized (lock) {
  	locked = false;
	lock.notifyAll();
      }
    }

    if (DEBUG) System.err.println("processMessage: Done processing");
    t2 = System.currentTimeMillis();
    if (DEBUG) System.err.println("processMessage: Took "+(t2-t1)+" ms");

  }

}

