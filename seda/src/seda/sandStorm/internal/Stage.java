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

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.api.StageIF;
import seda.sandStorm.api.internal.StageWrapperIF;

/**
 * A Stage is a basic implementation of StageIF for application-level
 * stages.
 * 
 * @author   Matt Welsh
 */
public class Stage implements StageIF {

  private String name;
  private StageWrapperIF wrapper;
  private SinkIF mainsink;

  // If true, instantate a SinkProxy for the stage's event queue 
  // when batchControllor or rtController are enabled. This should
  // be obsolete; older implementations of these controllers relied
  // on the proxy, but it's no longer needed.
  private static final boolean ENABLE_SINK_PROXY = false;

  /**
   * Create a Stage with the given name, wrapper, and sink.
   */
  public Stage(String name, StageWrapperIF wrapper, SinkIF mainsink, ConfigDataIF config) {
    this.name = name;
    this.wrapper = wrapper;

    SandstormConfigIF cf = config.getManager().getConfig();
    this.mainsink = mainsink;

    if (ENABLE_SINK_PROXY && 
	(cf.getBoolean("global.batchController.enable") || 
	 cf.getBoolean("global.rtController.enable"))) {
      this.mainsink = new SinkProxy((SinkIF)mainsink, config.getManager(), wrapper);
    }
  }

  /**
   * Create a Stage with the given name and wrapper, with no sink.
   * This is used only for specialized stages.
   */
  public Stage(String name, StageWrapperIF wrapper) {
    this.name = name;
    this.wrapper = wrapper;
  }

  /**
   * Return the name of this stage.
   */
  public String getName() {
    return name;
  }

  /**
   * Return the event sink.
   */
  public SinkIF getSink() {
    return (SinkIF)mainsink;
  }

  /**
   * Return the stage wrapper for this stage.
   */
  public StageWrapperIF getWrapper() {
    return wrapper;
  }

  /**
   * Destroy this stage.
   */
  public void destroy() {
      // nothing to do really, StageWrapper takes care of everything
  }

}

