/* 
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SourceIF;
import seda.sandStorm.api.internal.BatchDescrIF;
import seda.sandStorm.api.internal.BatchSorterIF;
import seda.sandStorm.api.internal.StageWrapperIF;

/**
 * An implementation of BatchSorter that uses the AggThrottle
 * mechanism to automatically determine the batch size.
 */
public class AggThrottleBatchSorter implements BatchSorterIF {

  private static final boolean DEBUG = false;

  private String name;
  private AggThrottle aggThrottle;
  private SourceIF source;

  public AggThrottleBatchSorter() {
  }

  /**
   * Called by the thread manager to associate a queue with this
   * batch sorter.
   */
  public void init(StageWrapperIF stage, ManagerIF mgr) {
    this.aggThrottle = new AggThrottle(stage, mgr);
    this.source = stage.getSource();
    this.name = stage.getStage().getName();
  }

  /**
   * Returns a single batch for processing by the stage's event handler.
   * Blocks until a batch can be returned.
   */
  public BatchDescrIF nextBatch(int timeout) {
    int aggTarget = aggThrottle.getAggTarget();

    final QueueElementIF elemarr[];
    if (aggTarget == -1) {
      elemarr = source.blocking_dequeue_all(timeout);
    } else {
      elemarr = source.blocking_dequeue(timeout, aggTarget);
    }

    if (elemarr == null) return null;
    else return new BatchDescrIF() {
      public QueueElementIF[] getBatch() {
	return elemarr;
      }
      public void batchDone() {
	// Empty
      }
    };
  }

}
