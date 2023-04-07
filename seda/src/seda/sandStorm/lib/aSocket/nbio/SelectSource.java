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

package seda.sandStorm.lib.aSocket.nbio;

import seda.nbio.SelectItem;
import seda.nbio.SelectSet;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.lib.aSocket.SelectSourceIF;
import seda.util.Tracer;

import java.util.Random;

/**
 * A SelectSource is an implementation of SourceIF which pulls events from
 * the operating system via the NBIO SelectSet interface. This can be thought
 * of as a 'shim' which turns a SelectSet into a SourceIF. 
 *
 * <p>SelectSource can also "balances" the set of events returned on 
 * subsequent calls to dequeue, in order to avoid biasing the servicing of
 * underlying O/S events to a particular order. This feature can be 
 * disabled by creating a SelectSource with the boolean flag 'do_balance'.
 *
 * @author Matt Welsh 
 */
public class SelectSource implements SelectSourceIF {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  private SelectSet selset;
  private SelectItem ready[];
  private int ready_offset, ready_size;

  private boolean do_balance;
  private final int BALANCER_SEQUENCE_SIZE = 10000;
  private int balancer_seq[];
  private int balancer_seq_off;
  private Object blocker;
  private Tracer tracer;

  // XXX MDW HACKING
  public Object getSelectSet() {
    return selset;
  }

  /**
   * Create a new empty SelectSource. This SelectSource will perform
   * event balancing.
   */
  public SelectSource() {
    this(true);
  }

  /**
   * Create a new empty SelectSource. 
   *
   * @param do_balance Indicates whether this SelectSource should perform
   * event balancing.
   */
  public SelectSource(boolean do_balance) {
    blocker = new Object();
    selset = new SelectSet();
    ready = null;
    ready_offset = ready_size = 0;
    this.do_balance = do_balance;

    if (DEBUG) System.err.println("SelectSource created, do_balance = "+do_balance);
    if (PROFILE) tracer = new Tracer("NBIO SelectSource <"+selset.hashCode()+">");

    if (do_balance) initBalancer();
  }

  /**
   * Register a SelectItem with the SelectSource. The SelectItem should 
   * generally correspond to a Selectable along with a set of event flags
   * that we wish this SelectSource to test for. 
   *
   * <p>The user is allowed to modify the event flags in the SelectItem 
   * directly (say, to cause the SelectSource ignore a given SelectItem for 
   * the purposes of future calls to one of the dequeue methods). However,
   * modifying the event flags may not be synchronous with calls to dequeue -
   * generally because SelectSource maintains a cache of recently-received
   * events.
   *
   * @see seda.nbio.Selectable
   */
  public void register(Object selobj) {
    if (DEBUG) System.err.println("SelectSource: register "+selobj);
    if (! (selobj instanceof SelectItem)) {
        System.err.println(
            "register() called with non SelectItem argument.  " +
            "Should not happen!!"
        );
        return;
    }
    selset.interruptSelect(); // blow it out of any select, unlock blocker.
    SelectItem sel = (SelectItem)selobj;
    selset.add(sel);
    synchronized (blocker) {
      blocker.notify();
    }
  }

  public Object register(Object sc_obj, int ops) {
      System.err.println(
        "Double argument register() called on nbio SelectSource.  " +
        "Should not happen!!"
      );
      return null;
  }

  /**
   * Deregister a SelectItem with this SelectSource.
   * Note that after calling deregister, subsequent calls to dequeue
   * may in fact return this SelectItem as a result. This is because
   * the SelectQueue internally caches results.
   */
  public void deregister(Object selobj) {
    if (DEBUG) System.err.println("SelectSource: deregister "+selobj);
    if (! (selobj instanceof SelectItem)) {
        System.err.println(
            "deregister() called with non SelectItem argument.  " +
            "Should not happen!!"
        );
        return;
    }
    selset.interruptSelect();	// blow it out of any selects, unlock blocker
    SelectItem sel = (SelectItem)selobj;
    selset.remove(sel);
    synchronized (blocker) {
      blocker.notify();
    }
  }

  /**
   * Must be called if the 'events' mask of any SelectItem registered
   * with this SelectSource changes. Pushes event mask changes down to
   * the underlying event-dispatch mechanism.
   */
  public void update() {
    selset.update();
  }

  /**
   * Must be called if the 'events' mask of this SelectItem (which
   * must be registered with this SelectSource) changes. Pushes 
   * event mask changes down to the underlying event-dispatch mechanism.
   */
  public void update(Object selobj) {
    if (! (selobj instanceof SelectItem)) {
        System.err.println(
            "update() called with non SelectItem argument.  " +
            "Should not happen!!"
        );
        return;
    }
    selset.update((SelectItem)selobj);
  }

  /**
   * Return the number of SelectItems registered with the SelectSource.
   */
  public int numRegistered() {
    return selset.size();
  }
  
  /**
   * Return the number of active SelectItems registered with the SelectSource.
   * An active SelectItem is one defined as having a non-zero events
   * interest mask.
   */
  public int numActive() {
    return selset.numActive();
  }

  /**
   * Return the number of elements waiting in the queue (that is,
   * which don't require a SelectSet poll operation to retrieve).
   */
  public int size() {
    return (ready_size - ready_offset);
  }

  /** 
   * Dequeues the next element from the SelectSource without blocking.
   * Returns null if no entries available.
   */
  public QueueElementIF dequeue() {
    if (selset.size() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    return new SelectQueueElement(ready[ready_offset++]);
  }

  /** 
   * Dequeues all elements which are ready from the SelectSource.
   * Returns null if no entries available.
   */
  public QueueElementIF[] dequeue_all() {
    if (selset.size() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    SelectQueueElement ret[] = new SelectQueueElement[ready_size-ready_offset];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new SelectQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  /** 
   * Dequeues at most <tt>num</tt> elements which are ready from the 
   * SelectSource. Returns null if no entries available.
   */
  public QueueElementIF[] dequeue(int num) {
    if (selset.size() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    int numtoret = Math.min(ready_size - ready_offset, num);

    SelectQueueElement ret[] = new SelectQueueElement[numtoret];
    for (int i = 0; i < numtoret; i++) {
      ret[i] = new SelectQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  /**
   * Dequeue the next element from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF blocking_dequeue(int timeout_millis) {

    if (selset.size() == 0) {
      if (timeout_millis == 0) return null;
      // Wait for something to be registered
      synchronized (blocker) {
	if (timeout_millis == -1) {
	  try {
	    blocker.wait();
	  } catch (InterruptedException ie) {
	  }
	} else {
	  try {
	    blocker.wait(timeout_millis);
	  } catch (InterruptedException ie) {
	  }
	}
      }
    }

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(timeout_millis);
    } 
    if (ready_size == 0) return null;
    return new SelectQueueElement(ready[ready_offset++]);
  }

  /**
   * Dequeue a set of elements from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF[] blocking_dequeue_all(int timeout_millis) {
    if (PROFILE) tracer.trace("blocking_dequeue_all called");

    if (selset.size() == 0) {
      if (timeout_millis == 0) return null;
      // Wait for something to be registered
      if (PROFILE) tracer.trace(" bdqa: blocking");
      synchronized (blocker) {
	if (timeout_millis == -1) {
	  try {
	    blocker.wait();
	  } catch (InterruptedException ie) {
	  }
	} else {
	  try {
	    blocker.wait(timeout_millis);
	  } catch (InterruptedException ie) {
	  }
	}
      }
      if (PROFILE) tracer.trace(" bdqa: done blocking");
    }

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(timeout_millis);
    } 
    if (ready_size == 0) return null;

    if (PROFILE) tracer.trace(" bdqa: fill in ret[]");
    SelectQueueElement ret[] = new SelectQueueElement[ready_size-ready_offset];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new SelectQueueElement(ready[ready_offset++]);
    }
    if (PROFILE) tracer.trace(" bdqa: done fill in ret[]");
    return ret;
  }

  /**
   * Dequeue a set of elements from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF[] blocking_dequeue(int timeout_millis, int num) {

    if (selset.size() == 0) {
      if (timeout_millis == 0) return null;
      // Wait for something to be registered
      synchronized (blocker) {
	if (timeout_millis == -1) {
	  try {
	    blocker.wait();
	  } catch (InterruptedException ie) {
	  }
	} else {
	  try {
	    blocker.wait(timeout_millis);
	  } catch (InterruptedException ie) {
	  }
	}
      }
    }

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(timeout_millis);
    } 
    if (ready_size == 0) return null;
    int numtoret = Math.min(ready_size - ready_offset, num);
    SelectQueueElement ret[] = new SelectQueueElement[numtoret];
    for (int i = 0; i < numtoret; i++) {
      ret[i] = new SelectQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  // Actually performs the poll and sets ready[], ready_off, ready_size
  private void doPoll(int timeout) {
    if (DEBUG) System.err.println("SelectSource: Doing poll, timeout "+timeout);
    if (PROFILE) tracer.trace("doPoll called");
    int c = selset.select(timeout);
    if (DEBUG) System.err.println("SelectSource: poll returned "+c);
    if (c > 0) {
      if (PROFILE) tracer.trace("select returned nonzero");
      SelectItem ret[] = selset.getEvents();
      if (PROFILE) tracer.trace("getEvents() return");
      if (ret != null) {
	// XXX We can't get ret == null if doPoll() is synchronized with 
	// deregister() - but I'm not sure I want to do that
	ready_offset = 0; ready_size = ret.length;
        if (PROFILE) tracer.trace("calling balance");
	balance(ret);
        if (PROFILE) tracer.trace("balance return");
	return;
      }
    }
    if (PROFILE) tracer.trace("select returned zero");
    // Didn't get anything
    ready = null; ready_offset = ready_size = 0;
  }

  // Balances selarr[] by shuffling the entries - sets ready[]
  private void balance(SelectItem selarr[]) {
    if (DEBUG) System.err.println("SelectSource: balance called, selarr size="+selarr.length);
    if ((!do_balance) || (selarr.length < 2)) {
      ready = selarr;
    } else {
      SelectItem a;
      ready = new SelectItem[selarr.length];

      for (int i = 0; i < ready.length; i++) {
      	if (balancer_seq_off == BALANCER_SEQUENCE_SIZE) {
     	  balancer_seq_off = 0;
      	}
       	int n = balancer_seq[balancer_seq_off++] % selarr.length;
	int c = 0;
	while (selarr[n] == null) {
	  n++; c++;
	  if (n == selarr.length) n = 0;
	  if (c == selarr.length) {
	    System.err.println("WARNING: SelectSource.balance(): All items in selarr are null (n="+n+", c="+c+", len="+selarr.length);
	    for (int k = 0; k < ready.length; k++) {
	      System.err.println("["+k+"] ready:"+ready[k]+" selarr:"+selarr[k]);
	    }
	    throw new IllegalArgumentException("balance: All items in selarr are null! This is a bug - please contact mdw@cs.berkeley.edu");
	  }
	}
	if (DEBUG) System.err.println("SelectSource: balance: "+n+"->"+i);
	a = selarr[n]; selarr[n] = null; ready[i] = a;
      }
    }
  }

  // Initialize the balancer
  private void initBalancer() {
    balancer_seq = new int[BALANCER_SEQUENCE_SIZE];
    Random r = new Random(); // XXX Need better seed?
    for (int i = 0; i < BALANCER_SEQUENCE_SIZE; i++) {
      balancer_seq[i] = Math.abs(r.nextInt());
    }
    balancer_seq_off = 0;
  }

    public void interruptSelect() {
	if(DEBUG)System.err.println("SelectSource.interruptSelect() called.");
	selset.interruptSelect();
    }

}

