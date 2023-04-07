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
import seda.util.MDWUtil;

import java.util.Arrays;

/**
 * An implementation of ResponseTimeController that uses a PID control.
 * 
 * @author   Matt Welsh
 */
public class ResponseTimeControllerPID extends ResponseTimeController {

  private static final boolean DEBUG = true;

  private static final boolean ADJUST_THRESHOLD = false;
  private static final boolean ADJUST_RATE = true;

  private static final boolean BE_CREATIVE = false;

  private static final int MEASUREMENT_SIZE = 100;
  private static final long MEASUREMENT_TIME = 5000;
  private static final double SMOOTH_CONST = 0.8;
  private static final double PROP_GAIN = 1.0;
  private static final double DERIV_GAIN = -0.5;
  private static final double INTR_GAIN = (0.2 / MEASUREMENT_SIZE);
  private static final int NINETIETH = (int)((double)MEASUREMENT_SIZE * 0.9);

  protected final static int INIT_THRESHOLD = 1;
  protected final static int MIN_THRESHOLD = 1;
  protected final static int MAX_THRESHOLD = 1024;

  private static final double INIT_RATE = 10.0;
  private static final int INIT_DEPTH = 10;
  private static final double MAX_RATE = 5000.0;
  private static final double MIN_RATE = 0.05;

  private SinkProxy sinkProxy;
  private long measurements[], sortedmeasurements[];
  private double errors[], lasterr, lastinterr, totalinterr;
  private int curThreshold, cur_measurement, cur_error;
  private long numReceived;
  private double curRate;
  private double ninetiethRT, lambda;
  private long adjtime;
  private boolean enabled;

  public ResponseTimeControllerPID(ManagerIF mgr, StageWrapperIF stage) throws IllegalArgumentException {
    super(mgr, stage);
    this.adjtime = System.currentTimeMillis();
    this.sinkProxy = (SinkProxy)stage.getStage().getSink();
    this.measurements = new long[MEASUREMENT_SIZE];
    this.sortedmeasurements = new long[MEASUREMENT_SIZE];
    this.errors = new double[MEASUREMENT_SIZE];
    this.cur_measurement = 0;
    this.cur_error = 0;

    // Add profile 
    mgr.getProfiler().add("RTControllerPID 90th-percentile RT <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)ninetiethRT;
	}
	});

    if (ADJUST_THRESHOLD) {

      mgr.getProfiler().add("RTControllerPID queue threshold <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return curThreshold;
  	  }
  	  });

      this.pred = new QueueThresholdPredicate(stage.getStage().getSink(), MAX_THRESHOLD);
      ((QueueThresholdPredicate)pred).setThreshold(INIT_THRESHOLD);
      this.curThreshold = ((QueueThresholdPredicate)pred).getThreshold();
      stage.getStage().getSink().setEnqueuePredicate(this.pred);

      System.err.println("RTControllerPID <"+stage.getStage().getName()+">: ADJUST_THRESH enabled, MEASUREMENT_SIZE="+MEASUREMENT_SIZE+", SMOOTH_CONST="+SMOOTH_CONST+", PROP_GAIN="+PROP_GAIN+", DERIV_GAIN="+DERIV_GAIN+", INTR_GAIN="+INTR_GAIN);

    } else if (ADJUST_RATE) {

      this.pred = new RateLimitingPredicate(stage.getStage().getSink(), INIT_RATE, INIT_DEPTH);
      this.curRate = ((RateLimitingPredicate)pred).getTargetRate();
      stage.getStage().getSink().setEnqueuePredicate(pred);

      System.err.println("RTControllerPID <"+stage.getStage().getName()+">: ADJUST_RATE enabled, MEASUREMENT_SIZE="+MEASUREMENT_SIZE+", SMOOTH_CONST="+SMOOTH_CONST+", PROP_GAIN="+PROP_GAIN+", DERIV_GAIN="+DERIV_GAIN+", INTR_GAIN="+INTR_GAIN);
    }

    this.enabled = true;
  }

  public synchronized void enable() {
    if (enabled) return;

    System.err.println("RTControllerPID <"+stage.getStage().getName()+">: Enabling");
    if (ADJUST_THRESHOLD) {
      this.pred = new QueueThresholdPredicate(stage.getStage().getSink(), curThreshold);

    } else if (ADJUST_RATE) {
      this.pred = new RateLimitingPredicate(stage.getStage().getSink(), curRate, INIT_DEPTH);
    }

    stage.getStage().getSink().setEnqueuePredicate(this.pred);
    enabled = true;
  } 

  public synchronized void disable() {
    if (!enabled) return;
    System.err.println("RTControllerPID <"+stage.getStage().getName()+">: Disabling");
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
    long elapsed = curtime - adjtime;
    if (elapsed >= MEASUREMENT_TIME) {
      adjust = true;
      numsort = cur_measurement;
      cur_measurement = 0;
    }

    if (!adjust) return;
    System.arraycopy(measurements, 0, sortedmeasurements, 0, numsort);
    Arrays.sort(sortedmeasurements, 0, numsort);
    long cur = sortedmeasurements[ (int)(0.9 * (double)numsort) ];
    ninetiethRT = (SMOOTH_CONST * (double)ninetiethRT*1.0) + ((1.0 - SMOOTH_CONST) * ((double)cur * 1.0));
    adjtime = curtime;
    stage.getStats().record90thRT(ninetiethRT);

    int numReceived = sinkProxy.enqueueSuccessCount;
    sinkProxy.enqueueSuccessCount = 0;
    double cur_lambda = (numReceived * 1.0) / (elapsed * 1.0e-3);
    lambda = (SMOOTH_CONST * lambda) + ((1.0 - SMOOTH_CONST) * cur_lambda);

    // Apply PID control
    double err = (targetRT - ninetiethRT) / targetRT;
    double derr = (err - lasterr) / (double)(elapsed * 1.0e-3);
    double interr = (((lasterr + err)/2) * (double)((elapsed) * 1.0e-3));
    lasterr = err; adjtime = curtime;

    totalinterr -= errors[cur_error];
    totalinterr += interr;
    errors[cur_error] = interr;
    cur_error++; if (cur_error == MEASUREMENT_SIZE) cur_error = 0;

//    interr -= errors[cur_error];
//    errors[cur_error] = interr;
//    cur_error++; if (cur_error == MEASUREMENT_SIZE) cur_error = 0;
//    lasterr = err; lastinterr = interr; adjtime = curtime;

    double out;
    if (BE_CREATIVE) {
      out = (PROP_GAIN * err * err);
      if (err < 0) { out *= -1; }
    } else {
      out = ((PROP_GAIN * err) + (DERIV_GAIN * derr) + (INTR_GAIN*totalinterr));
    }

    if (DEBUG) System.err.println("RTControllerPID <"+stage.getStage().getName()+">: lambda "+MDWUtil.format(lambda)+" 90th "+MDWUtil.format(ninetiethRT)+" err "+MDWUtil.format(err)+" derr "+MDWUtil.format(derr)+" interr "+MDWUtil.format(totalinterr)+" out "+MDWUtil.format(out));

    if (!enabled) return;

    if (ADJUST_THRESHOLD) {
      curThreshold += out;
      //curThreshold = (int)((MIN_THRESHOLD) + ((MAX_THRESHOLD - MIN_THRESHOLD) * out));
      if (curThreshold < MIN_THRESHOLD) curThreshold = MIN_THRESHOLD;
      if (curThreshold > MAX_THRESHOLD) curThreshold = MAX_THRESHOLD;

      if (DEBUG) System.err.println("RTControllerPID <"+stage.getStage().getName()+">: ninetiethRT "+ninetiethRT+" target "+targetRT+" threshold "+curThreshold);
      ((QueueThresholdPredicate)pred).setThreshold(curThreshold);

    } else if (ADJUST_RATE) {

      if (BE_CREATIVE) {
	if (out < 0) {
	  //curRate /= (out * -1);
	  curRate /= 2;
	} else {
	  curRate += out;
	}
      } else {
	curRate += out;
      }

      curRate = Math.max(MIN_RATE, curRate);
      curRate = Math.min(MAX_RATE, curRate);
      ((RateLimitingPredicate)pred).setTargetRate(this.curRate);

      if (DEBUG) System.err.println("RTControllerPID <"+stage.getStage().getName()+">: ninetiethRT "+ninetiethRT+" target "+targetRT+" rate now "+curRate);

    }

  }

}
