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

import seda.sandStorm.api.ClassQueueElementIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.core.MulticlassRateLimitingPredicate;
import seda.sandStorm.core.TimeStampedEvent;
import seda.util.MDWUtil;

import java.util.Arrays;

/**
 * An implementation of ResponseTimeController that uses a direct 
 * adjustment of queue thresholds based on the error in the 90th
 * percentile response time. Allows multiple class SLAs.
 * 
 * @author   Matt Welsh
 */
public class ResponseTimeControllerMulticlass extends ResponseTimeControllerDirect {

  private static final boolean DEBUG = true;

  private static final int MEASUREMENT_SIZE = 100;
  private static final long MEASUREMENT_TIME = 1000;
  private static final double SMOOTH_CONST = 0.7;
  private static final int NINETIETH = (int)((double)MEASUREMENT_SIZE * 0.9);

  private static final double LOW_WATER = -0.1;
  private static final double HIGH_WATER = 0.0;
  private static final double ADDITIVE_INCREASE = 2.0;
  private static final double MULTIPLICATIVE_INCREASE = 1.1;
  private static final double MULTIPLICATIVE_DECREASE = 1.2; //2
  private static final double MULTIPLICATIVE_DECREASE_LOWPRIO = 10;
  private static final double MULTIPLICATIVE_DECREASE_HIPRIO = 1.2;

  private static final double INIT_RATE = 100.0;
  private static final int INIT_DEPTH = 1;
  private static final double MAX_RATE = 5000.0;
  private static final double MIN_RATE = 0.05;
  private static final double MIN_90th = 1.0e-5;
  private static final int LOWCOUNT_THRESH = 20;

  private static final boolean SAVE_MAX_RATE = false;

  private static final int MAX_CLASSES = 10;
  private int NUM_CLASSES;

  private String name;
  private boolean enabled;
  private cinfo carr[];

  class cinfo {
    int theclass;
    double adjtime;
    double targetRT;
    long measurements[], sortedmeasurements[];
    int cur_measurement = 0;
    int num_measurements = 0;
    double curRate;
    int lowCount = 0;
    double ninetiethRT;
    double err, last_err;
    boolean adjust = false;
    boolean preempted = false;
    double maxRate = -1.0;
    boolean last_increased = false;

    cinfo(int theclass, double target) {
      this.theclass = theclass;
      this.targetRT = target;
      this.measurements = new long[MEASUREMENT_SIZE];
      this.sortedmeasurements = new long[MEASUREMENT_SIZE];

      this.curRate = ((MulticlassRateLimitingPredicate)pred).getTargetRate(theclass);
      this.adjtime = System.currentTimeMillis();
      System.err.println("RTControllerMulticlass: Class "+theclass+" targetRT "+targetRT+", curRate "+this.curRate);
    }

    void setRate(double newrate) {
      curRate = newrate;
      if (curRate < MIN_RATE) curRate = MIN_RATE;
      if (curRate > MAX_RATE) curRate = MAX_RATE;
      if (SAVE_MAX_RATE && maxRate > 0.0 && curRate > maxRate) curRate = maxRate;
      ((MulticlassRateLimitingPredicate)pred).setTargetRate(theclass, curRate);
    }

    void addMeasurement(long time) {
      measurements[cur_measurement] = time;
      cur_measurement++; 
      num_measurements++; 
      if (cur_measurement == MEASUREMENT_SIZE) {
	cur_measurement = 0;
	adjust = true;
      }
    }

    void record90th(int numsort, long curtime) {
      System.arraycopy(measurements, 0, sortedmeasurements, 0, numsort);
      Arrays.sort(sortedmeasurements, 0, numsort);
      long cur = sortedmeasurements[ (int)(0.9 * (double)numsort) ];
      ninetiethRT = (SMOOTH_CONST * (double)ninetiethRT*1.0) + ((1.0 - SMOOTH_CONST) * ((double)cur * 1.0)); 
      if (ninetiethRT < MIN_90th) ninetiethRT = 0;

      if (theclass == 0) stage.getStats().record90thRT(ninetiethRT);
      adjtime = curtime;

      // Avoid timeout causing us to always read old value
      if (cur_measurement == 0) sortedmeasurements[0] = 0L;
    }

    boolean adjust(long curtime) {

      int numsort = MEASUREMENT_SIZE;
      if (num_measurements > 0 && (curtime - adjtime) >= MEASUREMENT_TIME) {
	adjust = true;
	numsort = cur_measurement;
	cur_measurement = 0;
      } 

      if (!adjust) return false;
      adjust = false;

      record90th(numsort, curtime);

      if (!enabled) return false;
      if (targetRT == -1) return false;

      num_measurements = 0;
      err = (ninetiethRT - targetRT) / targetRT;

      if (err < LOW_WATER) {
	// We are below our target - increase our rate only

	if (preempted) {
	  // Not allowed to increase - preempted by higher priority
	  preempted = false;
	  return false;
	}

	setRate(curRate + rateAdd(err));
	last_increased = true;
	lowCount = 0;

      } else if (err > HIGH_WATER) {
	// We are above our target - reduce rates of all lower classes
	boolean found = false;
	for (int c2 = 0; c2 < theclass; c2++) {
	  cinfo ci2 = carr[c2];

	  ci2.preempted = true;
	  if (ci2.curRate > MIN_RATE) {
	    found = true;
	    ci2.setRate(ci2.curRate / MULTIPLICATIVE_DECREASE_LOWPRIO);
	  }
	}

	if (found) lowCount = 0;

	if (!found && ((++lowCount >= LOWCOUNT_THRESH) || (theclass == 0))) {
	  // Didn't find anyone else to penalize; adjust ourselves
	  setRate(curRate / MULTIPLICATIVE_DECREASE);
	  if (last_increased) maxRate = curRate;
	  last_increased = false;

	} else {
	  // Found someone else to penalize or not at LOWCOUNT_THRESH
	  setRate(curRate / MULTIPLICATIVE_DECREASE_HIPRIO);
	  last_increased = false;
	}

      } else {
	last_increased = false;
      }

      return true;
    }

  }

  public ResponseTimeControllerMulticlass(ManagerIF mgr, StageWrapperIF stage) throws IllegalArgumentException {
    super(mgr, stage);

    this.name = stage.getStage().getName();

    SandstormConfigIF config = mgr.getConfig();
    // First count number of classes
    for (int c = 0; c < MAX_CLASSES; c++) {
      double t = config.getDouble("stages."+name+".rtController.multiclass.class"+(c)+"Target");
      if (t == -1) {
	t = config.getDouble("global.rtController.multiclass.class"+c+"Target");
      }
      if (t != -1) NUM_CLASSES++;
    }

    if (NUM_CLASSES == 0) { 
      NUM_CLASSES = 1;
    }

    this.pred = new MulticlassRateLimitingPredicate(stage.getStage().getSink(), NUM_CLASSES, INIT_RATE, INIT_DEPTH);
    stage.getStage().getSink().setEnqueuePredicate(pred);

    this.carr = new cinfo[NUM_CLASSES];
    for (int c = 0; c < NUM_CLASSES; c++) {
      double t = config.getDouble("stages."+name+".rtController.multiclass.class"+(c)+"Target");
      if (t == -1) {
	t = config.getDouble("global.rtController.multiclass.class"+c+"Target");
      }
      this.carr[c] = new cinfo(c, t);
    }

    System.err.println("RTControllerMulticlass9 <"+name+">: MEASUREMENT_SIZE="+MEASUREMENT_SIZE+", SMOOTH_CONST="+SMOOTH_CONST+", LOW_WATER="+LOW_WATER+", HIGH_WATER="+HIGH_WATER+", ADDITIVE_INCREASE="+ADDITIVE_INCREASE+", MULTIPLCATIVE_DECREASE="+MULTIPLICATIVE_DECREASE);

    this.enabled = true;
  }

  // Additive increase function
  private double rateAdd(double err) {
    // LOW_WATER gets increase of 0
    if (err > -0.5) return 0;
    else return ADDITIVE_INCREASE * ((-1.0 * err) + LOW_WATER);
  }

  public synchronized void adjustThreshold(QueueElementIF fetched[], long procTime) {
    long curtime = System.currentTimeMillis();

    for (int i = 0; i < fetched.length; i++) {
      if (fetched[i] instanceof TimeStampedEvent) {
	TimeStampedEvent ev = (TimeStampedEvent)fetched[i];
	long time = ev.timestamp;
	if (time != 0) {
	  int theclass = 0;
	  if (ev instanceof ClassQueueElementIF) {
	    ClassQueueElementIF cqel = (ClassQueueElementIF)ev;
	    theclass = cqel.getRequestClass();
	    if (theclass == -1) theclass = 0;
	  }
	  carr[theclass].addMeasurement(curtime - time);
	}
      }
    }

    boolean adjusted_any = false;

    for (int c = NUM_CLASSES-1; c >= 0; c--) {
      if (carr[c].adjust(curtime)) {
	adjusted_any = true;
      }
    }

    if (adjusted_any) {
      for (int c = 0; c < NUM_CLASSES; c++) {
	if (DEBUG) System.err.println("RTController <"+name+"> class "+c+": ninetiethRT "+MDWUtil.format(carr[c].ninetiethRT)+" target "+MDWUtil.format(carr[c].targetRT)+" rate now "+MDWUtil.format(carr[c].curRate));
      }
    }

  }

}
