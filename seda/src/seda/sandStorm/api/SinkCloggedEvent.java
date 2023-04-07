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
 * This event indicates that a sink was clogged when trying to process
 * the given element. A sink is considered clogged if it is full (that is,
 * its length threshold has been reached), or some other condition is
 * preventing the given element from being serviced. 
 *
 * <p>As opposed to SinkFullException, which is thrown immediately
 * if attempting to enqueue onto a full sink, SinkCloggedEvent is 
 * pushed to an application if a sink becomes full asynchronously,
 * or if some other condition caused the sink to become clogged.
 *
 * @see SinkFullException
 * @author Matt Welsh
 */
public class SinkCloggedEvent implements QueueElementIF {

  /**
   * The sink which clogged.
   */
  public SinkIF sink;

  /**
   * The element which clogged.
   */
  public QueueElementIF element;

  /**
   * Create a new SinkCloggedEvent with the given sink and element.
   */
  public SinkCloggedEvent(SinkIF sink, QueueElementIF element) {
    this.sink = sink;
    this.element = element;
  }
}

