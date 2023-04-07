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
 * ManagerIF represents the system manger, which provides various 
 * runtime services to applications, such as access to other stages.
 * 
 * @author Matt Welsh
 */
public interface ManagerIF {

  /**
   * Each stage may have multiple event queues associated with it.
   * This is the name of the 'main' event queue for a given stage, and
   * is the default sink returned by a call to StageIF.getSink().
   *
   * @see StageIF
   */
  public static final String MAINSINK = "main";

  /**
   * Return a handle to the stage with the given name.
   *
   * @exception NoSuchStageException Thrown if the stage does not exist.
   */
  public StageIF getStage(String stagename) throws NoSuchStageException;

  /**
   * Create a stage with the given name, event handler, and initial
   * arguments. This method can be used by applications to create
   * new stages at runtime. 
   *
   * <p>The default stage wrapper and thread manager are used; 
   * the sandStorm.api.SystemManagerIF interface provides a lower-level 
   * mechanism in case the application has a need to specify these
   * explicitly.
   *
   * @param stagename The name under which the new stage should be registered.
   * @param eventHandler The event handler object which should be associated
   *   with the new stage.
   * @param initargs The initial arguments to the stage, to be passed to
   *   the new stage through a ConfigDataIF.
   *
   * @return A handle to the newly-created stage.
   * @exception Exception If an exception occurred during stage 
   *   creation or initialization.
   *
   * @see seda.sandStorm.api.internal.SystemManagerIF
   * @see ConfigDataIF
   */
  public StageIF createStage(String stagename, EventHandlerIF eventHandler,
      String initargs[]) throws Exception;

  /**
   * Returns a handle to the system signal interface.
   */
  public SignalMgrIF getSignalMgr();

  /**
   * Returns a handle to the system profiler. 
   */
  public ProfilerIF getProfiler();

  /**
   * Returns a copy of the SandstormConfig for this Manager. This contains all
   * of the global options used by the runtime system. Note that modifying
   * any options of this copy does not in fact change the runtime parameters
   * of the system; this is used for informational purposes only.
   */
  public SandstormConfigIF getConfig();

  /**
   * Destroy the given stage, and remove all references to it that are
   * being maintained by SandStorm
   *
   * @exception Exception if anything goes wrong during the destruction
   */
  public void destroyStage(String stagename) throws Exception;

}
