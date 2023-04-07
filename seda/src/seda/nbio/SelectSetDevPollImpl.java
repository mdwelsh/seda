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
 * SelectSetDevPollImpl is an implementation of SelectSet which
 * uses the UNIX /dev/poll mechanism.
 *
 * @see SelectSet
 */
class SelectSetDevPollImpl extends SelectSetImpl {

  /**
   * The maximum number of events to return on each call to doSelect().
   */
  private static final int MAX_EVENTS_PER_SELECT = 32768;

  // The initial size of the internal SelectItem vector.
  private static final int TO_ALLOCATE = 256;

  private long native_state;      // Internal pointer to native state
  private Vector vec;             // Vector of registered SelectItems
  private boolean needUpdate;     // Does itemarr need to be updated
  private SelectItem itemarr[];   // Array of registered SelectItems
  private SelectItem retevents[]; // List of returned events
  private int retevents_length;   // Number of valid entries in retevents
  private int cachedActiveCount = -1;  // Cached count of active selitems

  // Return true if supported
  private static native boolean supported();
  // Initialize native code
  private native void init(int max_retevents);
  // Register a SelectItem with native code
  private native void register(SelectItem sel);
  // Deregister a SelectItem with native code
  private native void deregister(SelectItem sel);
  // Actually do select; return number of events 
  // Places returned events in retevents array
  private native int doSelect(int timeout, int num_fds_to_poll);
  // Does a signal to knock pollers out of wait.
  public native void interruptSelect();

  // Copy vec into itemarr
  private void updateitemarr() {
    if (vec.size() == 0) {
      itemarr = null;
    } else {
      itemarr = new SelectItem[vec.size()];
      vec.copyInto(itemarr);
    }
  }

  // Returns true if fd already in vec
  private boolean checkFD(NBIOFileDescriptor fd) {
    for (int i = 0; i < vec.size(); i++) {
      SelectItem sel = (SelectItem)vec.elementAt(i);
      if (fd.equals(sel.getFD())) return true;
    }
    return false;
  }

  /**
   * Returns true if /dev/poll is supported on this platform.
   */
  static boolean isSupported() {
    return supported();
  }

  /**
   * Create a SelectSetDevPollImpl with no SelectItems.
   */
  SelectSetDevPollImpl() {
    vec = new Vector(TO_ALLOCATE, TO_ALLOCATE);
    retevents = new SelectItem[MAX_EVENTS_PER_SELECT];
    retevents_length = 0;
    init(MAX_EVENTS_PER_SELECT);
  }

  /**
   * Add a SelectItem to this SelectSetDevPollImpl.
   */
  synchronized void add(SelectItem sel) {
    NBIOFileDescriptor fd = sel.getFD();
    if (checkFD(fd)) throw new IllegalArgumentException("Cannot register SelectItem with same NBIOFileDescriptor twice");
    vec.addElement(sel);
    register(sel);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Add all of the SelectItems in the given array to the SelectSetDevPollImpl.
   */
  synchronized void add(SelectItem selarr[]) {
    for (int i = 0; i < selarr.length; i++) {
      SelectItem sel = selarr[i];
      NBIOFileDescriptor fd = sel.getFD();
      if (checkFD(fd)) throw new IllegalArgumentException("Cannot register SelectItem with same NBIOFileDescriptor twice");
      vec.addElement(sel);
      register(sel);
    }
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove a SelectItem from the SelectSetDevPollImpl.
   */
  synchronized void remove(SelectItem sel) {
    vec.removeElement(sel);
    deregister(sel);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove all of the SelectItems in the given array from the 
   * SelectSetDevPollImpl.
   */
  synchronized void remove(SelectItem selarr[]) {
    for (int i = 0; i < selarr.length; i++) {
      vec.removeElement(selarr[i]);
      deregister(selarr[i]);
    }
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Remove the SelectItem at the given index from the SelectSetDevPollImpl.
   */
  synchronized void remove(int index) {
    SelectItem sel = (SelectItem)vec.elementAt(index);
    vec.removeElementAt(index);
    deregister(sel);
    needUpdate = true;
    cachedActiveCount = -1;
  }

  /**
   * Push updated event masks for all SelectItems in this SelectSet to 
   * the /dev/poll device.
   */
  synchronized void update() {
    for (int i = 0; i < vec.size(); i++) {
      SelectItem sel = (SelectItem)vec.elementAt(i);
      if (sel.events == 0) deregister(sel);
      else register(sel);
    }
    cachedActiveCount = -1;
  }

  /**
   * Push updated event masks for all SelectItems in this SelectSet to 
   * native code.
   */
  synchronized void update(SelectItem sel) {
    if (sel.events == 0) deregister(sel);
    else register(sel);
    cachedActiveCount = -1;
  }

  /**
   * Return the number of SelectItems in this SelectSetDevPollImpl.
   */
  synchronized int size() {
    return vec.size();
  }

  /**
   * Return the number of active SelectItems in this SelectSetDevPollImpl.
   */
  synchronized int numActive() {
    if (cachedActiveCount != -1) return cachedActiveCount;
    if (needUpdate) {
      updateitemarr();
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
   * Wait for events to occur on the SelectItems in this SelectSetDevPollImpl.
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
    synchronized (this) {
      if (needUpdate) {
        updateitemarr();
	needUpdate = false;
      }
    }
    retevents_length = doSelect(timeout, itemarr.length);
    return retevents_length;
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
    if (retevents_length == 0) return null;

    int count = 0;
    for (int i = 0; i < retevents_length; i++) {
      if ((retevents[i].events & mask) != 0) count++;
    }
    SelectItem retarr[] = new SelectItem[count];
    count = 0;
    for (int i = 0; i < retevents_length; i++) {
      if ((retevents[i].events & mask) != 0) retarr[count++] = retevents[i];
    }
    return retevents;
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
    if (retevents_length == 0) return null;
    SelectItem retarr[] = new SelectItem[retevents_length];
    System.arraycopy(retevents, 0, retarr, 0, retevents_length);
    return retarr;
  }

  public String toString() {
    String s = "SelectSetDevPollImpl:\n";
    for (int i = 0; i < size(); i++) {
      s = s + "\t"+elementAt(i).toString()+"\n";
    }
    return s;
  }

}
