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
import seda.sandStorm.api.QueueElementIF;

import java.util.Random;

public class ProcessStageBatch extends ProcessStage {

  private static final boolean DEBUG = false;

  private static final int BUFFER_SIZE = 64*1024;
  private static final int NUM_INIT_MSGS = 10000;
  private static final double CRIT_PROB = 0.5;
  private static final boolean NEW_ARRAY_EACH_TIME = false;

  private int NUM_LOOPS;
  private int BATCH_SIZE;

  private Object lock = new Object();
  private boolean locked = false;
  private Random rand = new Random();
  private int arr[] = new int[BUFFER_SIZE];
  private int processCount = 0;
  private long totalCount = 0;

  public void init(ConfigDataIF config) throws Exception {
    super.init(config);

    NUM_LOOPS = config.getInt("num_loops");
    if (NUM_LOOPS == -1) { 
      throw new Exception("Must specify num_loops");
    }
    BATCH_SIZE = config.getInt("batch_size");
    if (BATCH_SIZE == -1) { 
      throw new Exception("Must specify batch_size");
    }
    System.err.println(config.getStage().getName()+": Started, num_loops="+NUM_LOOPS+", batch_size="+BATCH_SIZE);

    for (int i = 0; i < NUM_INIT_MSGS; i++) {
      Message m = new Message(0, Message.STATUS_OK, mysink, null);
      m.send();
    }
  }

  public void handleEvents(QueueElementIF items[]) {
    // Essentially: Only do work for first item in a batch, throw out
    // the rest
    for (int i = 0; i < items.length; i++) {
      mysink.enqueue_lossy(items[i]);
    }
    processCount = 0;
    for (int i = 0; i < items.length; i++) {
      handleEvent(items[i]);
    }
  }

  protected void processMessage(Message msg) {
    if (DEBUG) System.err.println("processMessage: Processing "+msg);

    long t1, t2;

    t1 = System.currentTimeMillis();
    int n = 0;

    // Do work
    if (NEW_ARRAY_EACH_TIME) arr = new int[BUFFER_SIZE];
    Random r = new Random();

    int ls = NUM_LOOPS;
    if ((processCount % BATCH_SIZE) != 0) ls /= 20;
    //if (processCount != 0) ls /= 100;
    processCount++;
    totalCount++;

    for (int x = 0; x < ls; x++) {
      arr[n] = r.nextInt();
      n++; if (n == BUFFER_SIZE) n = 0;
    }

    if (DEBUG) System.err.println("processMessage: Done processing");
    t2 = System.currentTimeMillis();
    if (DEBUG) System.err.println("processMessage: Took "+(t2-t1)+" ms");
    if ((totalCount % 100) == 0) {
      System.err.print("\rProcessStageBatch: "+totalCount+"  ");
    }

  }

}

