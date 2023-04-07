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

import seda.sandStorm.internal.StageGraph;

/**
 * A ProfilerIF is responsible for profiling the behavior of the system
 * over time. If the system is being run in profiling mode, applications
 * can get a handle to the ProfilerIF by invoking ManagerIF.getProfiler().
 *
 * @see ManagerIF
 * @author Matt Welsh
 */
public interface ProfilerIF {

  /**
   * Returns true if the system is being run in profiling mode;
   * false otherwise.
   */
  public boolean enabled();

  /**
   * Add a class to the profile. This will cause the profiler to track
   * the object's size over time.
   *
   * @param name The name of the object as it should appear in the profile.
   * @param pr The object to profile.
   */
  public void add(String name, ProfilableIF pr);

  /**
   * Return a handle to the graph profiler. 
   */
  public StageGraph getGraphProfiler();

}
