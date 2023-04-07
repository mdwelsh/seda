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

package seda.sandStorm.internal;

import seda.sandStorm.api.EnqueuePredicateIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.internal.ResponseTimeControllerIF;
import seda.sandStorm.api.internal.StageWrapperIF;

/**
 * The ResponseTimeController attempts to keep the response time of
 * a given stage below a given target by adjusting admission control
 * parameters for a stage.
 * 
 * @author   Matt Welsh
 */
public abstract class ResponseTimeController implements ResponseTimeControllerIF {

  protected final static int INIT_THRESHOLD = 1;
  protected final static int MIN_THRESHOLD = 1;
  protected final static int MAX_THRESHOLD = 1024;

  protected StageWrapperIF stage;
  protected EnqueuePredicateIF pred;
  protected double targetRT;

  protected ResponseTimeController(ManagerIF mgr, StageWrapperIF stage) throws IllegalArgumentException {
    this.stage = stage;

    SandstormConfigIF config = mgr.getConfig();
    this.targetRT = config.getDouble("stages."+stage.getStage().getName()+".rtController.targetResponseTime");
    if (this.targetRT == -1) {
      this.targetRT = config.getDouble("global.rtController.targetResponseTime");
      if (this.targetRT == -1) {
	throw new IllegalArgumentException("ResponseTimeController: Must specify targetResponseTime");
      }
    }
  }

  public void setTarget(double target) {
    this.targetRT = target;
  }

  public double getTarget() {
    return targetRT;
  }

  public abstract void adjustThreshold(QueueElementIF fetched[], long serviceTime);
  public abstract void enable();
  public abstract void disable();

}
