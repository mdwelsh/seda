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

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.*;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * TPSThreadManager provides a threadpool-per-source-per-stage thread 
 * manager implementation. 
 * 
 * @author   Matt Welsh
 */

public class TPSThreadManager implements ThreadManagerIF, sandStormConst {

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_VERBOSE = false;

  protected ManagerIF mgr;
  protected SandstormConfigIF config;
  protected Hashtable srTbl;
  protected ThreadPoolController sizeController;
  protected boolean crashOnException;

  public TPSThreadManager(ManagerIF mgr) {
    this(mgr, true);
  }

  public TPSThreadManager(ManagerIF mgr, boolean initialize) {
    this.mgr = mgr;
    this.config = mgr.getConfig();

    if (initialize) {
      if (config.getBoolean("global.threadPool.sizeController.enable")) {
	sizeController = new ThreadPoolController(mgr);
      }
      srTbl = new Hashtable();
    }

    crashOnException = config.getBoolean("global.crashOnException");
  }

  /**
   * Register a stage with this thread manager.
   */
  public void register(StageWrapperIF stage) {
    // Create a threadPool for the stage
    stageRunnable sr = new stageRunnable(stage);
    srTbl.put(sr, stage);
  }

  /**
   * Deregister a stage with this thread manager.
   */
  public void deregister(StageWrapperIF stage) {
    Enumeration e = srTbl.keys();
    while (e.hasMoreElements()) {
      stageRunnable sr = (stageRunnable)e.nextElement();
      StageWrapperIF s = (StageWrapperIF)srTbl.get(sr);
      if (s == stage) {
	sr.tp.stop();
	srTbl.remove(sr);
      }
    }
  }

  /**
   * Stop the thread manager and all threads managed by it.
   */
  public void deregisterAll() {
    Enumeration e = srTbl.keys();
    while (e.hasMoreElements()) {
      stageRunnable sr = (stageRunnable)e.nextElement();
      StageWrapperIF s = (StageWrapperIF)srTbl.get(sr);
      sr.tp.stop();
      srTbl.remove(sr);
    }
  }

  /**
   * Wake any thread waiting for work.  This is called by
   * an enqueue* method of FiniteQueue.
   **/
  public void wake(){ /* do nothing*/ }

  /**
   * Internal class representing the Runnable for a single stage.
   */
  public class stageRunnable implements Runnable {

    protected ThreadPool tp;
    protected StageWrapperIF wrapper;
    protected BatchSorterIF sorter;
    protected EventHandlerIF handler;
    protected SourceIF source;
    protected String name;
    protected ResponseTimeControllerIF rtController = null;
    protected boolean firstToken = false;
    protected int blockTime = -1;

    protected stageRunnable(StageWrapperIF wrapper, ThreadPool tp) {
      this.wrapper = wrapper;
      this.tp = tp;
      this.init();
    }

    protected stageRunnable(StageWrapperIF wrapper) {
      this.wrapper = wrapper;
      // Create a threadPool for the stage
      if (wrapper.getEventHandler() instanceof SingleThreadedEventHandlerIF) {
	tp = new ThreadPool(wrapper, mgr, this, 1);
      } else {
	tp = new ThreadPool(wrapper, mgr, this);
      }
      this.init();
    }

    private void init() {
      this.source = wrapper.getSource();
      this.handler = wrapper.getEventHandler();
      this.name = wrapper.getStage().getName();
      this.rtController = wrapper.getResponseTimeController();

      if (tp != null) {
	blockTime = (int)tp.getBlockTime();
	if (sizeController != null) {
  	  // The sizeController is globally enabled -- has the user disabled
  	  // it for this stage?
  	  String val = config.getString("stages."+this.name+".threadPool.sizeController.enable");
  	  if ((val == null) || val.equals("true") || val.equals("TRUE")) {
  	    sizeController.register(wrapper, tp);
  	  }
   	}
      }

      this.sorter = wrapper.getBatchSorter();
      if (this.sorter == null) {
	// XXX MDW: Should be ControlledBatchSorter
	this.sorter = new NullBatchSorter(); 
      }
      sorter.init(wrapper, mgr);

      if (tp != null) tp.start();
    }

    public void run() {
      long t1, t2;
      long tstart = 0, tend = 0;

      if (DEBUG) System.err.println(name+": starting, source is "+source);

      t1 = System.currentTimeMillis();

      while (true) {

       	try {
	  if (DEBUG_VERBOSE) System.err.println(name+": Doing blocking dequeue for "+wrapper);

	  Thread.yield(); // only accomplishes delay

	  // Run any pending batches
	  boolean ranbatch = false;
	  BatchDescrIF batch;

	  while ((batch = sorter.nextBatch(blockTime)) != null) {
	    ranbatch = true;
	    QueueElementIF events[] = batch.getBatch();
	    if (DEBUG_VERBOSE) System.err.println("<"+name+">: Got batch of "+events.length+" events");

	    // Call event handler
	    tstart = System.currentTimeMillis();
	    handler.handleEvents(events);
	    batch.batchDone();
	    tend = System.currentTimeMillis();

	    // Record service rate 
	    wrapper.getStats().recordServiceRate(events.length, tend-tstart);

	    // Run response time controller 
	    if (rtController != null) {
	      rtController.adjustThreshold(events, tend-tstart);
	    }
	  }

	  // Check if idle
	  if (!ranbatch) {
	    t2 = System.currentTimeMillis();
	    if (tp.timeToStop(t2-t1)) {
	      if (DEBUG) System.err.println(name+": Exiting");
	      return;
	    }
	    continue;
	  } 

	  t1 = System.currentTimeMillis();

	  if (tp.timeToStop(0)) {
	    if (DEBUG) System.err.println(name+": Exiting");
	    return;
	  }

	} catch (Exception e) {
	  System.err.println("Sandstorm: Stage <"+name+"> got exception: "+e);
	  e.printStackTrace();
	  if (crashOnException) {
	    System.err.println("Sandstorm: Crashing runtime due to exception - goodbye");
	    System.exit(-1);
	  }
	}
      }
    }
  }

}

