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

package seda.sandStorm.core;

import seda.sandStorm.api.ProfilableIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;

/**
 * The ssTimer class provides a mechanism for registering
 * timer events that will go off at some future time.  The future time
 * can be specified in absolute or relative terms.  When the timer goes
 * off, an element is placed on a queue.  There is no way to unregister
 * a timer.  Events will be delivered guaranteed, but the time that they
 * are delivered may slip depending on stuff like how loaded the system
 * is and all that.
 * <P>
 * WARNING: you should use cancelEvent to cancel timers that you no longer
 * need, otherwise you will waste many, many cycles on unneeded timer
 * firings.  This was the bottleneck in vSpace and the DDS before we
 * fixed it.  For example, if you set a timer to go off on a cross-CPU
 * task to detect failure, then if the task returns successfully, cancel
 * the timer!
 *
 * @author   Matt Welsh and Steve Gribble
 */

public class ssTimer implements Runnable, ProfilableIF {

  private static final boolean DEBUG = false;
  private ssTimerEvent                 head_event = null;
  private ssTimerEvent                 tail_event = null;
  private Thread                     thr;
  private Object                     sync_o;
  private boolean                    die_thread;
  private int                        num_events = 0;

  public ssTimer() {
    sync_o = new Object();
    die_thread = false;
    thr = new Thread(this, "SandStorm ssTimer thread");
    thr.start();
  }

  public static class ssTimerEvent {
    public long            time_millis;
    public QueueElementIF  obj;
    public SinkIF queue;
    public ssTimerEvent      nextE;
    public ssTimerEvent      prevE;
    
    public ssTimerEvent(long m, QueueElementIF o, SinkIF q) {
      time_millis = m;
      obj = o;
      queue = q;
      nextE = null;
      prevE = null;
    }

    public String toString() {
      return "ssTimerEvent<"+hashCode()+">";
    }
  }

  /**
   * Object <code>obj</code> will be placed on SinkIF <code>queue</code>
   * no earlier than <code>millis</code> milliseconds from now.
   *
   * @param millis  the number of milliseconds from now when the event will
   *                take place
   * @param obj     the object that will be placed on the queue
   * @param queue   the queue on which the object will be placed
   */
  public ssTimer.ssTimerEvent registerEvent(long millis, QueueElementIF obj,
					  SinkIF queue) {
    long time_millis = System.currentTimeMillis() + millis;
    ssTimerEvent newTimer = new ssTimerEvent(time_millis, obj, queue);
    
    insertEvent(newTimer);

    return newTimer;
  }

  /**
   * Object <code>obj</code> will be placed on SinkIF <code>queue</code>
   * no earlier than absolute time <code>the_date</code>.
   *
   * @param the_date the date when the event will take place - if this date
   *                 is in the past, the event will happen right away
   * @param obj      the object that will be placed on the queue
   * @param queue    the queue on which the object will be placed
   */
  public ssTimer.ssTimerEvent registerEvent(java.util.Date the_date,
					  QueueElementIF obj,
					  SinkIF queue) {
    ssTimerEvent newTimer = new ssTimerEvent(the_date.getTime(),
					 obj, queue);
    insertEvent(newTimer);

    return newTimer;
  }

  /**
   * Kills off this timer object, dropping all pending events on floor.
   */
  public void doneWithTimer() {
    die_thread = true;

    synchronized(sync_o) {
      sync_o.notify();
    }
  }

  /**
   * How many events yet to fire?
   */
  public int size() {
    return num_events;
  }

  /**
   * Return the profile size of this timer.
   */
  public int profileSize() {
    return size();
  }

  /**
   * Cancels all events.
   */
  public void cancelAll() {
    synchronized(sync_o) {
      head_event = tail_event = null;
      num_events = 0;
    }
  }

  /**
   * Cancels the firing of this timer event.
   *
   * @param evt   the ssTimer.ssTimerEvent to cancel.  This ssTimerEvent
   *              is returned to you when you call registerEvent
   */
  public void cancelEvent(ssTimerEvent evt) {
    if (evt == null)
      return;

    try {
      synchronized(sync_o) {
	if (evt == tail_event) {

	  // is this only list item?
	  if (tail_event == head_event) {
	    tail_event = head_event = null;
	    num_events--;
	    return;
	  }

	  // not only list item, is at tail, so lop off tail
	  tail_event = tail_event.prevE;
	  tail_event.nextE = null;
	  num_events--;
	  return;

	} else if (evt == head_event) {

	  // not only list item, is at head, so lop off head
	  head_event = head_event.nextE;
	  head_event.prevE = null;
	  num_events--;
	  return;

	} else {

	  // make sure event didn't fire already
	  if ((evt.prevE != null) && (evt.nextE != null)) {
	    // in middle somewhere
	    evt.prevE.nextE = evt.nextE;
	    evt.nextE.prevE = evt.prevE;
	    num_events--;
	    return;
	  }
	}
      }
    } finally {
      evt.nextE = null;
      evt.prevE = null;
    }
  }

  // takes the event, does insertion-sort into ssTimerEvent linked list
  private void insertEvent(ssTimerEvent newTimer) {
    boolean do_notify = false;

    synchronized(sync_o) {
      if (head_event == null) {
        // list empty
	if (DEBUG) System.err.println("ssTimer: Inserting first event, num pending "+num_events+" event "+newTimer);
	tail_event = newTimer;
	head_event = newTimer;
	num_events++;
        do_notify = true;
      } else if (head_event.time_millis > newTimer.time_millis) {
	// insert head
	if (DEBUG) System.err.println("ssTimer: Inserting event at head, num pending "+num_events+" event "+newTimer);
	newTimer.nextE = head_event;
	head_event.prevE = newTimer;
	head_event = newTimer;
	num_events++;
        do_notify = true;
      } else if (tail_event.time_millis <= newTimer.time_millis) {
        // insert tail
	if (DEBUG) System.err.println("ssTimer: Inserting event at tail, num pending "+num_events+" event "+newTimer);
	newTimer.prevE = tail_event;
	tail_event.nextE = newTimer;
	tail_event = newTimer;
	num_events++;
	// if not insert at head, no notify! :)
      } else {
        // insert somewhere in middle :(
	if (DEBUG) System.err.println("ssTimer: Inserting new event in middle, num pending "+num_events+" event "+newTimer);
        ssTimerEvent prevE = tail_event.prevE;
        ssTimerEvent curE = tail_event;
        boolean gotit = false;
        while((prevE != null) && (gotit == false)) {
          if (prevE.time_millis <= newTimer.time_millis) {
	    prevE.nextE = newTimer;
	    curE.prevE = newTimer;
	    newTimer.nextE = curE;
	    newTimer.prevE = prevE;
	    // if not insert at head, no notify! :)
            gotit = true;
	  }
	  curE = prevE;
	  prevE = prevE.prevE;
        }
	num_events++;
      }

      if (do_notify) {
	sync_o.notify();
      }
    }
  }

  private void process_head() {

    ssTimerEvent fire = null;
    long wait_time = -1;

    long curTime = System.currentTimeMillis();

    if (head_event.time_millis <= curTime) {
      // fire off event
      fire = head_event;
      if (DEBUG) System.err.println("Firing "+fire+" -> "+head_event.nextE+" "+(num_events-1)+" pending");
      head_event = head_event.nextE;
      if (head_event == null) {
	// was only event
	tail_event = null;
      } else {
	// reset back pointer
	head_event.prevE = null;
      }

      if ((head_event == null) && (num_events != 1)) {
	System.err.println("ssTimer: Warning: No more events to process, but still have "+(num_events-1)+" pending. This is a bug; please contact <mdw@cs.berkeley.edu>");
      }

      fire.nextE = null;
      fire.prevE = null;
      num_events--;

    } else {
      // sleep till head
      if (DEBUG) System.err.println("ssTimer: head is "+(head_event.time_millis - curTime)+" ms in the future");
      wait_time = head_event.time_millis - curTime;
      if (wait_time != -1) {
	try {
	  sync_o.wait(wait_time);
	} catch (InterruptedException ie) {
	  // Ignore
	}
      }
    }

    if (fire != null) {
      fire.queue.enqueue_lossy(fire.obj);
    } 
  }

  public void run() {
    synchronized(sync_o) {
      while(die_thread == false) {
	try {
       	  if (head_event != null) {
	    process_head();
	  } else {
	    if (die_thread == true)
	      return;

	    try {
	      sync_o.wait(500);
	    } catch (InterruptedException ie) {
	    }
	  }
	} catch (Throwable t) {
  	  t.printStackTrace();
 	}
      }
    }
  }

  private static class GQEString implements QueueElementIF {
    private String ns = null;
    private long inj;

    public GQEString(String f) {
      ns = f;
      inj = System.currentTimeMillis();
    }
    public String toString() {
      return ns + " elapsed="+(System.currentTimeMillis() - inj);
    }
  }

  public static void main(String args[]) {
    FiniteQueue q = new FiniteQueue();
    ssTimer te = new ssTimer();
    ssTimer.ssTimerEvent t1, t10, t20, t30, t40, t50, t250, t500, t2500, t1500, t3500, t15000, t8000;

    System.out.println("adding 1 millisecond event");
    t1 = te.registerEvent(1, new GQEString("1"), q);
    System.out.println("adding 10 millisecond event");
    t10 = te.registerEvent(10, new GQEString("10"), q);
    System.out.println("adding 20 millisecond event");
    t20 = te.registerEvent(20, new GQEString("20"), q);
    System.out.println("adding 30 millisecond event");
    t30 = te.registerEvent(30, new GQEString("30"), q);
    System.out.println("adding 40 millisecond event");
    t40 = te.registerEvent(40, new GQEString("40"), q);
    System.out.println("adding 50 millisecond event");
    t50 = te.registerEvent(50, new GQEString("50"), q);
    System.out.println("adding 250 millisecond event");
    t250 = te.registerEvent(250, new GQEString("250"), q);
    System.out.println("adding 500 millisecond event");
    t500 = te.registerEvent(500, new GQEString("500"), q);
    System.out.println("adding 2500 millisecond event");
    t2500 = te.registerEvent(2500, new GQEString("2500"), q);
    System.out.println("adding 1500 millisecond event");
    t1500 = te.registerEvent(1500, new GQEString("1500"), q);
    System.out.println("adding 3500 millisecond event");
    t3500 = te.registerEvent(3500, new GQEString("3500"), q);
    System.out.println("adding 15000 millisecond event");
    t15000 = te.registerEvent(15000, new GQEString("15000"), q);
    System.out.println("adding 8000 millisecond event");
    t8000 = te.registerEvent(8000, new GQEString("8000"), q);

    int num_got = 0;
    while (num_got < 13) {
      QueueElementIF nextEl[] = q.dequeue_all();

      if (nextEl != null) {
	num_got += nextEl.length;
	System.out.println("got " + nextEl.length + " event" +
	  (nextEl.length > 1 ? "s" : ""));
	for (int i=0; i<nextEl.length; i++)
	  System.out.println("  " + i + ": " + nextEl[i]);
	System.out.println("total num got so far is: " + num_got);
	System.out.println("num remain is: " + te.size());
	if (num_got == 3)
	  te.cancelEvent(t2500);
      } else {
	try {
	  Thread.sleep(5);
	} catch (InterruptedException ie) {
	}
      }
    }

    te.doneWithTimer();
  }
}

