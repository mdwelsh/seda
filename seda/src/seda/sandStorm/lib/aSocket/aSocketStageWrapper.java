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

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.*;
import seda.sandStorm.core.FiniteQueue;
import seda.sandStorm.core.QueueThresholdPredicate;
import seda.sandStorm.internal.AggThrottleBatchSorter;
import seda.sandStorm.internal.NullBatchSorter;
import seda.sandStorm.internal.Stage;
import seda.sandStorm.internal.StageStats;

/**
 * Internal stage wrapper implementation for aSocket.
 */
class aSocketStageWrapper implements StageWrapperIF {

  private String name;
  private StageIF stage;
  private EventHandlerIF handler;
  private ConfigDataIF config;
  private FiniteQueue eventQ;
  private SelectSourceIF selsource;
  private ThreadManagerIF tm;
  private StageStatsIF stats;
  private BatchSorterIF sorter;

  aSocketStageWrapper(ManagerIF mgr, String name, EventHandlerIF handler, ConfigDataIF config, ThreadManagerIF tm) {
    this.name = name;
    this.handler = handler;
    this.config = config;
    this.tm = tm;
    this.stats = new StageStats(this);

    int queuelen;
    if ((queuelen = config.getInt("_queuelength")) <= 0) {
      queuelen = -1;
    }
    if (queuelen == -1) {
      eventQ = new FiniteQueue(tm);
    } else {
      eventQ = new FiniteQueue(tm);
      QueueThresholdPredicate pred = new QueueThresholdPredicate(eventQ, queuelen);
      eventQ.setEnqueuePredicate(pred);
    }

    if (mgr.getConfig().getBoolean("global.batchController.enable")) {
      this.sorter = new AggThrottleBatchSorter();
    } else {
      this.sorter = new NullBatchSorter();
    }

    this.selsource = ((aSocketEventHandler)handler).getSelectSource();
    this.stage = new Stage(name, this, (SinkIF)eventQ, config);
    this.config.setStage(this.stage);
  }

  /**
   * Initialize this stage.
   */
  public void init() throws Exception {
    handler.init(config);
    tm.register(this);
  }

  /**
   * Destroy this stage.
   */
  public void destroy() throws Exception {
    tm.deregister(this);
    handler.destroy();
  }

  /**
   * Return the event handler associated with this stage.
   */
  public EventHandlerIF getEventHandler() {
    return handler;
  }

  /**
   * Return the stage handle for this stage.
   */
  public StageIF getStage() {
    return stage;
  }

  /**
   * Return the source from which events should be pulled to 
   * pass to this EventHandlerIF. <b>Note</b> that this method is not
   * used internally.
   */
  public SourceIF getSource() {
    return eventQ;
  }

  /**
   * Return the thread manager for this stage.
   */
  public ThreadManagerIF getThreadManager() {
    return tm;
  }

  // So aSocketTM can access it
  SelectSourceIF getSelectSource() {
    return selsource;
  }

  // So aSocketTM can access it
  SourceIF getEventQueue() {
    return eventQ;
  }

  public StageStatsIF getStats() {
    return stats;
  }

  /** Not implemented. */
  public void setBatchSorter(BatchSorterIF sorter) {
    return;
  }

  public BatchSorterIF getBatchSorter() {
    return sorter;
  }

  /** Not implemented. */
  public ResponseTimeControllerIF getResponseTimeController() {
    return null;
  }

  public String toString() {
    return "ASOCKETSW["+stage.getName()+"]";
  }

}

