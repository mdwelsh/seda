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

import seda.sandStorm.api.internal.StageWrapperIF;

/**
 * A StageIF represents a handle to an application stage. Applications
 * to not implement StageIF directly; rather, they implement EventHandlerIF.
 * A StageIF is used by an event handler to access other stages and is
 * obtained by a call to ManagerIF.getStage().
 *
 * @see EventHandlerIF
 * @see ManagerIF
 * @author   Matt Welsh
 */
public interface StageIF {

  /**
   * Return the name of this stage.
   */
  public String getName();

  /**
   * Return the event sink for this stage. 
   */
  public SinkIF getSink();

  /**
   * Return the stage wrapper associated with this stage.
   * For internal use.
   */
  public StageWrapperIF getWrapper();

  /**
   * Destroy the given stage. Removes the stage from the system and 
   * invokes its event handler's destroy() method. Stage destruction may
   * be delayed until all pending events for the stage have been processed.
   */
  public void destroy();

}

