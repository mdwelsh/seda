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

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.ThreadManagerIF;

import java.util.Hashtable;

/**
 * The FiniteQueue class is a simple implementation of the QueueIF
 * interface, using a linked list.
 *
 * @author   Matt Welsh
 * @see      seda.sandStorm.api.QueueIF
 */

public class FiniteQueue implements QueueIF, ProfilableIF {

  private static final boolean DEBUG = false;

  private ssLinkedList qlist;
  private int queueSize;
  private Object blocker;
  private Hashtable provisionalTbl;
  private EnqueuePredicateIF pred;
  private String name;
  private boolean closed;
  private ThreadManagerIF threadmgr;

  /** 
   * Create a FiniteQueue with the given enqueue predicate, name, and
   * thread manager.
   */
  public FiniteQueue(EnqueuePredicateIF pred, String name, ThreadManagerIF threadmgr) {
    this.name = name;
    this.pred = pred;
    qlist = new ssLinkedList();
    queueSize = 0;
    blocker = new Object();
    provisionalTbl = new Hashtable(1);
    this.threadmgr = threadmgr;
  }

  /** 
   * Create a FiniteQueue with the given enqueue predicate.
   */
  public FiniteQueue(EnqueuePredicateIF pred) {
    this(pred, null, null);
  }

  /** 
   * Create a FiniteQueue with the given enqueue predicate and thread manager.
   */
  public FiniteQueue(EnqueuePredicateIF pred, ThreadManagerIF threadmgr) {
    this(pred, null, threadmgr);
  }

  /**
   * Create a FiniteQueue with no enqueue predicate.
   */
  public FiniteQueue() {
    this((EnqueuePredicateIF)null);
  }
  
  /** 
   * Create a FiniteQueue with no enqueue predicate, but inlude a thread 
   * manager.
   */
  public FiniteQueue(ThreadManagerIF threadmgr) {
    this((EnqueuePredicateIF)null, null, threadmgr);
  }
  /**
   * Create a FiniteQueue with no enqueue or thread manager, but include
   * the given name. Used for debugging.
   */
  public FiniteQueue(String name) {
    this((EnqueuePredicateIF)null, name, null);
  }

  /**
   * Create a FiniteQueue with no enqueue, but include a thread manager and
   * the given name. Used for debugging.
   */
  public FiniteQueue(String name, ThreadManagerIF threadmgr) {
    this((EnqueuePredicateIF)null, name, threadmgr);
  }

  /** 
   * Return the size of the queue.
   */
  public int size() {
    synchronized(blocker) {
      synchronized(qlist) {
	return queueSize;
      }
    }
  }

  public void enqueue(QueueElementIF enqueueMe) throws SinkException {

    if (DEBUG) System.err.println("**** ENQUEUE ("+name+") **** Entered");

    if( closed ) {
      throw new SinkClosedException( "This queue is no longer accepting " + 
				     " events." );
    }

    synchronized(blocker) {

      synchronized(qlist) {
	if (DEBUG) System.err.println("**** ENQUEUE ("+name+") **** Checking pred");
	if ((pred != null) && (!pred.accept(enqueueMe))) 
	  throw new SinkFullException("FiniteQueue is full!");
	queueSize++;
	if (DEBUG) System.err.println("**** ENQUEUE ("+name+") **** Add to tail");
	qlist.add_to_tail(enqueueMe);  // wake up one blocker
      }
      if (DEBUG) System.err.println("**** ENQUEUE ("+name+") **** Doing notify");
      blocker.notifyAll();
    }
    if(threadmgr!=null)
      threadmgr.wake();
    if (DEBUG) System.err.println("**** ENQUEUE ("+name+") **** Exiting");
  }

  public boolean enqueue_lossy(QueueElementIF enqueueMe) {
    try {
      this.enqueue(enqueueMe);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  public void enqueue_many(QueueElementIF[] enqueueMe) throws SinkException {

    if( closed ) {
      throw new SinkClosedException( "This queue is no longer accepting " + 
				     " events." );
    }

    synchronized(blocker) {
      int qlen = enqueueMe.length;

      synchronized(qlist) {
	if (pred != null) {
	  int i = 0;
	  while ((i < qlen) && (pred.accept(enqueueMe[i]))) i++;
	  if (i != qlen) throw new SinkFullException("FiniteQueue is full!");
	}

	queueSize += qlen;
	for (int i=0; i<qlen; i++) {
	  qlist.add_to_tail(enqueueMe[i]);
	}
      }
      blocker.notifyAll();  // wake up all sleepers
    }
    if(threadmgr!=null)
      threadmgr.wake();
  }

  public QueueElementIF dequeue() {
    
    QueueElementIF el = null;
    synchronized(blocker) {
      synchronized(qlist) {
	if (qlist.size() == 0)
	  return null;

	el = (QueueElementIF) qlist.remove_head();
	queueSize--;
	return el;
      }
    }
  }

  public QueueElementIF[] dequeue_all() {
    
    synchronized(blocker) {
      synchronized(qlist) {
	int qs = qlist.size();
	if (qs == 0) return null;

	QueueElementIF[] retIF = new QueueElementIF[qs];
	for (int i=0; i<qs; i++)
	  retIF[i] = (QueueElementIF) qlist.remove_head();
	queueSize -= qs;
	return retIF;
      }
    }
  }

  public QueueElementIF[] dequeue(int num) {
    
    synchronized(blocker) {
      synchronized(qlist) {
	int qs = Math.min(qlist.size(), num);

	if (qs == 0)
	  return null;

	QueueElementIF[] retIF = new QueueElementIF[qs];
	for (int i=0; i<qs; i++)
	  retIF[i] = (QueueElementIF) qlist.remove_head();
	queueSize -= qs;
	return retIF;
      }
    }
  }

  public QueueElementIF[] dequeue(int num, boolean mustReturnNum) {
    
    synchronized(blocker) {
      synchronized(qlist) {
	int qs;

	if (!mustReturnNum) {
	  qs = Math.min(qlist.size(), num);
	} else {
	  if (qlist.size() < num) return null;
	  qs = num;
	}

	if (qs == 0)
	  return null;

	QueueElementIF[] retIF = new QueueElementIF[qs];
	for (int i=0; i<qs; i++)
	  retIF[i] = (QueueElementIF) qlist.remove_head();
	queueSize -= qs;
	return retIF;
      }
    }
  }

  public QueueElementIF[] blocking_dequeue_all(int timeout_millis) {
    QueueElementIF[] rets = null;
    long    goal_time;
    int     num_spins = 0;

    if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** Entered");

    goal_time = System.currentTimeMillis() + timeout_millis;
    while (true) {
      synchronized(blocker) {

	if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** Doing D_A");
	rets = this.dequeue_all();
	if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** RETS IS "+rets);
	if ((rets != null) || (timeout_millis == 0)) {
	  if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** RETURNING (1)");
	  return rets;
	}

	if (timeout_millis == -1) {
	  try {
	    blocker.wait();
	  } catch (InterruptedException ie) {
	  }
	} else {
	  try {
	    if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** WAITING ON BLOCKER");
	    blocker.wait(timeout_millis);
	  } catch (InterruptedException ie) {
	  }
	}
	
	if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** Doing D_A (2)");
	rets = this.dequeue_all();
	if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** RETS(2) IS "+rets);
	if (rets != null) {
	  if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** RETURNING(2)");
	  return rets;
	}
	
	if (timeout_millis != -1) {
	  if (System.currentTimeMillis() >= goal_time) {
	    if (DEBUG) System.err.println("**** B_DEQUEUE_A ("+name+") **** RETURNING(3)");
	    return null;
	  }
	}
      }
    }
  }

  public QueueElementIF[] blocking_dequeue(int timeout_millis, int num, boolean mustReturnNum) {

    QueueElementIF[] rets = null;
    long    goal_time;
    int     num_spins = 0;

    goal_time = System.currentTimeMillis() + timeout_millis;
    while (true) {
      synchronized(blocker) {

	rets = this.dequeue(num, mustReturnNum);
	if ((rets != null) || (timeout_millis == 0)) {
	  return rets;
	}

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
	
	rets = this.dequeue(num, mustReturnNum);
	if (rets != null) {
	  return rets;
	}
	
	if (timeout_millis != -1) {
	  if (System.currentTimeMillis() >= goal_time) {
	    // Timeout - take whatever we can get
	    return this.dequeue(num);
	  }
	}
      }
    }
  }

  public QueueElementIF[] blocking_dequeue(int timeout_millis, int num) {
    return blocking_dequeue(timeout_millis, num, false);
  }


  public QueueElementIF blocking_dequeue(int timeout_millis) {
    QueueElementIF rets = null;
    long    goal_time;
    int     num_spins = 0;

    goal_time = System.currentTimeMillis() + timeout_millis;
    while (true) {
      synchronized(blocker) {

	rets = this.dequeue();
	if ((rets != null) || (timeout_millis == 0)) {
	  return rets;
	}

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
	
	rets = this.dequeue();
	if (rets != null) {
	  return rets;
	}
	
	if (timeout_millis != -1) {
	  if (System.currentTimeMillis() >= goal_time)
	    return null;
	}
      }
    }
  }

  /** 
   * Return the profile size of the queue.
   */
  public int profileSize() {
    return size();
  }

  /**
   * Provisionally enqueue the given elements.
   */
  public Object enqueue_prepare(QueueElementIF enqueueMe[]) throws SinkException {

    if( closed ) {
      throw new SinkClosedException( "This queue is no longer accepting " + 
				     " events." );
    }

    int qlen = enqueueMe.length;
    synchronized(blocker) {
      synchronized(qlist) {
	if (pred != null) {
	  int i = 0;
	  while ((i < qlen) && (pred.accept(enqueueMe[i]))) i++;
	  if (i != qlen) throw new SinkFullException("FiniteQueue is full!");
	}
	queueSize += qlen;
	Object key = new Object();
	provisionalTbl.put(key, enqueueMe);
	return key;
      }
    }
  }

  /** 
   * Commit a provisional enqueue.
   */
  public void enqueue_commit(Object key) {
    synchronized(blocker) {
      synchronized(qlist) {
	QueueElementIF elements[] = (QueueElementIF[])provisionalTbl.remove(key);
	if (elements == null) throw new IllegalArgumentException("Unknown enqueue key "+key);
	for (int i=0; i<elements.length; i++) {
	  qlist.add_to_tail(elements[i]);
	}
      }
      blocker.notifyAll();
    }
    if(threadmgr!=null)
      threadmgr.wake();
  }

  /** 
   * Abort a provisional enqueue.
   */
  public void enqueue_abort(Object key) {
    synchronized(blocker) {
      synchronized(qlist) {
	QueueElementIF elements[] = (QueueElementIF[])provisionalTbl.remove(key);
	if (elements == null) throw new IllegalArgumentException("Unknown enqueue key "+key);
	queueSize -= elements.length;
      }
    }
  }

  /**
   * Set the enqueue predicate for this sink. 
   */
  public void setEnqueuePredicate(EnqueuePredicateIF pred) {
    this.pred = pred;
  }

  /**
   * Return the enqueue predicate for this sink.
   */
  public EnqueuePredicateIF getEnqueuePredicate() {
    return pred;
  }

  /**
   * Indicate that this queue will not be used anymore.   Causes an
   * exception to be thrown every time enqueue is called.
   */
  public void close() {
    closed = true;
  }

  public String toString() {
    return "FiniteQueue <"+name+">";
  }

}
