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
 * A SelectItem represents a single socket/file descriptor/etc. which
 * can be handled by a SelectSet. Each SelectItem has an associated
 * Selectable as well as two event masks: 'events' and 'revents'.
 * Setting 'events' allows you to specify which events you are
 * interested in receiving notification on for this Selectable.
 * After calling SelectSet.select(), 'revents' will be set to the
 * set of events that occurred. 
 */
public class SelectItem {
  private Selectable sel; // Do not let user change this without updating fd

  /**
   * The set of events that you are interested in receiving notification on.
   * The event types are specified by the constants in Selectable.
   *
   * <p><b>Important:</b> If you change the events field of a SelectItem
   * after registering it with a SelectSet (using SelectSet.add()), 
   * you must invoke SelectSet.update() to push the new event mask to the
   * SelectSet.
   *
   * @see Selectable
   * @see SelectSet
   */
  public short events;

  /**
   * The set of events that occurred.
   * The event types are specified by the constants in Selectable.
   * @see Selectable
   */
  public short revents;

  /** 
   * A state object associated with this SelectItem. You can use this
   * for any purpose you like.
   */
  public Object obj;

  private NBIOFileDescriptor fd;

  private void updatefd() {
    // We want to keep a clone of the fd here, since if the socket 
    // closes, we want to remember the fd for deregistering with SelectSet.
    if (sel instanceof NonblockingSocket) {
      fd = ((NonblockingSocket)sel).impl.getFileDescriptor().getClone();
    } else if (sel instanceof NonblockingServerSocket) {
      fd = ((NonblockingServerSocket)sel).impl.getFileDescriptor().getClone();
    } else if (sel instanceof NonblockingDatagramSocket) {
      fd = ((NonblockingDatagramSocket)sel).impl.getFileDescriptor().getClone();
    } else {
      throw new IllegalArgumentException("Selectable must support internal NBIOFileDescriptor");
    }
  }

  // So SelectSet can get fd
  NBIOFileDescriptor getFD() {
    return fd;
  }

  /**
   * Create a SelectItem with the given Selectable, given state pointer,
   * and the given event mask.
   */
  public SelectItem(Selectable sel, Object obj, short events) {
    this.sel = sel;
    this.obj = obj;
    this.events = events;
    this.revents = (short)0;
    updatefd();
  }

  /**
   * Create a SelectItem with the given Selectable and the given event
   * mask.
   */
  public SelectItem(Selectable sel, short events) {
    this(sel, null, events);
  }

  /**
   * Return the Selectable associated with this SelectItem.
   */
  public Selectable getSelectable() {
    return sel;
  }

  /**
   * Return the state pointer associated with this SelectItem.
   */
  public Object getObj() {
    return obj;
  }

  /**
   * Return the requested events mask.
   */
  public short getEvents() {
    return events;
  }

  /**
   * Return the returned events mask.
   */
  public short returnedEvents() {
    return revents;
  }

  public String toString() {
    return "SelectItem [sel="+sel+"] events=0x"+Integer.toHexString(events)+" revents=0x"+Integer.toHexString(revents);
  }
  
}

