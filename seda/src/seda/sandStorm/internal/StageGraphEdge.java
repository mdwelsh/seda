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

import seda.sandStorm.api.SinkIF;
import seda.sandStorm.api.internal.StageWrapperIF;

/**
 * This class represents an edge in the stage-connectivity graph.
 * Used by StageGraph.
 *
 * @author Matt Welsh
 */
class StageGraphEdge {

  StageWrapperIF fromStage;
  StageWrapperIF toStage;
  SinkIF sink;

  public boolean equals(Object o) {
    if (!(o instanceof StageGraphEdge)) return false;
    StageGraphEdge e = (StageGraphEdge)o;
    if ((e.fromStage.equals(fromStage)) &&
	(e.toStage.equals(toStage)) &&
	(e.sink.equals(sink))) {
      return true;
    } else {
      return false;
    }
  }

  public String toString() {
    return "StageGraphEdge [from="+fromStage+", to="+toStage+", sink="+sink+"]";
  }

}
