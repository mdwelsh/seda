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
 * An implementation of ResponseTimeController that models the stage
 * as an M/M/1 queue.
 * 
 * @author   Matt Welsh
 */
public class ResponseTimeControllerMM1 extends ResponseTimeController {

  private static final boolean DEBUG = true;

  private static final boolean ADJUST_THRESHOLD = false;
  private static final boolean ADJUST_RATE = true;

  protected final static int INIT_THRESHOLD = 1;
  protected final static int MIN_THRESHOLD = 1;
  protected final static int MAX_THRESHOLD = 1024;

  private static final double INIT_RATE = -1.0;
  private static final int INIT_DEPTH = 100;
  private static final double MIN_RATE = 0.5;

  private static final boolean DEBUG_CAP_RATE = false;
  private static final double DEBUG_RATE = 100000.0;

  private static final boolean MOVING_AVERAGE = true;
  private static final int MEASUREMENT_SIZE = 200;



  // Arashi runs
  private static final int ESTIMATION_SIZE = 5000;
  private static final long ESTIMATION_TIME = 5000; 

  // Arashi runs
//  private static final int ESTIMATION_SIZE = 500;   
// private static final long ESTIMATION_TIME = 2000; 

  // Original benchmarking
//  private static final int ESTIMATION_SIZE = 100;   
//  private static final long ESTIMATION_TIME = 1000; 

  private static final double SMOOTH_CONST = 0.1;
  private static final int NINETIETH = (int)((double)MEASUREMENT_SIZE * 0.9);

  private static final boolean BIDIRECTIONAL_FILTER = true;
  private static final double SMOOTH_CONST_UP = 0.9;
  private static final double SMOOTH_CONST_DOWN = 0.1;

  private SinkProxy sinkProxy;
  private long measurements[], sortedmeasurements[];
  private int curThreshold, cur_measurement;
  private double curRate; 
  private double measured_mu, measured_lambda, est_ninetiethRT;
  private double total_measured_mu, count_measured_mu, total_measured_lambda, 
    count_measured_lambda, total_est_ninetiethRT, count_est_ninetiethRT;
  private double ninetiethRT, totalNinetiethRT;
  private int countNinetiethRT;
  private long lasttime, totalProcTime;
  private long startProcTime, endProcTime;
  private int numProcessed, numReceived, numEst;
  private double avgNumThreads = 1.0;
  private int totalNumThreads = 0, countNumThreads = 0;
  private boolean enabled;

  public ResponseTimeControllerMM1(ManagerIF mgr, StageWrapperIF stage) throws IllegalArgumentException {
    super(mgr, stage);
    this.sinkProxy = (SinkProxy)stage.getStage().getSink();
    this.lasttime = System.currentTimeMillis();

    if (ADJUST_THRESHOLD) {
      this.pred = new QueueThresholdPredicate(stage.getStage().getSink(), INIT_THRESHOLD);
      this.curThreshold = ((QueueThresholdPredicate)pred).getThreshold();
    } 
    if (ADJUST_RATE) {
      this.pred = new RateLimitingPredicate(stage.getStage().getSink(), INIT_RATE, INIT_DEPTH);
      this.curRate = ((RateLimitingPredicate)pred).getTargetRate();
    }
    stage.getStage().getSink().setEnqueuePredicate(pred);
    enabled = true;

    this.measurements = new long[MEASUREMENT_SIZE];
    this.sortedmeasurements = new long[MEASUREMENT_SIZE];
    this.cur_measurement = 0;
    this.startProcTime = Long.MAX_VALUE; this.endProcTime = 0L;

    // Add profile 
    mgr.getProfiler().add("RTControllerMM1 90th-percentile RT <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)ninetiethRT;
	}
	});
    mgr.getProfiler().add("RTControllerMM1 lambda <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)measured_lambda;
	}
	});
    mgr.getProfiler().add("RTControllerMM1 mu <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)measured_mu;
	}
	});
    mgr.getProfiler().add("RTControllerMM1 est90thRT <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)est_ninetiethRT;
	}
	});
    mgr.getProfiler().add("RTControllerMM1 avgNumThreads <"+stage.getStage().getName()+">",
	new ProfilableIF() {
	public int profileSize() {
	return (int)avgNumThreads;
	}
	});

    if (ADJUST_THRESHOLD) {
      System.err.print("RTControllerMM1 <"+stage.getStage().getName()+">: ADJUST_THRESHOLD enabled, INIT_THRESHOLD="+INIT_THRESHOLD+", ESTIMATION_SIZE="+ESTIMATION_SIZE+", ESTIMATION_TIME="+ESTIMATION_TIME);
      mgr.getProfiler().add("RTControllerMM1 queueThreshold <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return curThreshold;
  	  }
  	  });
    } 

    if (ADJUST_RATE) {
      System.err.print("RTControllerMM1 <"+stage.getStage().getName()+">: ADJUST_RATE enabled, INIT_DEPTH="+INIT_DEPTH+", ESTIMATION_SIZE="+ESTIMATION_SIZE+", ESTIMATION_TIME="+ESTIMATION_TIME);
      if (BIDIRECTIONAL_FILTER) {
	System.err.println(", SMOOTH_CONST_UP="+SMOOTH_CONST_UP+", SMOOTH_CONST_DOWN="+SMOOTH_CONST_DOWN);
      } else {
	System.err.println(", SMOOTH_CONST="+SMOOTH_CONST);
      }

      mgr.getProfiler().add("RTControllerMM1 queueRate <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return (int)curRate;
  	  }
  	  });
      mgr.getProfiler().add("RTControllerMM1 tokenBucket <"+stage.getStage().getName()+">",
  	  new ProfilableIF() {
  	  public int profileSize() {
  	  return ((RateLimitingPredicate)pred).getBucketSize();
  	  }
  	  });
    }

    System.err.println("RTControllerMM1 <"+stage.getStage().getName()+">: initialized, targetRT="+targetRT+" ms");
  }

  public synchronized void enable() {
    if (enabled) return;

    System.err.println("RTControllerMM1 <"+stage.getStage().getName()+">: Enabling");
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
    System.err.println("RTControllerMM1 <"+stage.getStage().getName()+">: Disabling");
    this.pred = null;
    stage.getStage().getSink().setEnqueuePredicate(null);
    enabled = false;
  } 

  // Measure 90thRT, mu, lambda, estimate RT from model
  public synchronized void adjustThreshold(QueueElementIF fetched[], 
      long startTime, long endTime, boolean isFirst, int numThreads) {
//    if (DEBUG) System.err.println("RTControllerMM1 <"+stage.getStage().getName()+">: adjustThreshold called, fetched.len="+fetched.length+", time="+(endTime-startTime)+", isFirst="+isFirst+", numThreads="+numThreads);

    boolean adjust_meas = false;
    boolean adjust_est = false;

    if (MOVING_AVERAGE) {
      avgNumThreads = (SMOOTH_CONST * avgNumThreads) + ((1.0 - SMOOTH_CONST) * (double)(numThreads*1.0)); 
    } else {
      totalNumThreads += numThreads;
      countNumThreads ++;
      avgNumThreads = (totalNumThreads * 1.0) / (countNumThreads * 1.0);
    }

    numProcessed += fetched.length;
    totalProcTime += endTime - startTime;

    //if (startTime < startProcTime) startProcTime = startTime;
    //if (endTime > endProcTime) endProcTime = endTime;

    /*
    if ((endTime <= startProcTime) || (startTime >= endProcTime)) {
      totalProcTime += endTime - startTime;
      startProcTime = startTime; endProcTime = endTime;
    } else {
      if (startTime < startProcTime) {
	totalProcTime += startProcTime - startTime;
	startProcTime = startTime;
      }
      if (endTime > endProcTime) {
	totalProcTime += endTime - endProcTime;
	endProcTime = endTime;
      }
    }
    */

    //System.err.println("RTControllerMM1 <"+stage.getStage().getName()+"> S="+(startTime-startProcTime)+" E="+(endTime-startProcTime));

    if (!isFirst) return;

    // On every iteration reset the timespan
    //totalProcTime += endProcTime - startProcTime;
    //startProcTime = Long.MAX_VALUE; endProcTime = 0L;

    numReceived += sinkProxy.enqueueSuccessCount;
    sinkProxy.enqueueSuccessCount = 0;

    // Measure actual 90th RT
    long curtime = System.currentTimeMillis();
    for (int i = 0; i < fetched.length; i++) {
      if (fetched[i] instanceof TimeStampedEvent) {
	adjust_est = true;
	TimeStampedEvent ev = (TimeStampedEvent)fetched[i];
	long time = ev.timestamp;
	if (time != 0) {
	  measurements[cur_measurement] = curtime - time;
	  cur_measurement++; 
	  if (cur_measurement == MEASUREMENT_SIZE) {
	    cur_measurement = 0;
	    adjust_meas = true;
	    break; // XXX MDW TESTING
	  }
	}
      }
    }

    // XXX MDW: Continuously update
    adjust_meas = true;
    if (adjust_meas) {
      System.arraycopy(measurements, 0, sortedmeasurements, 0, MEASUREMENT_SIZE);
      Arrays.sort(sortedmeasurements);
      long cur = sortedmeasurements[NINETIETH];

      if (MOVING_AVERAGE) {
        ninetiethRT = (SMOOTH_CONST * (double)ninetiethRT*1.0) + ((1.0 - SMOOTH_CONST) * ((double)(cur) * 1.0)); 
      } else {
	totalNinetiethRT += cur;
	countNinetiethRT ++;
	ninetiethRT = (totalNinetiethRT * 1.0) / (countNinetiethRT * 1.0);
      }
      stage.getStats().record90thRT(ninetiethRT);

    }

    // XXX MDW: Always adjust estimated lambda/mu
    //if (!adjust_est) return;

    long elapsed = curtime - lasttime;
    numEst++; 
    if ((numEst == ESTIMATION_SIZE) || (elapsed >= ESTIMATION_TIME)) {
      numEst = 0; 
    } else {
      return;
    }

    lasttime = curtime;

    //System.err.println("RT: recv "+numReceived+" proc "+numProcessed);

    // Estimate 90th RT using M/M/1 model
    // Assume mu scales linearly with number of threads
    double mu_scaling = Math.log(avgNumThreads)+1.0;

    if (DEBUG) System.err.println("\nRT: numProcessed "+numProcessed+" mu_scaling "+mu_scaling+" totalProcTime "+totalProcTime);


    // Don't recalculate if we don't have enough data - avoid large mu 
    // spikes due to fast measurements
    if ((totalProcTime < 2) || (numProcessed < 2)) return;

    // XXX TESTING
    if (elapsed < 2) return;

    double cur_mu = (numProcessed * mu_scaling * 1.0) / (totalProcTime * 1.0e-3);
    double cur_lambda = (numReceived * 1.0) / (elapsed * 1.0e-3);

    if (MOVING_AVERAGE) { 
      if (BIDIRECTIONAL_FILTER) {
	if (cur_mu < measured_mu) {
	  measured_mu = (SMOOTH_CONST_DOWN * (double)measured_mu*1.0) + ((1.0 - SMOOTH_CONST_DOWN) * cur_mu);
	} else {
	  measured_mu = (SMOOTH_CONST_UP * (double)measured_mu*1.0) + ((1.0 - SMOOTH_CONST_UP) * cur_mu);
	}

      } else {
	measured_mu = (SMOOTH_CONST * (double)measured_mu*1.0) + ((1.0 - SMOOTH_CONST) * cur_mu);
      }

      measured_lambda = (SMOOTH_CONST * measured_lambda) + ((1.0 - SMOOTH_CONST) * cur_lambda);

    } else {
      total_measured_mu += numProcessed * mu_scaling;
      count_measured_mu += totalProcTime * 1.0e-3;
      measured_mu = total_measured_mu / count_measured_mu;
      total_measured_lambda += numReceived;
      count_measured_lambda += elapsed * 1.0e-3;
      measured_lambda = total_measured_lambda / count_measured_lambda;
    }

    double rho = measured_lambda/measured_mu;
    // XXX MDW: This is wrong - for waiting time
    //double est = (((1.0/measured_mu)/(1.0-rho)) * Math.log(10.0)) * 1.0e3;
    double est = (((1.0/measured_mu)/(1.0-rho)) * 2.3) * 1.0e3;
    if (DEBUG) System.err.println("\nRT: cur_mu "+cur_mu+", cur_lambda "+cur_lambda+", est90th "+est);
    if (est >= 0.0) {
      if (MOVING_AVERAGE) {
	est_ninetiethRT = (SMOOTH_CONST * (double)est_ninetiethRT*1.0) + ((1.0 - SMOOTH_CONST) * ((double)(est) * 1.0)); 
      } else {
	total_est_ninetiethRT += est;
	count_est_ninetiethRT ++;
	est_ninetiethRT = (total_est_ninetiethRT * 1.0) / (count_est_ninetiethRT * 1.0);
      }
    }

    numProcessed = 0; numReceived = 0; totalProcTime = 0L;

    if (DEBUG) System.err.println("RTControllerMM1 <"+stage.getStage().getName()+">: ninetiethRT "+ninetiethRT+" est "+MDWUtil.format(est_ninetiethRT)+" mu "+MDWUtil.format(measured_mu)+" lambda "+MDWUtil.format(measured_lambda));

    if (!enabled) return;

    // Now do threshold scaling
    if (ADJUST_THRESHOLD) {
      if (est < 0.0) {
	// If under overload
	if (DEBUG) System.err.println("RT: Overload detected");
	// XXX MDW TESTING: Was 0.9
	curThreshold = MIN_THRESHOLD;
      } else {
	if (est_ninetiethRT < (0.5 * targetRT)) {
	  curThreshold += 1;
	  if (curThreshold > MAX_THRESHOLD) curThreshold = MAX_THRESHOLD;
	} else if (est_ninetiethRT >= (1.2 * targetRT)) {
	  curThreshold /= 2;
	  if (curThreshold < MIN_THRESHOLD) curThreshold = MIN_THRESHOLD;
	}
      }
      ((QueueThresholdPredicate)pred).setThreshold(curThreshold);
      if (DEBUG) System.err.println("RTControllerMM1 <"+stage.getStage().getName()+"> threshold now "+curThreshold);
    }

    // Do rate scaling
    if (ADJUST_RATE) {
      if (est < 0.0) {
	// If under overload
	if (DEBUG) System.err.println("RT: Overload detected");
	// XXX MDW TESTING: Was 0.9
	this.curRate = measured_mu * 0.1;
      } else {

	// XXX TESTING: Maybe don't adjust if we are under the target, 
	// but, then we may be rejecting requests needlessly
//      } else if (est_ninetiethRT >= targetRT) {

	this.curRate = measured_mu - (2.302) / (targetRT / 1.0e3);
	if (this.curRate < 0.0) {
	  // If the target is not feasible
	  if (DEBUG) System.err.println("RT: Target infeasible");
	  this.curRate = measured_mu * 0.1;
	}
      } 

      if (DEBUG_CAP_RATE) {
	this.curRate = DEBUG_RATE;
      }

      this.curRate = Math.max(MIN_RATE, this.curRate);
      ((RateLimitingPredicate)pred).setTargetRate(this.curRate);

      if (DEBUG) System.err.println("RTControllerMM1 <"+stage.getStage().getName()+"> rate now "+curRate);
    }
  }

  public void adjustThreshold(QueueElementIF fetched[], long procTime) {
    throw new IllegalArgumentException("Not supported");
  }

}
