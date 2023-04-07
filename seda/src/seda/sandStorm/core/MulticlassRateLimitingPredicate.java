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

package seda.sandStorm.core;

import seda.sandStorm.api.ClassQueueElementIF;
import seda.sandStorm.api.EnqueuePredicateIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.util.StatsGatherer;

/**
 * This enqueue predicate implements multiclass input rate policing.
 */
public class MulticlassRateLimitingPredicate implements EnqueuePredicateIF {

  private static final boolean DEBUG = false;

  private SinkIF thesink;
  private int NUM_CLASSES;
  private double targetRate[];
  private int depth[];
  private double tokenCount[];
  private double regenTimeMS[];
  private long lasttime[];

  // Number of milliseconds between regenerations
  private long MIN_REGEN_TIME = 0;

  private static final boolean PROFILE = true;
  private StatsGatherer interArrivalStats;
  private StatsGatherer acceptArrivalStats;
  
  /**
   * Create a new RateLimitingPredicate for the given sink,
   * targetRate, and token bucket depth. A rate of -1.0 indicates no rate limit.
   */
  public MulticlassRateLimitingPredicate(SinkIF sink, int numclasses, double targetRate, int depth) {
    this.thesink = sink;
    this.NUM_CLASSES = numclasses;

    this.targetRate = new double[NUM_CLASSES];
    this.depth = new int[NUM_CLASSES];
    this.regenTimeMS = new double[NUM_CLASSES];
    this.tokenCount = new double[NUM_CLASSES];
    this.lasttime = new long[NUM_CLASSES];

    for (int c = 0; c < NUM_CLASSES; c++) {
      this.targetRate[c] = targetRate;
      this.regenTimeMS[c] = (1.0 / this.targetRate[c]) * 1.0e3;
      if (this.regenTimeMS[c] < 1) this.regenTimeMS[c] = 1;
      this.depth[c] = depth;
      this.tokenCount[c] = depth*1.0;
      this.lasttime[c] = System.currentTimeMillis();
    }

    System.err.println("MulticlassRateLimitingPredicate<"+sink.toString()+">: Created");

    if (PROFILE) {
      interArrivalStats = new StatsGatherer("IA<"+sink.toString()+">", 
	  "IA<"+sink.toString()+">", 1, 0);
      acceptArrivalStats = new StatsGatherer("AA<"+sink.toString()+">", 
	  "AA<"+sink.toString()+">", 1, 0);
    }
  }

  /**
   * Returns true if the given element can be accepted into the queue.
   */
  public boolean accept(QueueElementIF qel) {

    if (DEBUG) System.err.println("MCRLP <"+thesink.toString()+": Got "+qel);

    int c = 0;
    if (qel instanceof ClassQueueElementIF) {
      ClassQueueElementIF cqel = (ClassQueueElementIF)qel;
      c = cqel.getRequestClass();
      if (c == -1) c = 0;
    }
    if (DEBUG) System.err.println("MCRLP <"+thesink.toString()+": Class is "+c);

    if (targetRate[c] == -1.0) return true;

    // First regenerate tokens
    long curtime = System.currentTimeMillis();
    long delay = curtime - lasttime[c];

    if (PROFILE) {
      interArrivalStats.add(delay);
    }

    if (delay >= MIN_REGEN_TIME) {
      double numTokens = ((double)delay * 1.0) / (regenTimeMS[c] * 1.0);
      tokenCount[c] += numTokens; 
      if (tokenCount[c] > depth[c]) tokenCount[c] = depth[c];
      lasttime[c] = curtime;
    }

    if (tokenCount[c] >= 1.0) {
      tokenCount[c] -= 1.0;
      if (PROFILE) {
       	acceptArrivalStats.add(delay);
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Return the current rate limit.
   */
  public double getTargetRate(int theclass) {
    return targetRate[theclass];
  }

  /**
   * Return the current depth.
   */
  public int getDepth(int theclass) {
    return depth[theclass];
  }

  /**
   * Return the number of tokens currently in the bucket.
   */
  public int getBucketSize(int theclass) {
    return (int)tokenCount[theclass];
  }

  /**
   * Set the rate limit. A limit of -1.0 indicates no rate limit.
   */
  public void setTargetRate(int theclass, double targetRate) {
    // Kill off old tokens if reducing rate
    if (targetRate < this.targetRate[theclass]) {
      this.tokenCount[theclass] = 0;
    }

    this.targetRate[theclass] = targetRate;
    this.regenTimeMS[theclass] = (1.0 / targetRate) * 1.0e3;
    if (regenTimeMS[theclass] < 1) regenTimeMS[theclass] = 1;
  }

  /**
   * Set the bucket depth.
   */
  public void setDepth(int theclass, int depth) {
    this.depth[theclass] = depth;
  }

}
