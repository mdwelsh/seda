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
 * A SinkIF implements the 'sink' end of a finite-length event queue: 
 * it supports enqueue operations only. These operations can throw a
 * SinkException if the sink is closed or becomes full, allowing event
 * queues to support thresholding and backpressure.
 * 
 * @author   Matt Welsh
 */
public interface SinkIF {

  /**
   * Enqueues the given element onto the queue.
   *
   * @param element  The <code>QueueElementIF</code> to enqueue
   * @exception SinkFullException Indicates that the sink is temporarily full.
   * @exception SinkClosedException Indicates that the sink is 
   *   no longer being serviced.
   */
  public void enqueue(QueueElementIF element) 
      throws SinkException;

  /**
   * Enqueues the given element onto the queue.
   *
   * This is lossy in that this method drops the element if the element 
   * could not be enqueued, rather than throwing a SinkFullException or 
   * SinkClosedException. This is meant as a convenience interface for 
   * "low priority" enqueue events which can be safely dropped. 
   *
   * @param element  The <code>QueueElementIF</code> to enqueue
   * @return true if the element was enqueued, false otherwise. 
   * 
   */
  public boolean enqueue_lossy(QueueElementIF element);

  /**
   * Given an array of elements, atomically enqueues all of the elements
   * in the array. This guarantees that no other thread can interleave its
   * own elements with those being inserted from this array. The 
   * implementation must enqueue all of the elements or none of them;
   * if a SinkFullException or SinkClosedException is thrown, none of
   * the elements will have been enqueued. This implies that the enqueue
   * predicate (if any) must accept all elements in the array for the
   * enqueue to proceed.
   *
   * @param elements The element array to enqueue
   * @exception SinkFullException Indicates that the sink is temporarily full.
   * @exception SinkClosedException Indicates that the sink is 
   *   no longer being serviced.
   *
   */
  public void enqueue_many(QueueElementIF[] elements) 
      throws SinkException;

  /**
   * Support for transactional enqueue.
   *
   * <p>This method allows a client to provisionally enqueue a number 
   * of elements onto the queue, and then later commit the enqueue (with
   * a <tt>enqueue_commit()</tt> call), or abort (with a 
   * <tt>enqueue_abort()</tt> call). This mechanism can be used to 
   * perform "split-phase" enqueues, where a client first enqueues a 
   * set of elements on the queue and then performs some work to "fill in"
   * those elements before performing a commit. This can also be used
   * to perform multi-queue transactional enqueue operations, with an
   * "all-or-nothing" strategy for enqueueing events on multiple queues.
   *
   * <p>This method would generally be used in the following manner:
   * <pre>
   *   Object key = sink.enqueue_prepare(someElements);
   *   if (can_commit) {
   *     sink.enqueue_commit(key);
   *   } else {
   *     sink.enqueue_abort(key);
   *   }
   * </pre>
   *
   * <p> Note that this method does <b>not</b> protect against
   * "dangling prepares" -- that is, a prepare without an associated
   * commit or abort operation. This method should be used with care.
   * In particular, be sure that all code paths (such as exceptions)
   * after a prepare include either a commit or an abort. 
   *
   * <p>Like <tt>enqueue_many</tt>, <tt>enqueue_prepare</tt> is an
   * "all or none" operation: the enqueue predicate must accept all
   * elements for enqueue, or none of them will be enqueued.
   *
   * @param elements The element array to provisionally enqueue
   * @return A "transaction key" that may be used to commit or abort
   *  the provisional enqueue
   * @exception SinkFullException Indicates that the sink is temporarily full
   *  and that the requested elements could not be provisionally enqueued.
   * @exception SinkClosedException Indicates that the sink is 
   *   no longer being serviced.
   *
   * @see enqueue_commit
   * @see enqueue_abort
   */
  public Object enqueue_prepare(QueueElementIF[] elements)
      throws SinkException;

  /**
   * Commit a previously prepared provisional enqueue operation (from
   * the <tt>enqueue_prepare()</tt> method). Causes the provisionally
   * enqueued elements to appear on the queue for future dequeue operations.
   * Note that once a <tt>enqueue_prepare()</tt> has returned an enqueue
   * key, the queue cannot reject the entries.
   * 
   * @param key The enqueue key returned by a previous call to 
   *  <tt>enqueue_prepare()</tt>.
   * @exception IllegalArgumentException Thrown if an unknown enqueue key 
   *  is provided.
   */
  public void enqueue_commit(Object enqueue_key);

  /**
   * Abort a previously prepared provisional enqueue operation (from
   * the <tt>enqueue_prepare()</tt> method). Causes the queue to discard
   * the provisionally enqueued elements.
   * 
   * @param key The enqueue key returned by a previous call to 
   *  <tt>enqueue_prepare()</tt>.
   * @exception IllegalArgumentException Thrown if an unknown enqueue key 
   *  is provided.
   */
  public void enqueue_abort(Object enqueue_key);

  /**
   * Set the enqueue predicate for this sink. This mechanism allows
   * user to define a method that will 'screen' QueueElementIF's during
   * the enqueue procedure to either accept or reject them. The enqueue
   * predicate runs in the context of the <b>caller of enqueue()</b>,
   * which means it must be simple and fast. This can be used to implement
   * many interesting queue-thresholding policies, such as simple count
   * threshold, credit-based mechanisms, and more.
   */
  public void setEnqueuePredicate(EnqueuePredicateIF pred);

  /**
   * Return the enqueue predicate for this sink.
   */
  public EnqueuePredicateIF getEnqueuePredicate();

  /**
   * Return the number of elements in this sink.
   */
  public int size();

}
