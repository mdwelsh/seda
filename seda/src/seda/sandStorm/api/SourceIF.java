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

package seda.sandStorm.api;

/**
 * A SourceIF implements the 'source side' of an event queue: it supports 
 * dequeue operations only.
 * 
 * @author   Matt Welsh
 */

public interface SourceIF {

  /**
   * Dequeues the next element, or returns <code>null</code> if there is
   * nothing left on the queue.
   *
   * @return the next <code>QueueElementIF</code> on the queue
   */
  public QueueElementIF dequeue();

  /**
   * Dequeues all available elements, or returns <code>null</code> if there is
   * nothing left on the queue.
   *
   * @return all pending <code>QueueElementIF</code>s on the queue
   */
  public QueueElementIF[] dequeue_all();

  /**
   * Dequeues at most <code>num</code> available elements, or returns 
   * <code>null</code> if there is nothing left on the queue.
   *
   * @return At most <code>num</code> <code>QueueElementIF</code>s on the queue
   */
  public QueueElementIF[] dequeue(int num);

  /**
   * Just like blocking_dequeue_all, but returns only a single element.
   */
  public QueueElementIF blocking_dequeue(int timeout_millis);

  /**
   * This method blocks on the queue up until a timeout occurs or
   * until an element appears on the queue. It returns all elements waiting 
   * on the queue at that time.
   *
   * @param timeout_millis if timeout_millis is <code>0</code>, this method
   *    will be non-blocking and will return right away, whether or not
   *    any elements are pending on the queue.  If timeout_millis is
   *    <code>-1</code>, this method blocks forever until something is 
   *    available.  If timeout_millis is positive, this method will wait 
   *    about that number of milliseconds before returning, but possibly a
   *    little more.
   *
   * @return an array of <code>QueueElementIF</code>'s.  This array will
   *    be null if no elements were pending.
   */
  public QueueElementIF[] blocking_dequeue_all(int timeout_millis);

  /**
   * This method blocks on the queue up until a timeout occurs or
   * until an element appears on the queue. It returns at most 
   * <code>num</code> elements waiting on the queue at that time.
   */
  public QueueElementIF[] blocking_dequeue(int timeout_millis, int num);

  /**
   * Returns the number of elements waiting in this queue.
   */
  public int size();


}

