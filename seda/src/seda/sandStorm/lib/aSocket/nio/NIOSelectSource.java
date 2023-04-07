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

package seda.sandStorm.lib.aSocket.nio;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.lib.aSocket.SelectSourceIF;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

/**
 * A NIOSelectSource is an implementation of SourceIF which pulls events from
 * the operating system via the NIO Selector interface. This can be thought
 * of as a 'shim' which turns a Selector into a SourceIF. 
 *
 * <p>NIOSelectSource also "balances" the set of events returned on 
 * subsequent calls to dequeue, in order to avoid biasing the servicing of
 * underlying O/S events to a particular order. This feature can be 
 * disabled by creating a SelectSource with the boolean flag 'do_balance'.
 *
 * <p><b>Important note:</b> This class is not threadsafe with respect
 * to multiple threads calling dequeue() or blocking_dequeue() at once.
 * Clients must synchronize their access to this class. 
 *
 * @author Matt Welsh 
 */
public class NIOSelectSource implements SelectSourceIF {

  private static final boolean DEBUG = false;

  private Selector selector;
  private SelectionKey ready[];
  private int ready_offset, ready_size;

  private boolean do_balance;
  private final int BALANCER_SEQUENCE_SIZE = 10000;
  private int balancer_seq[];
  private int balancer_seq_off;
  private Object blocker;
  private String name = "(unknown)";

  // XXX MDW HACKING
  public Selector getSelector() {
    return selector;
  }

  /**
   * Create a new empty SelectSource. This SelectSource will perform
   * event balancing.
   */
  public NIOSelectSource() {
    this(true);
  }

  /**
   * Create a new empty SelectSource. 
   *
   * @param do_balance Indicates whether this SelectSource should perform
   * event balancing.
   */
  public NIOSelectSource(boolean do_balance) {
    blocker = new Object();
    try {
        selector = Selector.open();
    } catch (IOException e) {
        System.err.println("NIOSelectSource ("+name+"): error creating selector: " + e);
    }
    ready = null;
    ready_offset = ready_size = 0;
    this.do_balance = do_balance;

    if  (DEBUG) System.err.println("NIOSelectSource created, do_balance = "+do_balance);

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
   */
  public Object register(Object nio_sc_obj, int ops) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): register " +nio_sc_obj +
        " : " + this);

    if (! (nio_sc_obj instanceof SelectableChannel)) {
        System.err.println(
           "register() called with non SelectableChannel argument.  " +
           "Should not happen!!"
        );
        return null;
    }

    SelectableChannel nio_sc = (SelectableChannel)nio_sc_obj;

    synchronized (blocker) {
      SelectionKey ret;
      try {
      	ret = nio_sc.register(selector, ops);
      } catch (ClosedChannelException cce) {
      	System.err.println("Closed Channel Exception: " + cce);
     	ret = null;
      }
      if (DEBUG) System.err.println("returning " + ret);
      if (DEBUG) System.err.println("numactive = " + numActive());
      blocker.notify();
      return ret;
    }
  }


  public void register(Object sel) {
      System.err.println(
        "Single argument register() called on NIOSelectSource.  " +
        "Should not happen!!"
      );
      return;
  }
  /**
   * Deregister a SelectItem with this SelectSource.
   * Note that after calling deregister, subsequent calls to dequeue
   * may in fact return this SelectItem as a result. This is because
   * the SelectQueue internally caches results.
   */
  public void deregister(Object selkey_obj) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): deregister "+selkey_obj);

    if (! (selkey_obj instanceof SelectionKey)) {
        System.err.println(
            "deregister() called on NIOSelectSource with non SelectionKey " +
            "argument.  Should not happen!!"
        );
        return;
    }

    synchronized (blocker) {
      SelectionKey selkey = (SelectionKey)selkey_obj;
      if (DEBUG) System.err.println("NIOSelectSource ("+name+"): cancel "+selkey);
      selkey.cancel();
      /* This must be done so that calls to close() actually close. */
      try {
        if (DEBUG) System.err.println("NIOSelectSource ("+name+"): selectNow");
	selector.wakeup(); 
	selector.selectNow();
        if (DEBUG) System.err.println("NIOSelectSource ("+name+"): selectNow returned");
      } catch (IOException ioe) {
	// Ignore
      }
      blocker.notify();
    }
  }

  /**
   * Must be called if the 'events' mask of any SelectItem registered
   * with this SelectSource changes. Pushes event mask changes down to
   * the underlying event-dispatch mechanism.
   */
  public void update() {
    // selset.update();
  }

  /**
   * Must be called if the 'events' mask of this SelectItem (which
   * must be registered with this SelectSource) changes. Pushes 
   * event mask changes down to the underlying event-dispatch mechanism.
   */
  public void update(Object sel) {
      System.err.println(
        "update() called on NIOSelectSource with argument.  " +
        "should not happen!!"
      );
      return;
  }


  /**
   * Return the number of SelectItems registered with the SelectSource.
   */
  public int numRegistered() {
    Set keys = selector.keys();
    synchronized (keys) {
      return keys.size();
    }
  }
  
  /**
   * Return the number of active SelectItems registered with the SelectSource.
   * An active SelectItem is one defined as having a non-zero events
   * interest mask.
   */
  public int numActive() {
    Set keys = selector.keys();
    synchronized (blocker) {
      synchronized (keys) {
  	Iterator key_iter = keys.iterator();
   	SelectionKey sk;
    	int n_active = 0;
     	while (key_iter.hasNext()) {
  	  sk = (SelectionKey)key_iter.next();
  	  if (sk.isValid() && sk.interestOps() != 0) n_active++;
   	}
    	return n_active;
      }
    }
  }

  /**
   * Return the number of elements waiting in the queue (that is,
   * which don't require a SelectSet poll operation to retrieve).
   */
  public int size() {
    synchronized (this) {
      return (ready_size - ready_offset);
    }
  }

  /** 
   * Dequeues the next element from the SelectSource without blocking.
   * Returns null if no entries available.
   */
  public QueueElementIF dequeue() {
    if (numRegistered() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    return new NIOSelectorQueueElement(ready[ready_offset++]);
  }

  /** 
   * Dequeues all elements which are ready from the SelectSource.
   * Returns null if no entries available.
   */
  public QueueElementIF[] dequeue_all() {
    if (numRegistered() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    NIOSelectorQueueElement ret[] =
        new NIOSelectorQueueElement[ready_size-ready_offset];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new NIOSelectorQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  /** 
   * Dequeues at most <tt>num</tt> elements which are ready from the 
   * SelectSource. Returns null if no entries available.
   */
  public QueueElementIF[] dequeue(int num) {
    if (numRegistered() == 0) return null;

    if ((ready_size == 0) || (ready_offset == ready_size)) {
      doPoll(0);
    } 
    if (ready_size == 0) return null;
    int numtoret = Math.min(ready_size - ready_offset, num);

    NIOSelectorQueueElement ret[] = new NIOSelectorQueueElement[numtoret];
    for (int i = 0; i < numtoret; i++) {
      ret[i] = new NIOSelectorQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  /**
   * Dequeue the next element from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF blocking_dequeue(int timeout_millis) {

    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): blocking_dequeue called");
    synchronized (blocker) {
      if (numRegistered() == 0) {
	if (DEBUG) System.err.println("No keys in selector");

	if (timeout_millis == 0) return null;

        // Wait for something to be registered
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
    if (ready_size == 0) {
        if (DEBUG) System.err.println("still no ready");
        return null;
    }
    return new NIOSelectorQueueElement(ready[ready_offset++]);
  }

  /**
   * Dequeue a set of elements from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF[] blocking_dequeue_all(int timeout_millis) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): blocking_dequeue_all called");
    /* have to do this to retain same semantics as before
       nio expects 0 for indefinite.  there is no way to say
       don't block at all, so hopefully 1ms isn't noticable to people */

    synchronized (blocker) {
      if (numRegistered() == 0) {
	if (DEBUG) System.err.println("!!!!no keys");
	if (timeout_millis == 0) return null;
	// Wait for something to be registered
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
    if (DEBUG) System.err.println("!!!!ready_size=" + ready_size);
    if (ready_size == 0) return null;
    if (DEBUG) System.err.println("!!!!ready_size-ready_offset=" + (ready_size - ready_offset));
    NIOSelectorQueueElement ret[] =
      new NIOSelectorQueueElement[ready_size-ready_offset];
    for (int i = 0; i < ret.length; i++) {
      if (DEBUG) System.err.println("ret["+i+"] = " + ready[ready_offset]);
      ret[i] = new NIOSelectorQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  /**
   * Dequeue a set of elements from the SelectSource. Blocks up to 
   * timeout_millis milliseconds; returns null if no entries available
   * after that time. A timeout of -1 blocks forever.
   */
  public QueueElementIF[] blocking_dequeue(int timeout_millis, int num) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): blocking_dequeue called");

    synchronized (blocker) {
      if (numRegistered() == 0) {
      	if (timeout_millis == 0) return null;
       	// Wait for something to be registered
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
    NIOSelectorQueueElement ret[] = new NIOSelectorQueueElement[numtoret];
    for (int i = 0; i < numtoret; i++) {
      ret[i] = new NIOSelectorQueueElement(ready[ready_offset++]);
    }
    return ret;
  }

  // Actually performs the poll and sets ready[], ready_off, ready_size
  //
  // XXX MDW: There is a race condition here. If multiple threads
  // call doPoll (e.g., through dequeue()), then the ready set can 
  // get corrupted. The fix is to make this method synchronized, but
  // this would cause a blocking dequeue() to stall all other (possibly
  // nonblocking) dequeues until the timeout of the longest blocking
  // dequeue. I don't see an easy way around this problem since it's
  // the selector.selectedKeys() set that changes with each call to
  // selector.select() or selector.selectNow(). The answer is: This class
  // is not thread-safe!

  private void doPoll(int timeout) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): Doing poll, timeout "+timeout);

    int c = 0;
    try {
        // to correct for changed semantics in nio from nbio.
        // use selectNow to not block, and select(0) for indefinite block
        if (timeout == 0) {
            c = selector.selectNow();
        } else {
            if (timeout == -1) timeout = 0;
            c = selector.select(timeout);
        }
    } catch (IOException e) {
      // Essentially ignore the exception (since NBIO SelectSet.select()
      // doesn't throw any exceptions)
      if (DEBUG) System.err.println("NIOSelectSource ("+name+"): Error doing select: " + e);
    }
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): poll returned "+c);
    if (c == 0) {
      ready = null; ready_offset = ready_size = 0; return;
    }

    Set skeys = selector.selectedKeys();

    synchronized (skeys) {
      if (skeys.size() > 0) {

     	SelectionKey ret[] = new SelectionKey[skeys.size()];
      	Iterator key_iter = skeys.iterator();

       	int j = 0;
	while (key_iter.hasNext() && j < ret.length) {
	  ret[j] = (SelectionKey)key_iter.next();
	  key_iter.remove();
	  j++;
	}

  	if (ret.length != 0) {
  	  // XXX We can't get ret == null if doPoll() is synchronized with 
  	  // deregister() - but I'm not sure I want to do that
  	  ready_offset = 0; ready_size = ret.length;
  	  balance(ret);
  	  return;
   	}
      }
    }

    // Didn't get anything
    ready = null; ready_offset = ready_size = 0;
  }

  // Balances selarr[] by shuffling the entries - sets ready[]
  private void balance(SelectionKey selarr[]) {
    if (DEBUG) System.err.println("NIOSelectSource ("+name+"): balance called, selarr size="+selarr.length);

    for (int i = 0; i < selarr.length; i++)
        if (DEBUG) System.err.println("!!!!selar["+i+"] = " + selarr[i]);

    if ((!do_balance) || (selarr.length < 2)) {
      ready = selarr;
    } else {
      SelectionKey a;
      ready = new SelectionKey[selarr.length];

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
	    System.err.println("WARNING: NIOSelectSource.balance(): All items in selarr are null (n="+n+", c="+c+", len="+selarr.length);
	    for (int k = 0; k < ready.length; k++) {
	      System.err.println("["+k+"] ready:"+ready[k]+" selarr:"+selarr[k]);
	    }
	    throw new IllegalArgumentException("balance: All items in selarr are null! This is a bug - please contact mdw@cs.berkeley.edu");
	  }
	}
	if (DEBUG) System.err.println("NIOSelectSource ("+name+"): balance: "+n+"->"+i);
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

  void setName(String thename) {
    this.name = thename;
  }

  public String toString() {
    return "NIOSS("+name+")";
  }

  // The following is called to knock the polling thread out of a poll
  // when e.g. a StartListeningRequest is in the ReadEventHandler queue.
  public void interruptSelect() { 
    selector.wakeup();
  }

}

