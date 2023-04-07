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

import seda.sandStorm.api.*;

public class GenericHandler implements EventHandlerIF {

  private static final boolean DEBUG = false;

  private SinkIF nextHandlerSink;

  private int CPU1_TIME;
  private int CPU2_TIME;
  private int SLEEP_TIME;
  private String NEXT_HANDLER;

  public GenericHandler(String nextHandler, int cpu1_time, int cpu2_time, int sleep_time) {
    this.NEXT_HANDLER = nextHandler;
    this.CPU1_TIME = cpu1_time;
    this.CPU2_TIME = cpu2_time;
    this.SLEEP_TIME = sleep_time;
  }

  public void init(ConfigDataIF config) throws Exception {
    System.err.println(config.getStage().getName()+": Started, next="+NEXT_HANDLER+", cpu1="+CPU1_TIME+", cpu2="+CPU2_TIME+", sleep="+SLEEP_TIME);
    nextHandlerSink = config.getManager().getStage(NEXT_HANDLER).getSink();
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    long t1, t2;
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (CPU1_TIME != 0) {
      t1 = System.currentTimeMillis();
      do {
	t2 = System.currentTimeMillis();
      } while ((t2-t1) < CPU1_TIME);
    }

    if (SLEEP_TIME != 0) {
      try {
	Thread.sleep(SLEEP_TIME);
      } catch (InterruptedException ie) {
  	System.err.println("Warning: Interrupted sleep!");
      }
    }

    if (CPU2_TIME != 0) {
      t1 = System.currentTimeMillis();
      do {
	t2 = System.currentTimeMillis();
      } while ((t2-t1) < CPU2_TIME);
    }

    try {
      nextHandlerSink.enqueue(item);
    } catch (SinkException sce) {
      System.err.println("Got SE: "+sce);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

