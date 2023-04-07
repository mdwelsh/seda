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

package seda.sandStorm.lib.aDisk;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkException;
import seda.sandStorm.core.SimpleSink;

/**
 * Package-access only abstract class representing an implementation of
 * the AFile interface. By creating a subclass of AFileImpl and tying it
 * into the implementation-selection code in AFile, you can introduce new
 * implementations of AFile behind this interface.
 *
 * @author Matt Welsh
 * @see AFile
 */
abstract class AFileImpl extends SimpleSink {

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public abstract void enqueue(QueueElementIF req) throws SinkException;

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public abstract boolean enqueue_lossy(QueueElementIF req);

  /**
   * Enqueues the given requests (which must be AFileRequests)
   * to the file.
   */
  public abstract void enqueue_many(QueueElementIF[] elements) throws SinkException;

  /**
   * Return information on the properties of the file.
   */
  abstract AFileStat stat();

  /**
   * Close the file after all enqueued requests have completed.
   * Disallows any additional requests to be enqueued on this file.
   * A SinkClosedEvent will be posted on the file's completion queue
   * when the close is complete.
   */
  public abstract void close();

  /**
   * Causes a SinkFlushedEvent to be posted on the file's completion queue
   * when all pending requests have completed.
   */
  public abstract void flush();

}


