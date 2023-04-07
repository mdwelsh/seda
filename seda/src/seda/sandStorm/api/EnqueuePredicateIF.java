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
 * Enqueue predicates allow users to specify a method that will
 * 'screen' elements being enqueued onto a sink, either accepting or
 * rejecting them. This mechanism can be used to implement many interesting
 * load-conditioning policies, for example, simple thresholding, rate
 * control, credit-based flow control, and so forth. Note that the enqueue
 * predicate runs in the context of the <b>caller of enqueue()</b>, which
 * means it must be simple and fast.
 * 
 * @author   Matt Welsh
 * @see SinkIF
 *
 */
public interface EnqueuePredicateIF {

  /**
   * Tests the given element for acceptance onto the queue.
   *
   * @param element  The <code>QueueElementIF</code> to enqueue
   * @return True if the sink accepts the element; false otherwise.
   */
  public boolean accept(QueueElementIF element);

}
