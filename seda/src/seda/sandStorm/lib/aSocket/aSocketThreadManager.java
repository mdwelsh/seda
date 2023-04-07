/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SourceIF;
import seda.sandStorm.api.internal.BatchDescrIF;
import seda.sandStorm.api.internal.BatchSorterIF;
import seda.sandStorm.api.internal.StageWrapperIF;
import seda.sandStorm.api.internal.ThreadManagerIF;
import seda.sandStorm.internal.ThreadPool;
import seda.util.Tracer;

/**
 * aSocketThreadManager provides a thread manager for the aSocket layer:
 * one thread for each of the read, write, and listen stages.
 * 
 * @author   Matt Welsh
 */
class aSocketThreadManager implements ThreadManagerIF, aSocketConst {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  private ManagerIF mgr;

  aSocketThreadManager(ManagerIF mgr) {
    this.mgr = mgr;
  }

  protected aSocketThread makeThread(aSocketStageWrapper wrapper) {
    return new aSocketThread(wrapper);
  }

  /**
   * Register a stage with this thread manager.
   */
  public void register(StageWrapperIF thestage) {
    aSocketStageWrapper stage = (aSocketStageWrapper)thestage;
    aSocketThread at = makeThread(stage);
    ThreadPool tp = new ThreadPool(stage, mgr, at, 1);
    at.registerTP(tp);
    tp.start();
  }

  /**
   * Deregister a stage with this thread manager.
   */
  public void deregister(StageWrapperIF stage) {
    throw new IllegalArgumentException("aSocketThreadManager: deregister not supported");
  }

  /**
   * Deregister all stages from this thread manager.
   */
  public void deregisterAll() {
    throw new IllegalArgumentException("aSocketThreadManager: deregisterAll not supported");
  }

  /**
   * Wake any thread waiting for work.  This is called by
   * an enqueue* method of FiniteQueue.
   **/
  public void wake(){ /* ignore */ }

  /**
   * Internal class representing a single aSocketTM-managed thread.
   */
  protected class aSocketThread implements Runnable {

    protected ThreadPool tp;
    protected StageWrapperIF wrapper;
    protected SelectSourceIF selsource;
    protected SourceIF eventQ;
    protected String name;
    protected EventHandlerIF handler;
    protected BatchSorterIF sorter;
    protected Tracer tracer;

    protected aSocketThread(aSocketStageWrapper wrapper) {
      if (DEBUG) System.err.println("!!!!!aSocketThread init");
      this.wrapper = wrapper;
      this.name = "aSocketThread <"+wrapper.getStage().getName()+">";
      this.selsource = wrapper.getSelectSource();
      this.eventQ = wrapper.getEventQueue();
      this.handler = wrapper.getEventHandler();
      this.sorter = wrapper.getBatchSorter();
      sorter.init(wrapper, mgr);

      if (PROFILE) {
	if (name.indexOf("WriteStage") != -1) {
	  this.tracer = WriteEventHandler.tracer;
	} else {
	  this.tracer = new Tracer(name);
	}
      }
    }

    void registerTP(ThreadPool tp) {
      this.tp = tp;
    }

    public void run() {
      if (DEBUG) System.err.println(name+": starting, selsource="+ selsource +", eventQ="+eventQ
          + ", handler=" + handler);

      while (true) {

	Thread.yield(); // Only accomplishes delay

        if (DEBUG) System.err.println(name+": Looping in run()");
	try {

	  while (selsource != null && selsource.numActive() == 0) {
	    if (DEBUG) System.err.println(name+": numActive is zero, waiting on event queue");

	    if (PROFILE) tracer.trace("sorter.nextBatch");
	    BatchDescrIF batch = sorter.nextBatch(EVENT_QUEUE_TIMEOUT);
	    if (batch != null) {
	      QueueElementIF qelarr[] = batch.getBatch();
	      if (DEBUG) System.err.println(name+": got "+qelarr.length+" new requests");
	      if (PROFILE) tracer.trace("sorter.nextBatch return non-null");
      	      handler.handleEvents(qelarr);
	      if (PROFILE) tracer.trace("handle batch return");
	    } else {
	      if (PROFILE) tracer.trace("sorter.nextBatch return null");
	    }
	  }

	  for (int s = 0; s < SELECT_SPIN; s++) {
	    if (DEBUG) System.err.println(name+": doing select, numActive "+selsource.numActive());
	    SelectQueueElement ret[];
	    if (PROFILE) tracer.trace("selsource.blocking_dequeue_all");
	    ret = (SelectQueueElement[])selsource.blocking_dequeue_all(SELECT_TIMEOUT);
	    if (ret != null) {
	      if (DEBUG) System.err.println(name+": select got "+ret.length+" elements");
	      if (PROFILE) tracer.trace("selsource return non-null");

	      long tstart = System.currentTimeMillis();
	      handler.handleEvents(ret);
	      long tend = System.currentTimeMillis();
	      wrapper.getStats().recordServiceRate(ret.length, tend-tstart);

	    } else {
	      if (DEBUG) System.err.println(name+": select got null");
	      if (PROFILE) tracer.trace("selsource return null");
	    }
	  }

	  if (DEBUG) System.err.println(name+": Checking request queue");
	  for (int s = 0; s < EVENT_QUEUE_SPIN; s++) {
	    if (PROFILE) tracer.trace("eventq nextBatch");
	    BatchDescrIF batch = sorter.nextBatch(0);
	    if (batch != null) {
	      if (PROFILE) tracer.trace("eventq nextBatch ret non-null");
	      QueueElementIF qelarr[] = batch.getBatch();
	      if (DEBUG) System.err.println(name+": got "+qelarr.length+" new requests");
	      handler.handleEvents(qelarr);
	      if (PROFILE) tracer.trace("eventq nextBatch handler done");
	      break;
	    } else {
	      if (PROFILE) tracer.trace("eventq nextBatch ret null");
	      //Thread.currentThread().yield();
	    }
	  }

	} catch (Exception e) {
	  System.err.println(name+": got exception "+e);
	  e.printStackTrace();
	}
      }
    }

  }

}

