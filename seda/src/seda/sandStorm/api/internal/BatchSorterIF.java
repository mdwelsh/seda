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

package seda.sandStorm.api.internal;

import seda.sandStorm.api.ManagerIF;

/**
 * A BatchSorterIF is used to control the sorting and dispatching of
 * incoming events within a stage into batches. By implementing
 * BatchSorterIF and calling StageWrapperIF.setBatchSorter(),
 * a stage can control the way in which batches are passed to its
 * threads. 
 *
 * <p>With multiple threads in a stage, there is no guarantee on the 
 * order in which events will be processed. For example, multiple
 * incoming packets on the same TCP connection may be processed
 * out-of-order if they are dispatched to different threads. By
 * implementing BatchSorterIF the stage can control the order in which
 * batches are dispatched to its event handler.
 *
 * <p>BatchSorterIF is the low-level interface describing all batch
 * sorter operations; the utility class 
 * <tt>seda.sandStorm.internal.GenericBatchSorter</tt>
 * implements a "generic" BatchSorterIF that provides the most useful 
 * functionality and is easier to customize.
 *
 * @see seda.sandStorm.internal.GenericBatchSorter
 */

public interface BatchSorterIF {

  /**
   * Called by the thread manager to associate a stage with this
   * batch sorter.
   */
  public void init(StageWrapperIF stage, ManagerIF mgr);

  /**
   * Returns a single batch for processing by the stage's event handler.
   * Invoked by the thread manager when it is ready to invoke an 
   * event handler. Returns a BatchDescrIF, which contains the set of
   * events in the batch, as well as a "batchDone()" callback that is 
   * invoked by the thread manager after the event handler returns.
   * Blocks for up to timeout_millis milliseconds if no batch is
   * available.
   */
  public BatchDescrIF nextBatch(int timeout_millis);

}
