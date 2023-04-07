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
import seda.sandStorm.core.BufferElement;

/**
 * This is a 'generic' event handler used for benchmarking. It
 * accepts BufferElements and then spins on the CPU for 'cpu1' msecs,
 * sleeps for 'sleep' msecs, and spins on the CPU for 'cpu2' msecs.
 * It then delivers the original event to the worker specified by
 * 'next_handler'.
 */
public class GenericWorker implements EventHandlerIF {

  private static final boolean DEBUG = false;

  private SinkIF nextHandlerSink;

  private int CPU1_TIME;
  private int CPU2_TIME;
  private int SLEEP_TIME;

  public GenericWorker() {
  }

  public void init(ConfigDataIF config) throws Exception {

    if (config.getString("cpu1") == null) 
      throw new IllegalArgumentException("Must specify cpu1!");
    if (config.getString("cpu2") == null) 
      throw new IllegalArgumentException("Must specify cpu2!");
    if (config.getString("sleep") == null) 
      throw new IllegalArgumentException("Must specify sleep!");
    if (config.getString("next_handler") == null) 
      throw new IllegalArgumentException("Must specify next_handler!");

    CPU1_TIME = Integer.parseInt(config.getString("cpu1"));
    CPU2_TIME = Integer.parseInt(config.getString("cpu2"));
    SLEEP_TIME = Integer.parseInt(config.getString("sleep"));

    nextHandlerSink = config.getManager().getStage(config.getString("next_handler")).getSink();

    System.err.println("sleep="+SLEEP_TIME);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    long t1, t2;
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof BufferElement) {

      BufferElement task = (BufferElement)item;

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
        nextHandlerSink.enqueue(task);
      } catch (SinkFullException sfe) {
        System.err.println("Got SFE: "+sfe);
      } catch (SinkException sce) {
        System.err.println("Got SE: "+sce);
      }

    } else {
      System.err.println("Got unexpected event: "+item);
    }

  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

