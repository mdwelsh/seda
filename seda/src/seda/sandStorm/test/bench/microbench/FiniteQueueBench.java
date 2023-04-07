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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.QueueThresholdPredicate;
import seda.util.MDWUtil;

/**
 * A simple microbenchmark measuring overheads for various queue
 * operations.
 */
public class FiniteQueueBench {

  private static final int WARMUP_SIZE = 100;
  private static final int MEASUREMENT_SIZE = 1000000;

  private static void printStats(String msg, long t1, long t2, int count) {
    double time_ms = ((t2-t1)*1.0) / (count * 1.0);
    System.err.println(msg+": "+MDWUtil.format(time_ms)+" msec average");
  }

  public static void main(String args[]) {
    try {

      FiniteQueue queue;
      QueueElementIF event, event2;
      long t1, t2;

      event = new QueueElementIF(){ };

      queue = new FiniteQueue();
      // Warm up JIT
      for (int i = 0; i < WARMUP_SIZE; i++) {
	queue.enqueue(event);
	event2 = queue.dequeue();
      }

      queue = new FiniteQueue();

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	queue.enqueue(event);
      }
      t2 = System.currentTimeMillis();
      printStats("enqueue", t1, t2, MEASUREMENT_SIZE);

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	event2 = queue.dequeue();
      }
      t2 = System.currentTimeMillis();
      printStats("dequeue", t1, t2, MEASUREMENT_SIZE);

      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	queue.enqueue(event);
      }

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	event2 = queue.blocking_dequeue(-1);
      }
      t2 = System.currentTimeMillis();
      printStats("blocking_dequeue", t1, t2, MEASUREMENT_SIZE);

      // Set predicate
      queue.setEnqueuePredicate(new QueueThresholdPredicate(queue,MEASUREMENT_SIZE*2));
      for (int i = 0; i < WARMUP_SIZE; i++) {
	queue.enqueue(event);
	event2 = queue.dequeue();
      }

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	queue.enqueue(event);
      }
      t2 = System.currentTimeMillis();
      printStats("enqueue threshold predicate", t1, t2, MEASUREMENT_SIZE);

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	event2 = queue.dequeue();
      }
      t2 = System.currentTimeMillis();
      printStats("dequeue threshold predicate", t1, t2, MEASUREMENT_SIZE);

      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	queue.enqueue(event);
      }

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	event2 = queue.blocking_dequeue(-1);
      }
      t2 = System.currentTimeMillis();
      printStats("blocking_dequeue threshold predicate", t1, t2, MEASUREMENT_SIZE);

      // Disable predicate
      queue.setEnqueuePredicate(null);
      for (int i = 0; i < WARMUP_SIZE; i++) {
	queue.enqueue(event);
	event2 = queue.dequeue();
      }

      t1 = System.currentTimeMillis();
      for (int i = 0; i < MEASUREMENT_SIZE; i++) {
	queue.enqueue(event);
      }
      t2 = System.currentTimeMillis();
      printStats("enqueue no predicate", t1, t2, MEASUREMENT_SIZE);

    } catch (Exception e) {
      System.err.println("main() got exception: "+e);
      e.printStackTrace();
    }
  }

}
