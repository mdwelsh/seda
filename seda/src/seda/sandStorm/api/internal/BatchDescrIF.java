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

import seda.sandStorm.api.QueueElementIF;

/**
 * BatchDescrIF describes a single batch of events that has been
 * released for processing by a stage's BatchSorterIF. 
 *
 * @see seda.sandStorm.core.BatchSorterIF
 */
public interface BatchDescrIF {

  /**
   * Returns the array of events in the batch.
   */
  public QueueElementIF[] getBatch();

  /**
   * Invoked by the thread manager when the event handler processing
   * this batch has returned. Can be used to indicate to the batch
   * sorter that it is safe to release the next batch associated 
   * with a particular session. For example, if threads in a stage
   * should never process packets for the same TCP connection concurrently,
   * calling batchDone() tells the sorter that it is safe to release
   * the next batch for this connection.
   */
  public void batchDone();

}
