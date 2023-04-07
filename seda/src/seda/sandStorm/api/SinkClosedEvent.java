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
 * This event indicates that a sink has closed, either intentionally
 * by the application, or unintentionally, due to an error condition.
 * A sink is considered closed when it is no longer being serviced.
 * 
 * <p>As opposed to SinkClosedException (which is thrown immediately
 * if one tries to enqueue onto a closed sink), this event is pushed
 * to a stage if a sink closes asynchronously (that is, without being
 * requested by the application), or some time after the original 
 * enqueue occurred.
 *
 * @see SinkClosedException
 * @author Matt Welsh
 */
public class SinkClosedEvent implements QueueElementIF {

  /**
   * The sink that closed.
   */
  public SinkIF sink;

  /**
   * Create a new SinkClosedEvent with the given sink.
   */
  public SinkClosedEvent(SinkIF sink) {
    this.sink = sink;
  }

  public String toString() {
    return "SinkClosedEvent [sink="+sink+"]";
  }
}

