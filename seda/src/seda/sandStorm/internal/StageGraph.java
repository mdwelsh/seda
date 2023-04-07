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

import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.internal.StageWrapperIF;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class provides an interface allowing operations to be performed
 * on the graph of stages within the application. Used internally
 * (for example, by AggThrottle) to determine stage connectivity and
 * communication statistics. Gathers data from sources such as SinkProxy.
 *
 * @author Matt Welsh
 * @see AggThrottle
 * @see SinkProxy
 */
public class StageGraph {
  private static final boolean DEBUG = false;

  private Vector stages = new Vector(1);
  private Vector edges = new Vector(1);
  private Hashtable threads = new Hashtable(1);
  private Hashtable edgesFrom = new Hashtable(1);
  private PrintWriter graphpw = null;

  StageGraph(ManagerIF mgr) {
    SandstormConfigIF config = mgr.getConfig();
    boolean dumpModuleGraph = config.getBoolean("global.profile.graph");
    if (dumpModuleGraph) {
      String gfilename = config.getString("global.profile.graphfilename");
      try {
	graphpw = new PrintWriter(new FileWriter(gfilename, true));
      } catch (IOException e) {
	System.err.println("StageGraph: Warning: Could not open file "+gfilename+" for writing, disabling graph dump.");
      }
    }
  }

  public synchronized StageWrapperIF[] getStages() {
    StageWrapperIF arr[] = new StageWrapperIF[stages.size()];
    stages.copyInto(arr);
    return arr;
  }

  public synchronized StageGraphEdge[] getEdges() {
    StageGraphEdge arr[] = new StageGraphEdge[edges.size()];
    edges.copyInto(arr);
    return arr;
  }

  public synchronized StageGraphEdge[] getEdgesFromStage(StageWrapperIF fromStage) {
    stageList list = (stageList)edgesFrom.get(fromStage);
    if (list == null) return null;
    else return list.getEdges();
  }

  public synchronized StageWrapperIF getStageFromThread(Thread thread) {
    return (StageWrapperIF)threads.get(thread);
  }

  public synchronized void addStage(StageWrapperIF stage) {
    if (DEBUG) System.err.println("StageGraph: Adding stage "+stage);
    if (!stages.contains(stage)) {
      stages.addElement(stage);
    }
  }

  public synchronized void addThread(Thread thread, StageWrapperIF stage) {
    if (DEBUG) System.err.println("StageGraph: Adding thread "+thread+" -> stage "+stage);
    addStage(stage);
    threads.put(thread, stage);
  }

  public synchronized void addEdge(StageGraphEdge edge) {
    if (!edges.contains(edge)) {
      if ((edge.fromStage == null) ||
	  (edge.toStage == null) ||
	  (edge.sink == null)) return;

      addStage(edge.fromStage);
      addStage(edge.toStage);

      if (DEBUG) System.err.println("StageGraph: Adding edge "+edge);

      edges.addElement(edge);
      stageList list = (stageList)edgesFrom.get(edge.fromStage);
      if (list == null) {
	list = new stageList();
	list.add(edge);
	edgesFrom.put(edge.fromStage, list);
      } else {
	list.add(edge);
      }
    } 
  }

  /**
   * Output the graph in a format that can be used by the AT&amp;T 
   * 'graphviz' program: http://www.research.att.com/sw/tools/graphviz/
   * Makes it easy to draw pretty pictures of stage graphs.
   */
  public synchronized void dumpGraph() {
    if (graphpw == null) return;
    graphpw.println("digraph sandstorm {");
    graphpw.println("  rankdir=TB;");
    Enumeration e = edges.elements();
    if (e != null) {
      while (e.hasMoreElements()) {
	StageGraphEdge edge = (StageGraphEdge)e.nextElement();
	String from = edge.fromStage.getStage().getName();
	String to = edge.toStage.getStage().getName();
	int count = 0;
	try {
	  count = ((SinkProxy)edge.sink).enqueueCount;
 	} catch (ClassCastException cce) {
	  // Ignore
	}
	graphpw.println("  \""+from+"\" -> \""+to+"\" [label=\""+count+"\"];");
      }
      graphpw.println("}");
    }
    graphpw.flush();
  }

  class stageList {
    Vector vec = new Vector(1);
    void add(StageGraphEdge edge) {
      vec.addElement(edge);
    }
    StageGraphEdge[] getEdges() {
      StageGraphEdge arr[] = new StageGraphEdge[vec.size()];
      vec.copyInto(arr);
      return arr;
    }
  }

}
