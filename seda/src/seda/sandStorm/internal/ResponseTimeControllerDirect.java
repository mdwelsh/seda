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
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.core.QueueThresholdPredicate;
import seda.sandStorm.core.RateLimitingPredicate;
import seda.sandStorm.core.TimeStampedEvent;

import java.util.Arrays;

/**
 * An implementation of ResponseTimeController that uses a direct 
 * adjustment of queue thresholds based on the error in the 90th
 * percentile response time.
 * 
 * @author   Matt Welsh
 */
public class ResponseTimeControllerDirect extends ResponseTimeController {

  private static final boolean DEBUG = true;

  private static final boolean ADJUST_THRESHOLD = false;
  private static final boolean ADJUST_RATE = true;

  private static final int MEASUREMENT_SIZE = 100;
  private static final long MEASUREMENT_TIME = 1000;
  private static final double SMOOTH_CONST = 0.7;
  private static final int NINETIETH = (int)((double)MEASUREMENT_SIZE * 0.9);

  private static final double LOW_WATER = 0.9;
  private static final double HIGH_WATER = 1.2;
  private static final double ADDITIVE_INCREASE = 0.5;
  private static final double MULTIPLICATIVE_INCREASE = 1.1;
  private static final double MULTIPLICATIVE_DECREASE = 2;

  protected final static int INIT_THRESHOLD = 1;
  protected final static int MIN_THRESHOLD = 1;
  protected final static int MAX_THRESHOLD = 1024;

  private static final double INIT_RATE = 10.0;
  private static final int INIT_DEPTH = 10;
  private static final double MAX_RATE = 5000.0;
  private static final double MIN_RATE = 0.05;

  private long adjtime;
  private long measurements[], sortedmeasurements[];
  private int curThreshold, cur_measurement;
  private double curRate;
  private double ninetiethRT;
  private boolean enabled;

  public ResponseTimeControllerDirect(ManagerIF mgr, StageWrapperIF stage) throws IllegalArgumentException {
    super(mgr, stage);

    this.measurements = new long[MEASUREMENT_SIZE];
    this.sortedmeasurements = new long[MEASUREMENT_SIZE];
    this.cur_measurement = 0;
    this.adjtime = System.currentTimeMillis();

    // Add profile 
    mgr.getProfiler().add("RTController 90th-percentile RT <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)ninetiethRT;
	}
	});

    if (ADJUST_THRESHOLD) {
      mgr.getProfiler().add("RTController queueThreshold <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return curThreshold;
  	  }
  	  });

      this.pred = new QueueThresholdPredicate(stage.getStage().getSink(), MAX_THRESHOLD);
      ((QueueThresholdPredicate)pred).setThreshold(INIT_THRESHOLD);
      this.curThreshold = ((QueueThresholdPredicate)pred).getThreshold();
      stage.getStage().getSink().setEnqueuePredicate(this.pred);

      System.err.println("RTControllerDirect <"+stage.getStage().getName()+">: ADJUST_THRESH enabled, target="+targetRT+", MEASUREMENT_SIZE="+MEASUREMENT_SIZE+", SMOOTH_CONST="+SMOOTH_CONST+", LOW_WATER="+LOW_WATER+", HIGH_WATER="+HIGH_WATER+", ADDITIVE_INCREASE="+ADDITIVE_INCREASE+", MULTIPLCATIVE_DECREASE="+MULTIPLICATIVE_DECREASE);

    } else if (ADJUST_RATE) {
      mgr.getProfiler().add("RTController curRate <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return (int)curRate;
  	  }
  	  });

      this.pred = new RateLimitingPredicate(stage.getStage().getSink(), INIT_RATE, INIT_DEPTH);
      this.curRate = ((RateLimitingPredicate)pred).getTargetRate();
      stage.getStage().getSink().setEnqueuePredicate(pred);

      System.err.println("RTControllerDirect <"+stage.getStage().getName()+">: ADJUST_RATE enabled, target="+targetRT+", MEASUREMENT_SIZE="+MEASUREMENT_SIZE+", SMOOTH_CONST="+SMOOTH_CONST+", LOW_WATER="+LOW_WATER+", HIGH_WATER="+HIGH_WATER+", ADDITIVE_INCREASE="+ADDITIVE_INCREASE+", MULTIPLCATIVE_DECREASE="+MULTIPLICATIVE_DECREASE);
    }

    this.enabled = true;
  }

  public synchronized void enable() {
    if (enabled) return;

    System.err.println("RTControllerDirect <"+stage.getStage().getName()+">: Enabling");
    if (ADJUST_THRESHOLD) {
      this.pred = new QueueThresholdPredicate(stage.getStage().getSink(), curThreshold);
    } else if (ADJUST_RATE) {
      this.pred = new RateLimitingPredicate(stage.getStage().getSink(), curRate, INIT_DEPTH);
    }

    stage.getStage().getSink().setEnqueuePredicate(pred);
    enabled = true;
  }

  public synchronized void disable() {
    if (!enabled) return;
    System.err.println("RTControllerDirect <"+stage.getStage().getName()+">: Disabling");
    this.pred = null;
    stage.getStage().getSink().setEnqueuePredicate(null);
    enabled = false;
  }

  public synchronized void adjustThreshold(QueueElementIF fetched[], long procTime) {
    long curtime = System.currentTimeMillis();
    boolean adjust = false;

    for (int i = 0; i < fetched.length; i++) {
      if (fetched[i] instanceof TimeStampedEvent) {
	TimeStampedEvent ev = (TimeStampedEvent)fetched[i];
	long time = ev.timestamp;
	if (time != 0) {
	  measurements[cur_measurement] = curtime - time;
	  cur_measurement++; 
	  if (cur_measurement == MEASUREMENT_SIZE) {
	    cur_measurement = 0;
	    adjust = true;
	  }
	}
      }
    }

    int numsort = MEASUREMENT_SIZE;
    if ((curtime - adjtime) >= MEASUREMENT_TIME) {
      adjust = true;
      numsort = cur_measurement;
      cur_measurement = 0;
    } 

    if (!adjust) return;
    System.arraycopy(measurements, 0, sortedmeasurements, 0, numsort);
    Arrays.sort(sortedmeasurements, 0, numsort);
    long cur = sortedmeasurements[ (int)(0.9 * (double)numsort) ];
    ninetiethRT = (SMOOTH_CONST * (double)ninetiethRT*1.0) + ((1.0 - SMOOTH_CONST) * ((double)cur * 1.0)); 
    stage.getStats().record90thRT(ninetiethRT);

    adjtime = curtime;

    if (!enabled) return;

    if (ADJUST_THRESHOLD) {

      if (ninetiethRT < (LOW_WATER * targetRT)) {
	curThreshold += ADDITIVE_INCREASE;
	//curThreshold *= MULTIPLICATIVE_INCREASE;
	if (curThreshold > MAX_THRESHOLD) curThreshold = MAX_THRESHOLD;
      } else if (ninetiethRT > (HIGH_WATER * targetRT)) {
	curThreshold /= MULTIPLICATIVE_DECREASE;
	if (curThreshold < MIN_THRESHOLD) curThreshold = MIN_THRESHOLD;
      }
      if (DEBUG) System.err.println("RTController <"+stage.getStage().getName()+">: ninetiethRT "+ninetiethRT+" target "+targetRT+" threshold "+curThreshold);
      ((QueueThresholdPredicate)pred).setThreshold(curThreshold);

    } else if (ADJUST_RATE) {

      if (ninetiethRT < (LOW_WATER * targetRT)) {
	curRate += ADDITIVE_INCREASE;
	//curRate *= MULTIPLICATIVE_INCREASE;
	if (curRate > MAX_RATE) curRate = MAX_RATE;
      } else if (ninetiethRT > (HIGH_WATER * targetRT)) {
	curRate /= MULTIPLICATIVE_DECREASE;
	if (curRate < MIN_RATE) curRate = MIN_RATE;
      }
      if (DEBUG) System.err.println("RTController <"+stage.getStage().getName()+">: ninetiethRT "+ninetiethRT+" target "+targetRT+" rate now "+curRate);
      ((RateLimitingPredicate)pred).setTargetRate(this.curRate);

    }


  }

}
