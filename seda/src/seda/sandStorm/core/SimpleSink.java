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

package seda.sandStorm.core;

import seda.sandStorm.api.*;

/**
 * The SimpleSink class is an abstract class which implements 
 * 'null' functionality for most of the administrative methods
 * of SinkIF. This class can be extended to implement simple SinkIF's 
 * which don't require most of the special behavior of the fully general
 * case.
 *
 * @author   Matt Welsh
 * @see      seda.sandStorm.api.SinkIF
 */
public abstract class SimpleSink implements SinkIF, ProfilableIF {

  /**
   * Must be implemented by subclasses. 
   */
  public abstract void enqueue(QueueElementIF enqueueMe) throws SinkException;

  /**
   * Calls enqueue() and returns false if SinkException occurs.
   */
  public synchronized boolean enqueue_lossy(QueueElementIF enqueueMe) {
    try {
      enqueue(enqueueMe);
      return true;
    } catch (SinkException se) {
      return false;
    }
  }

  /**
   * Simply calls enqueue() on each item in the array. Note that this
   * behavior <b>breaks</b> the property that <tt>enqueue_many()</tt> 
   * should be an "all or nothing" operation, since enqueue() might
   * reject some items but not others. Don't use SimpleSink if this is
   * going to be a problem.
   */
  public synchronized void enqueue_many(QueueElementIF[] enqueueMe) throws SinkException {
    for (int i = 0; i < enqueueMe.length; i++) {
      enqueue(enqueueMe[i]);
    }
  }

  /**
   * Not supported; throws an IllegalArgumentException.
   */
  public Object enqueue_prepare(QueueElementIF enqueueMe[]) throws SinkException {
    throw new IllegalArgumentException("enqueue_prepare not supported on SimpleSink objects");
  }

  /**
   * Not supported; throws an IllegalArgumentException.
   */
  public void enqueue_commit(Object key) {
    throw new IllegalArgumentException("enqueue_commit not supported on SimpleSink objects");
  }

  /**
   * Not supported; throws an IllegalArgumentException.
   */
  public void enqueue_abort(Object key) {
    throw new IllegalArgumentException("enqueue_abort not supported on SimpleSink objects");
  }

  /**
   * Not supported; throws an IllegalArgumentException.
   */
  public void setEnqueuePredicate(EnqueuePredicateIF pred) {
    throw new IllegalArgumentException("setEnqueuePredicate not supported on SimpleSink objects");
  }

  /**
   * Returns null.
   */
  public EnqueuePredicateIF getEnqueuePredicate() {
    return null;
  }

  /**
   * Returns 0.
   */
  public int size() {
    return 0;
  }

  /**
   * Returns size.
   */
  public int profileSize() {
    return size();
  }

}
