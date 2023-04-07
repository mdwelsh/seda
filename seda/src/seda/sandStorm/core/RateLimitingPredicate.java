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

import seda.sandStorm.api.EnqueuePredicateIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.util.StatsGatherer;

/**
 * This enqueue predicate implements input rate policing.
 */
public class RateLimitingPredicate implements EnqueuePredicateIF {

  private static final boolean DEBUG = false;

  private SinkIF thesink;
  private double targetRate;
  private int depth;
  private double tokenCount;
  private double regenTimeMS;
  private long lasttime;

  // Number of milliseconds between regenerations
  private long MIN_REGEN_TIME = 0;

  private static final boolean PROFILE = true;
  private StatsGatherer interArrivalStats;
  private StatsGatherer acceptArrivalStats;
  
  /**
   * Create a new RateLimitingPredicate for the given sink,
   * targetRate, and token bucket depth. A rate of -1.0 indicates no rate limit.
   */
  public RateLimitingPredicate(SinkIF sink, double targetRate, int depth) {
    this.thesink = sink;
    this.targetRate = targetRate;
    this.regenTimeMS = (1.0 / targetRate) * 1.0e3;
    if (this.regenTimeMS < 1) this.regenTimeMS = 1;
    this.depth = depth; this.tokenCount = depth*1.0;
    this.lasttime = System.currentTimeMillis();

    System.err.println("RateLimitingPredicate<"+sink.toString()+">: Created");

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
    if (targetRate == -1.0) return true;

    // First regenerate tokens
    long curtime = System.currentTimeMillis();
    long delay = curtime - lasttime;

    if (PROFILE) {
      interArrivalStats.add(delay);
    }

    if (delay >= MIN_REGEN_TIME) {
      double numTokens = ((double)delay * 1.0) / (regenTimeMS * 1.0);
      tokenCount += numTokens; if (tokenCount > depth) tokenCount = depth;
      lasttime = curtime;
    }

    if (tokenCount >= 1.0) {
      tokenCount -= 1.0;
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
  public double getTargetRate() {
    return targetRate;
  }

  /**
   * Return the current depth.
   */
  public int getDepth() {
    return depth;
  }

  /**
   * Return the number of tokens currently in the bucket.
   */
  public int getBucketSize() {
    return (int)tokenCount;
  }

  /**
   * Set the rate limit. A limit of -1.0 indicates no rate limit.
   */
  public void setTargetRate(double targetRate) {
    this.targetRate = targetRate;
    this.regenTimeMS = (1.0 / targetRate) * 1.0e3;
    if (regenTimeMS < 1) regenTimeMS = 1;
  }

  /**
   * Set the bucket depth.
   */
  public void setDepth(int depth) {
    this.depth = depth;
  }

}
