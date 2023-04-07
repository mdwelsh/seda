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

import seda.sandStorm.api.*;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.ssTimer;

public class TimerHandler implements EventHandlerIF {

  private static final boolean DEBUG = true;

  private SinkIF nextHandlerSink, mysink;
  private ssTimer timer;
  private int DELAY_TIME;

  public TimerHandler() {
  }

  public void init(ConfigDataIF config) throws Exception {
    mysink = config.getStage().getSink();

    if (config.getString("delay") == null) 
      System.err.println("Must specify delay!");
    if (config.getString("next_handler") == null) 
      System.err.println("Must specify next_handler!");

    DELAY_TIME = Integer.parseInt(config.getString("delay"));

    nextHandlerSink = config.getManager().getStage(config.getString("next_handler")).getSink();

    System.err.println("delay="+DELAY_TIME);

    timer = new ssTimer();
    timer.registerEvent(DELAY_TIME, new BufferElement(200), mysink);

  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("TimerHandler: GOT QEL: "+item);

    try {
      nextHandlerSink.enqueue(item);
    } catch (SinkException se) {
      System.err.println("Got SinkException: "+se);
    }

    timer.registerEvent(DELAY_TIME, item, mysink);
  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}

