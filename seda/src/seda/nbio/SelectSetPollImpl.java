/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
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

package seda.nbio;

import java.util.Vector;

/**
 * SelectSetPollImpl is an implementation of SelectSet which
 * uses the UNIX poll(2) system call.
 *
 * @see SelectSet
 */
class SelectSetPollImpl extends SelectSetImpl {

  private static final boolean PROFILE = false;

  private Vector vec;
  private SelectItem itemarr[];
  private boolean needUpdate = false;
  private int cachedActiveCount = -1;
  private static int counter = 0;
  private static Object lock = new Object();
  private int my_id;

  private native int doSelect(int timeout, int my_id);
  private native void interruptSelect(int my_id);

  // Push the internal vector to itemarr.
  private void itemarrupdate() {
    if (vec.size() == 0) {
      itemarr = null;
    } else {
      itemarr = new SelectItem[vec.size()];
      vec.copyInto(itemarr);
    }
  }

  public void interruptSelect() {
    interruptSelect(my_id);
  }

  /**
   * Returns true if poll(2) is supported on this platform.
   */
  static boolean isSupported() {
    return true;
  }

  /**
   * Create a SelectSetPollImpl with no SelectItems.
   */
  SelectSetPollImpl() {
    synchronized(lock) {
	my_id = counter;
	counter++;
    }
    vec = new Vector(1);
  }

  /**
   * Add a SelectItem to this SelectSetPollImpl.
   */
  synchronized void add(SelectItem sel) {
    vec.addElement(sel);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Add all of the SelectItems in the given array to the SelectSetPollImpl.
   */
  synchronized void add(SelectItem selarr[]) {
    for (int i = 0; i < selarr.length; i++) {
      vec.addElement(selarr[i]);
    }
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove a SelectItem from the SelectSetPollImpl.
   */
  synchronized void remove(SelectItem sel) {
    vec.removeElement(sel);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove all of the SelectItems in the given array from the 
   * SelectSetPollImpl.
   */
  synchronized void remove(SelectItem selarr[]) {
    for (int i = 0; i < selarr.length; i++) {
      vec.removeElement(selarr[i]);
    }
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove the SelectItem at the given index from the SelectSetPollImpl.
   */
  synchronized void remove(int index) {
    vec.removeElementAt(index);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Update any changed event masks in the SelectSet. Does nothing
   * in this implementation.
   */
  void update() {
    cachedActiveCount = -1;
  }

  /**
   * Update any changed event masks in this SelectItem. Does nothing
   * in this implementation.
   */
  void update(SelectItem sel) {
    cachedActiveCount = -1;
  }

  /**
   * Return the number of SelectItems in this SelectSetPollImpl.
   */
  synchronized int size() {
    return vec.size();
  }

  /**
   * Return the number of active SelectItems in this SelectSetPollImpl.
   */
  synchronized int numActive() {
    if (cachedActiveCount != -1) return cachedActiveCount;
    if (needUpdate) {
      itemarrupdate();
      needUpdate = false;
    }
    int count = 0;
    if (itemarr != null) {
      for (int i = 0; i < itemarr.length; i++) {
	if (itemarr[i].events != 0) count++;
      }
    }
    cachedActiveCount = count;
    return count;
  }

  /**
   * Return the SelectItem at the given index.
   */
  synchronized SelectItem elementAt(int index) {
    return (SelectItem)vec.elementAt(index);
  }

  /**
   * Wait for events to occur on the SelectItems in this SelectSetPollImpl.
   * Upon return, the 'revents' field of each SelectItem will be
   * set to the mask of events that occurred. Note that this method
   * <b>does not</b> set revents to 0 when called; after processing an
   * event, it is the application's responsibility to clear the revents
   * field. This is intentional: if the application wishes to delay
   * the processing of an event, it can leave the revents field as-is so
   * that subsequent calls to select will continue to indicate that the
   * event is pending.
   *
   * <p>
   * <b>IMPORTANT NOTE:</b> If timeout is non-zero, this call will 
   * <b>block</b> the thread which invokes it. If you are using
   * Green Threads, this will block the entire JVM. Unless you have
   * a single-threaded application, you should only use 
   * SelectSet.select() with native threads.
   * 
   * @param timeout The maximum number of milliseconds to block waiting
   * for an event to occur. A timeout of 0 means than select should not block;
   * a timeout of -1 means that select should block indefinitely.
   *
   * @return The number of events received, or 0 if no events occurred.
   */
  int select(int timeout) {
    if (PROFILE) SelectSet.tracer.trace("SSPI "+hashCode()+" select() called");

    synchronized (this) {
      if (needUpdate) {
        itemarrupdate();
	needUpdate = false;
      }
    }
    return doSelect(timeout, my_id);
  }

  /**
   * Returns an array of SelectItems for which events matching the given
   * event mask have occurred (that is, that the revents field matches
   * the given mask).
   *
   * This is a convenience method and is not meant to be optimized; since
   * it scans the SelectItem array and creates a new reference array, it
   * imposes higher overhead than the application scanning the SelectItem
   * array directly, using the size() and elementAt() methods.
   */
  synchronized SelectItem[] getEvents(short mask) {
    int count = 0;
    if (itemarr == null) return null;
    for (int i = 0; i < itemarr.length; i++) {
      if (itemarr[i] == null) {
        // XXX MDW Trying to nail a Heisenbug here
        System.err.println("SelectSetPollImpl warning: itemarr["+i+"] is null! Please report to mdw@cs.berkeley.edu.");
	continue;
      }
      if ((itemarr[i].revents & mask) != 0) count++;
    }
    if (count == 0) return null;
    SelectItem retarr[] = new SelectItem[count];
    count = 0;
    for (int i = 0; i < itemarr.length; i++) {
      if (itemarr[i] == null) {
        // XXX MDW Trying to nail a Heisenbug here
	continue;
      }
      if ((itemarr[i].revents & mask) != 0) {
        retarr[count] = itemarr[i];
	count++;
      }
    }
    return retarr;
  }

  /**
   * Returns an array of SelectItems for which some events have occurred
   * (that is, that the revents field is nonzero).
   *
   * This is a convenience method and is not meant to be optimized; since
   * it scans the SelectItem array and creates a new reference array, it
   * imposes higher overhead than the application scanning the SelectItem
   * array directly, using the size() and elementAt() methods.
   */
  synchronized SelectItem[] getEvents() {
    return getEvents((short)0xffff);
  }

  public String toString() {
    String s = "SelectSetPollImpl:\n";
    for (int i = 0; i < size(); i++) {
      s = s + "\t"+elementAt(i).toString()+"\n";
    }
    return s;
  }

}
