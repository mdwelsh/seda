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

import seda.sandStorm.api.SignalIF;
import seda.sandStorm.api.SignalMgrIF;
import seda.sandStorm.api.SinkIF;

import java.util.Hashtable;
import java.util.Vector;

/**
 * The SignalMgr is an implementation of SignalMgrIF. It allows stages
 * to register to receive certain signals and delivers those signals once
 * they are triggered.
 * 
 * @author Matt Welsh
 * @see SignalMgrIF
 * @see SignalIF
 */
class SignalMgr implements SignalMgrIF {

  private Hashtable signalTbl; // Map signal type to Vector of sinks

  SignalMgr() {
    signalTbl = new Hashtable();
  }

  /**
   * Register for the given signal type. When the signal is triggered,
   * an object of the given type (although not necessarily the same
   * object instance) will be delivered to the given SinkIF.
   */
  public void register(SignalIF signalType, SinkIF sink) {
    Class type = signalType.getClass();
    Vector vec = (Vector)signalTbl.get(type);
    if (vec == null) {
      vec = new Vector();
      vec.addElement(sink);
      signalTbl.put(type, vec);
    } else {
      for (int i = 0; i < vec.size(); i++) {
	SinkIF s = (SinkIF)vec.elementAt(i);
	if (s.equals(sink)) throw new IllegalArgumentException("Sink "+sink+" already registered for signal type "+type);
      }
      vec.addElement(sink);
    }
  }

  /**
   * Deregister for the given signal type. 
   */
  public void deregister(SignalIF signalType, SinkIF sink) {
    Class type = signalType.getClass();
    Vector vec = (Vector)signalTbl.get(type);
    if (vec == null) {
      throw new IllegalArgumentException("Sink "+sink+" not registered for signal type "+type);
    } else {
      for (int i = 0; i < vec.size(); i++) {
	SinkIF s = (SinkIF)vec.elementAt(i);
	if (s.equals(sink)) {
	  vec.removeElementAt(i);
	  return;
	}
      }
      throw new IllegalArgumentException("Sink "+sink+" not registered for signal type "+type);
    }
  }

  /**
   * Send the given signal to all registered sinks. Uses enqueue_lossy
   * on each sink, so if a sink rejects the signal this method will
   * continue regardless. Package access only.
   *
   * XXX MDW: Really should register sinks with the chain of superclasses
   * for each signal as well, so that triggering a superclass of a given
   * signal will also reach those sinks registered for the subclass.
   */
  public void trigger(SignalIF signal) {
    Class type = signal.getClass();
    Vector vec = (Vector)signalTbl.get(type);
    if (vec == null) return;
    for (int i = 0; i < vec.size(); i++) {
      SinkIF s = (SinkIF)vec.elementAt(i);
      s.enqueue_lossy(signal);
    }
  }


}
