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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.SourceIF;

/**
 * A SelectSource is an implementation of SourceIF which pulls events from
 * the operating system via the NBIO SelectSet interface. This can be thought
 * of as a 'shim' which turns a SelectSet into a SourceIF. 
 *
 * <p>SelectSource can also "balances" the set of events returned on 
 * subsequent calls to dequeue, in order to avoid biasing the servicing of
 * underlying O/S events to a particular order. This feature can be 
 * disabled by creating a SelectSource with the boolean flag 'do_balance'.
 *
 * @author Matt Welsh 
 */
public interface SelectSourceIF extends SourceIF {

  /**
   * Register a SelectItem with the SelectSource. The SelectItem should 
   * generally correspond to a Selectable along with a set of event flags
   * that we wish this SelectSource to test for. 
   *
   * <p>The user is allowed to modify the event flags in the SelectItem 
   * directly (say, to cause the SelectSource ignore a given SelectItem for 
   * the purposes of future calls to one of the dequeue methods). However,
   * modifying the event flags may not be synchronous with calls to dequeue -
   * generally because SelectSource maintains a cache of recently-received
   * events.
   *
   * @see seda.nbio.Selectable
   */
  // SelectItem sel
  public void register(Object sel);

  // SelectableChannel sc
  public Object register(Object sc, int ops);

  /**
   * Deregister a SelectItem with this SelectSource.
   * Note that after calling deregister, subsequent calls to dequeue
   * may in fact return this SelectItem as a result. This is because
   * the SelectQueue internally caches results.
   */
  public void deregister(Object sel);

  /**
   * Must be called if the 'events' mask of any SelectItem registered
   * with this SelectSource changes. Pushes event mask changes down to
   * the underlying event-dispatch mechanism.
   */
  public void update();

  /**
   * Must be called if the 'events' mask of this SelectItem (which
   * must be registered with this SelectSource) changes. Pushes 
   * event mask changes down to the underlying event-dispatch mechanism.
   */
  public void update(Object sel);

  /**
   * Return the number of SelectItems registered with the SelectSource.
   */
  public int numRegistered();
  
  /**
   * Return the number of active SelectItems registered with the SelectSource.
   * An active SelectItem is one defined as having a non-zero events
   * interest mask.
   */
  public int numActive();

  /**
   * Return the number of elements waiting in the queue (that is,
   * which don't require a SelectSet poll operation to retrieve).
   */
  public int size();

  /**
   * Nudge the selector out of a poll() so that a StartReading or similar
   * event in the queue can be processed at once.
   */
  public void interruptSelect();

  /* 

  // Actually performs the poll and sets ready[], ready_off, ready_size
  private void doPoll(int timeout);

  // Balances selarr[] by shuffling the entries - sets ready[]
  private void balance(SelectItem selarr[]);

  // Initialize the balancer
  private void initBalancer();
  */

}

