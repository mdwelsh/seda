/* 
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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

package seda.util;
import java.util.*;

/**
 * This is a debugging utility class that can be used to trace
 * events within an application and report the average time between
 * subsequent events. Each call to <tt>trace()</tt> records the
 * current time (using <tt>MDWUtil.currentTimeUsec()</tt> by default)
 * with an associated descriptive key. Calling <tt>dump()</tt> lists
 * all of the trace keys with the average amount of time elapsed
 * between the associated event and the <em>preceeding</em> event. 
 * 
 * <p>For example,
 * <pre>
 *   Tracer myTracer = new Tracer("Just for testing");
 *   myTracer.trace("event1");
 *   ...
 *   myTracer.trace("event2");
 * </pre>
 * The avergae time for 'event2' is the time elapsed between
 * the call to <tt>trace("event1")</tt> and <tt>trace("event2")</tt>.
 * The average time for 'event1' is that elapsed between the time the
 * tracer is created and the call to <tt>trace("event1")</tt>. 
 *
 * @author Matt Welsh
 */
public class Tracer {

  private boolean verbose = false;
  private static Hashtable trTbl = new Hashtable();

  private String name;
  private Hashtable valTbl;
  private traceVal initVal, lastTr;
  private int tv_create_count;

  private class traceVal {
    String key;
    long total = 0;
    long lastValue = 0;
    long count = 0;
    int create_count;

    traceVal(String key) {
      this.key = key;
      this.create_count = tv_create_count++;
    }

    public int hashCode() {
      return create_count;
    }
  }

  /** Reset this Tracer - clears all timings. */
  public synchronized void reset() {
    this.tv_create_count = 0;
    this.valTbl = new Hashtable();
    this.lastTr = null;
    this.trace("init", MDWUtil.currentTimeUsec());
    initVal = (traceVal)valTbl.get("init");
  }

  /** Create a new Tracer with the given descriptive name. */
  public Tracer(String name) {
    this.name = name;
    this.tv_create_count = 0;
    this.valTbl = new Hashtable();
    this.lastTr = null;
    trTbl.put(name, this);
    this.trace("init", MDWUtil.currentTimeUsec());
    initVal = (traceVal)valTbl.get("init");
  }

  /** 
   * Make this tracer "verbose" - for each call to trace(), prints a 
   * line to stderr with the trace key, current time, and time difference
   * from the previous call to trace().
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Return the Tracer with the given name.
   */
  public static Tracer getTracer(String name) {
    return (Tracer)trTbl.get(name);
  }

  /**
   * Trace an event with the given descriptive key and given time value
   * (typically in units of msec or usec).
   */
  public synchronized void trace(String key, long value) {
    traceVal tv = (traceVal)valTbl.get(key);
    if (tv == null) {
      tv = new traceVal(key);
      valTbl.put(key, tv);
    }
    tv.lastValue = value;
    tv.count++;

    if (lastTr == null) {
      tv.total = 0;
    } else {
      if (verbose) {
	System.err.println("T["+name+"]["+key+"] "+(value - initVal.lastValue)+" "+(value - lastTr.lastValue));
      }
      tv.total += (value - lastTr.lastValue);
    } 
    lastTr = tv;
  }

  /**
   * Trace an event with the given descriptive key and the current
   * time, from <tt>MDWUtil.currentTimeUsec()</tt>.
   */
  public synchronized void trace(String key) {
    trace(key, MDWUtil.currentTimeUsec());
  }

  /**
   * Dump the average time for each trace event to stderr.
   */
  public synchronized void dump() {
    System.err.println("--- Dump of Tracer ["+name+"] ---");
    Set s = valTbl.entrySet();
    Object vals[] = s.toArray();
    Arrays.sort(vals, new Comparator() {
	public int compare(Object o1, Object o2) {
	  Map.Entry e1 = (Map.Entry)o1;
	  Map.Entry e2 = (Map.Entry)o2;
	  traceVal tv1 = (traceVal)e1.getValue();
	  traceVal tv2 = (traceVal)e2.getValue();
	  if (tv1.create_count < tv2.create_count) {
	    return -1;
	  } else if (tv1.create_count > tv2.create_count) {
	    return 1;
          } else {
	    return 0;
	  }
	}
	public boolean equals(Object o1, Object o2) {
	  Map.Entry e1 = (Map.Entry)o1;
	  Map.Entry e2 = (Map.Entry)o2;
	  traceVal tv1 = (traceVal)e1.getValue();
	  traceVal tv2 = (traceVal)e2.getValue();
	  if (tv1.create_count == tv2.create_count) {
	    return true;
	  } else {
	    return false;
	  }
	}
	});
    for (int i = 0; i < vals.length; i++) {
      Map.Entry en = (Map.Entry)vals[i];
      traceVal tv = (traceVal)en.getValue();
      double usec_per = (tv.total * 1.0) / (tv.count * 1.0);
      String k = tv.key.substring(0,Math.min(tv.key.length(),30));
      for (int j = 30 - k.length(); j >= 0; j--) {
	k += " ";
      }
      System.err.println("    "+k+"  count "+tv.count+"  usec_per "+MDWUtil.format(usec_per));
    }
    System.err.println("--- End dump of Tracer ["+name+"] ---");
  }

  /**
   * Reset all tracers.
   */
  public static void resetAll() {
    Enumeration e = trTbl.elements();
    while (e.hasMoreElements()) {
      Tracer tr = (Tracer)e.nextElement();
      tr.reset();
    }
  }

  /**
   * Dump all tracers.
   */
  public static void dumpAll() {
    Enumeration e = trTbl.elements();
    while (e.hasMoreElements()) {
      Tracer tr = (Tracer)e.nextElement();
      tr.dump();
      System.err.println("");
    }
  }

}

