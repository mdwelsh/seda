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

package seda.sandStorm.api.internal;

import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.SourceIF;
import seda.sandStorm.api.StageIF;

/**
 * A StageWrapperIF is the internal representation for an application 
 * stage - an event handler coupled with a set of queues.
 * 
 * @author   Matt Welsh
 */
public interface StageWrapperIF {

  /**
   * Return the StageIF for this stage.
   */
  public StageIF getStage();

  /**
   * Return the event handler associated with this stage.
   */
  public EventHandlerIF getEventHandler();

  /**
   * Return the source from which events should be pulled to 
   * pass to this EventHandlerIF.
   */
  public SourceIF getSource();

  /**
   * Return the thread manager which will run this stage.
   */
  public ThreadManagerIF getThreadManager();

  /**
   * Return a StageStatsIF interface which records and manages performance
   * statistics for this stage.
   */
  public StageStatsIF getStats();

  /**
   * Return the response time controller for this stage.
   */
  public ResponseTimeControllerIF getResponseTimeController();

  /**
   * Return the batch sorter for this stage.
   */
  public BatchSorterIF getBatchSorter();

  /**
   * Set the batch sorter for this stage.
   */
  public void setBatchSorter(BatchSorterIF sorter);

  /**
   * Initialize this stage.
   */
  public void init() throws Exception;

  /**
   * Destroy this stage.
   */
  public void destroy() throws Exception;

}

