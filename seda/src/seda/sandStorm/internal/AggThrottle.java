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

package seda.sandStorm.internal;

import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.ProfilableIF;
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.util.MDWUtil;

import java.util.Random;

/** 
 * AggThrottle is used by thread managers to adjust their aggregation
 * level based on observations of stage throughput.
 *
 * @author Matt Welsh
 */
class AggThrottle {

  private static final boolean DEBUG = false;

  private StageWrapperIF stage;
  private String name;
  private ManagerIF mgr;

  private double bestThroughput, lastThroughput;
  private int bestTarget;
  private long lastEvents;
  private long lastMeasurementTime;
  private int measurementCount, adjustCount;

  private static final int STATE_DECREASING = 0;
  private static final int STATE_INCREASING = 1;
  private int state = STATE_DECREASING;
  private int increase_count = 0;

  private static final int ADJUST_DELAY = 5;

  private int minAggregation = 2;
  private int maxAggregation = 1000;
  private int recalcWindow = 1000;
  private double smoothConst = 0.7;

  private static final double REDUCE_FACTOR = 1.2;
  private static final double INCREASE_FACTOR = 1.2;
  private static final double LOW_WATER = 0.90;
  private static final double HIGH_WATER = 0.98;
  private static final double VERY_LOW_WATER = 0.2;
  private static final double VERY_HIGH_WATER = 2.0;

  private int aggregationTarget;
  private Random rand = new Random();

  AggThrottle(StageWrapperIF stage, ManagerIF mgr) {
    this.stage = stage;
    this.name = stage.getStage().getName();
    this.mgr = mgr;
    SandstormConfigIF config = mgr.getConfig();

    this.minAggregation = config.getInt("global.batchController.minBatch", 
	minAggregation);
    this.maxAggregation = config.getInt("global.batchController.maxBatch", 
	maxAggregation);
    this.recalcWindow = config.getInt("global.batchController.recalcWindow",
	recalcWindow);
    this.smoothConst = config.getDouble("global.batchController.smoothConst",
	smoothConst);

    System.err.println("AggThrottle <"+name+"> created: minBatch "+minAggregation+", maxBatch "+maxAggregation+", recalcWindow "+recalcWindow);
    this.aggregationTarget = this.maxAggregation;

    lastThroughput = 0.0;
    bestThroughput = 0.0;
    bestTarget = aggregationTarget;
    lastEvents = 0;
    lastMeasurementTime = System.currentTimeMillis();
    measurementCount = adjustCount = 0;

    mgr.getProfiler().add("AggThrottle throughput for <"+name+">",
	new ProfilableIF() {
	public int profileSize() {
	//int foo = getAggTarget(); // Recalculate
	return (int)lastThroughput;
	}
	});
    mgr.getProfiler().add("AggThrottle bestThroughput for <"+name+">",
	new ProfilableIF() {
	public int profileSize() {
	//int foo = getAggTarget(); // Recalculate
	return (int)bestThroughput;
	}
	});
    mgr.getProfiler().add("AggThrottle aggTarget for <"+name+">",
	new ProfilableIF() {
	public int profileSize() {
	//int foo = getAggTarget(); // Recalculate
	return aggregationTarget;
	}
	});
  }

  public String toString() {
    return "AggThrottle <"+name+">";
  }

  synchronized int getAggTarget() {

    long cur_time = System.currentTimeMillis();
    long time_elapsed = cur_time - lastMeasurementTime;

    if (time_elapsed < recalcWindow) {
      return aggregationTarget;
    }

    long events = stage.getStats().getTotalEvents();
    long curEvents = events - lastEvents;
    lastEvents = events;

    lastMeasurementTime = cur_time;

    double throughput = (curEvents * 1.0) / ((double)time_elapsed * 1.0e-3);
    double avgThroughput = (smoothConst * lastThroughput) + ((1.0 - smoothConst) * throughput);

    adjustCount++;
    if ((adjustCount % ADJUST_DELAY) == 0) {

      if (avgThroughput < (VERY_LOW_WATER*bestThroughput)) {
   	aggregationTarget = maxAggregation;
    	state = STATE_DECREASING;
      }

      if (avgThroughput >= (VERY_HIGH_WATER*bestThroughput)) {
	aggregationTarget = maxAggregation;
	state = STATE_DECREASING;
      }

      if (state == STATE_DECREASING) {
	if (avgThroughput <= (LOW_WATER*bestThroughput)) {
	  // Fell below low water - increase
	  //bestThroughput = avgThroughput;
	  state = STATE_INCREASING;
	  aggregationTarget *= INCREASE_FACTOR;
	  if (aggregationTarget > maxAggregation) aggregationTarget = maxAggregation;
	} else if (avgThroughput > bestThroughput) {
	  // Better throughput - save and decrease
	  bestThroughput = avgThroughput;
	  aggregationTarget /= REDUCE_FACTOR;
	  if (aggregationTarget < minAggregation) aggregationTarget = minAggregation;
	} else {
	  // Just decrease
	  aggregationTarget /= REDUCE_FACTOR;
	  if (aggregationTarget < minAggregation) aggregationTarget = minAggregation;
	}

      } else if (state == STATE_INCREASING) {
	if (avgThroughput > bestThroughput) {
	  // Better throughput - save 
	  bestThroughput = avgThroughput;
	}
	if (avgThroughput >= (HIGH_WATER*bestThroughput)) {
	  // Start decreasing
	  state = STATE_DECREASING;
	  aggregationTarget /= REDUCE_FACTOR;
	  if (aggregationTarget < minAggregation) aggregationTarget = minAggregation;
//	} else if (avgThroughput <= (LOW_WATER*bestThroughput)) {
	  // Fell below low water - decrease
	  //bestThroughput = avgThroughput;
//	  state = STATE_DECREASING;
//	  aggregationTarget /= REDUCE_FACTOR;
//	  if (aggregationTarget < minAggregation) aggregationTarget = minAggregation;
	} else {
	  // Just increase
	  aggregationTarget *= INCREASE_FACTOR;
	  if (aggregationTarget > maxAggregation) {
	    // Maxed out, so save best throughput and start decreasing
	    aggregationTarget = maxAggregation;
	    state = STATE_DECREASING;
	    bestThroughput = avgThroughput;
	  }
	}
      }

      // Randomly reset best estimate if not below LOW_WATER
//      if (rand.nextDouble() <= 0.2) {
//	if (avgThroughput >= (LOW_WATER*bestThroughput)) {
//	  bestThroughput = avgThroughput;
//	}
 //     }

      // Randomly switch direction
      if (rand.nextDouble() <= 0.0) {
	if (state == STATE_INCREASING) {
	  state = STATE_DECREASING;
	  aggregationTarget /= REDUCE_FACTOR;
	  if (aggregationTarget < minAggregation) aggregationTarget = minAggregation;
	} else {
	  state = STATE_INCREASING;
	  aggregationTarget *= INCREASE_FACTOR;
	  if (aggregationTarget > maxAggregation) aggregationTarget = maxAggregation;
	}
      }

      // Randomly reset
      if (rand.nextDouble() <= 0.00) {
	state = STATE_DECREASING;
   	aggregationTarget = maxAggregation;
	bestThroughput = 0.0;
      }
    }

    if (DEBUG) System.err.println("AggThrottle <"+name+">: avgThroughput "+MDWUtil.format(avgThroughput)+", last "+MDWUtil.format(lastThroughput)+", state "+((state==0)?"dec":"inc")+", aggTarget "+aggregationTarget);

    //if ((adjustCount % ADJUST_DELAY) == 0) lastThroughput = avgThroughput;
    lastThroughput = avgThroughput;
    return aggregationTarget;
  }

}

