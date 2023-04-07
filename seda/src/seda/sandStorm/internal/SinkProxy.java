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
import seda.sandStorm.api.internal.StageWrapperIF;

import java.util.Hashtable;

/**
 * Used as a proxy to observe and measure communication behavior between 
 * stages. By handing out a SinkProxy instead of a FiniteQueue, it is
 * possible to gather statistics on event communication between stages.
 * This is used by StageGraph to construct a graph of the communication
 * patterns between stages.
 *
 * @author Matt Welsh
 */
public class SinkProxy implements SinkIF, ProfilableIF {

  private static final boolean DEBUG = false;

  private ManagerIF mgr;
  private StageWrapperIF toStage;
  private StageGraph stageGraph;
  public SinkIF thesink;
  private Thread client = null;
  private Hashtable clientTbl = null;

  /** 
   * Maintains a running sum of the number of elements enqueued onto 
   * this sink.
   */
  public int enqueueCount;

  /** 
   * Maintains a running sum of the number of elements successfully 
   * enqueued onto this sink (that is, not rejected by the enqueue predicate).
   */
  public int enqueueSuccessCount;

  /** 
   * Used to maintain a timer for statistics gathering. 
   */
  public long timer;

  /**
   * Create a SinkProxy for the given sink.
   *
   * @param sink The sink to create a proxy for.
   * @param mgr The associated manager.
   * @param toStage The stage which this sink pushes events to.
   */
  public SinkProxy(SinkIF sink, ManagerIF mgr, StageWrapperIF toStage) {
    this.thesink = sink;
    this.mgr = mgr;
    this.stageGraph = mgr.getProfiler().getGraphProfiler();
    this.toStage = toStage;
    this.enqueueCount = 0;
    this.enqueueSuccessCount = 0;
    this.timer = 0;
  }

  /** 
   * Return the size of the queue.
   */
  public int size() {
    if (thesink == null) return 0;
    return thesink.size();
  }

  public void enqueue(QueueElementIF enqueueMe) throws SinkException {
    recordUse();
    enqueueCount++; 
    thesink.enqueue(enqueueMe);
    enqueueSuccessCount++; 
  }

  public boolean enqueue_lossy(QueueElementIF enqueueMe) {
    recordUse();
    enqueueCount++; 
    boolean pass = thesink.enqueue_lossy(enqueueMe);
    if (pass) enqueueSuccessCount++;
    return pass;
  }

  public void enqueue_many(QueueElementIF[] enqueueMe) throws SinkException {
    recordUse();
    if (enqueueMe != null) {
      enqueueCount += enqueueMe.length;
    }
    thesink.enqueue_many(enqueueMe);
    if (enqueueMe != null) {
      enqueueSuccessCount += enqueueMe.length;
    }
  }

  /** 
   * Return the profile size of the queue.
   */
  public int profileSize() {
    return size();
  }

  public Object enqueue_prepare(QueueElementIF enqueueMe[]) throws SinkException {
    recordUse();
    if (enqueueMe != null) {
      enqueueCount += enqueueMe.length;
    }
    Object key = thesink.enqueue_prepare(enqueueMe);
    if (enqueueMe != null) {
      enqueueSuccessCount += enqueueMe.length;
    }
    return key;
  }

  public void enqueue_commit(Object key) {
    thesink.enqueue_commit(key);
  }

  public void enqueue_abort(Object key) {
    thesink.enqueue_abort(key);
  }

  public void setEnqueuePredicate(EnqueuePredicateIF pred) {
    thesink.setEnqueuePredicate(pred);
  }

  public EnqueuePredicateIF getEnqueuePredicate() {
    return thesink.getEnqueuePredicate();
  }

  public String toString() {
    return "[SinkProxy for toStage="+toStage+"]";
  }

  private void recordUse() {
    if (DEBUG) System.err.println("SinkProxy: Recording use of "+this+" by thread "+Thread.currentThread());

    if (client == null) {
      client = Thread.currentThread();

      StageGraphEdge edge = new StageGraphEdge();
      edge.fromStage = stageGraph.getStageFromThread(client);
      edge.toStage = toStage;
      edge.sink = this;
      stageGraph.addEdge(edge);

    } else {
      Thread t = Thread.currentThread();
      if (client != t) {
	if (clientTbl == null) clientTbl = new Hashtable();
	if (clientTbl.get(t) == null) {
	  clientTbl.put(t, t);

	  StageGraphEdge edge = new StageGraphEdge();
	  edge.fromStage = stageGraph.getStageFromThread(t);
	  edge.toStage = toStage;
	  edge.sink = this;
	  stageGraph.addEdge(edge);
	}
      }
    }
  }

}
