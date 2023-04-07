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


/**
 * A SelectSet represents a set of SelectItems which you wish to
 * poll or wait for events to occur on. The interface is very much
 * like the poll(2) system call in SVR4.
 *
 * To poll for events across many sockets or file descriptors, create
 * a SelectSet and initialize it with one or more SelectItems. The
 * 'events' field of each SelectItem should be set to the mask of event
 * types you wish to receive. The event types are specified by the constants
 * in the Selectable class.
 * <p>
 * Calling the <tt>select</tt> method (with an optional timeout) checks
 * each of the file descriptors in the SelectSet for events, and sets the
 * 'revents' field of each SelectItem accordingly. The <tt>getEvents</tt>
 * method is provided for convenience; it returns an array of SelectItems
 * for which some event occurred.
 *
 * <p>
 * Multiple implementations of SelectSet may be available on a given
 * system. The particular implementation used is determined on the
 * features of the underlying OS, but the default choice can be overridden
 * by setting the <tt>nbio.SelectSetImpl</tt> runtime property. See
 * the subclasses of SelectSetImpl for details.
 *
 * @author Matt Welsh (mdw@cs.berkeley.edu)
 * @see Selectable
 * @see SelectSetImpl, SelectSetPollImpl, SelectSetDevPollImpl
 */
public class SelectSet {

  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;

  private SelectSetImpl impl;
  private static final int POLL_IMPL = 0;
  private static final int DEVPOLL_IMPL = 1;
  private static int IMPL_TO_USE;
  static seda.util.Tracer tracer;

  static {
    NonblockingSocket.loadNativeLibrary();

    String implProp = System.getProperty("nbio.SelectSetImpl");
    if (implProp != null) {
      if (implProp.equals("devpoll") && SelectSetDevPollImpl.isSupported()) { 
       	System.err.println("SelectSet: Using /dev/poll");
	IMPL_TO_USE = DEVPOLL_IMPL;
      } else if (implProp.equals("poll") && SelectSetPollImpl.isSupported()) {
	System.err.println("SelectSet: Using poll(2)");
	IMPL_TO_USE = POLL_IMPL;
      } else {
	throw new UnsatisfiedLinkError("No SelectSetImpl supported on this platform!");
      }
    } else {
      if (SelectSetDevPollImpl.isSupported()) {
       	System.err.println("SelectSet: Using /dev/poll");
	IMPL_TO_USE = DEVPOLL_IMPL;
      } else if (SelectSetPollImpl.isSupported()) {
	System.err.println("SelectSet: Using poll(2)");
	IMPL_TO_USE = POLL_IMPL;
      } else {
	throw new UnsatisfiedLinkError("No SelectSetImpl supported on this platform!");
      }
    }
    if (PROFILE) tracer = new seda.util.Tracer("NBIO");
  }

  /**
   * Create a SelectSet with no SelectItems.
   */
  public SelectSet() {
    switch (IMPL_TO_USE) {
      case POLL_IMPL: 
	impl = new SelectSetPollImpl();
	break;
      case DEVPOLL_IMPL:
	impl = new SelectSetDevPollImpl();
	break;
      default:
	throw new LinkageError("Error: SelectSet has bad value for IMPL_TO_USE; this is a bug - please contact <mdw@cs.berkeley.edu>");
    }
  }

  /**
   * Add a SelectItem to this SelectSet.
   */
  public void add(SelectItem sel) {
    impl.add(sel);
  }

  /**
   * Add all of the SelectItems in the given array to the SelectSet.
   */
  public void add(SelectItem selarr[]) {
    impl.add(selarr);
  }

  /**
   * Remove a SelectItem from the SelectSet.
   */
  public void remove(SelectItem sel) {
    impl.remove(sel);
  }

  /**
   * Remove all of the SelectItems in the given array from the SelectSet.
   */
  public void remove(SelectItem selarr[]) {
    impl.remove(selarr);
  }

  /**
   * Remove the SelectItem at the given index from the SelectSet.
   */
  public void remove(int index) {
    impl.remove(index);
  }

  /**
   * Update any changed 'events' fields in SelectItems registered with
   * this SelectSet. This method should be called if a SelectItem
   * 'events' field is modified after adding it to this SelectSet.
   */
  public void update() {
    impl.update();
  }

  /**
   * Update any changed 'events' fields in the given SelectItem. 
   * This method should be called if a SelectItem 'events' field is 
   * modified after adding it to this SelectSet.
   */
  public void update(SelectItem sel) {
    impl.update(sel);
  }

  /**
   * Return the number of SelectItems in this SelectSet.
   */
  public int size() {
    return impl.size();
  }

  /**
   * Return the number of active SelectItems in this SelectSet.
   * An active SelectItem is defined as one with a non-zero
   * events request mask.
   */
  public int numActive() {
    return impl.numActive();
  }

  /**
   * Return the SelectItem at the given index.
   */
  public SelectItem elementAt(int index) {
    return impl.elementAt(index);
  }

  /**
   * Wait for events to occur on the SelectItems in this SelectSet.
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
  public int select(int timeout) {
    return impl.select(timeout);
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
  public SelectItem[] getEvents(short mask) {
    return impl.getEvents(mask);
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
  public SelectItem[] getEvents() {
    return impl.getEvents();
  }

  public String toString() {
    String s = "SelectSet (impl "+impl.toString()+"):\n";
    for (int i = 0; i < size(); i++) {
      s = s + "\t"+elementAt(i).toString()+"\n";
    }
    return s;
  }

    public void interruptSelect() {
	if (DEBUG)System.err.println("SelectSet.interruptSelect() called.");
	impl.interruptSelect();
    }

}
