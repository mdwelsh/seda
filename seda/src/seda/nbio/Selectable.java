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
 * Selectable is an interface which represents an object (such as a socket
 * or file descriptor) which may be used with the SelectItem and SelectSet
 * classes. It defines no methods.
 *
 * The static final fields of this interface specify the event types that
 * may be used with SelectItem and SelectSet.
 */
public interface Selectable {

  /**
   * Event mask specifying that data can be read without blocking.
   */
  public static final short READ_READY = (short)0x01;

  /**
   * Event mask specifying that data can be written without blocking.
   */
  public static final short WRITE_READY = (short)0x02;

  /**
   * Event mask specifying that a new incoming connection is pending.
   */
  public static final short ACCEPT_READY = READ_READY;

  /**
   * Event mask specifying that a pending outgoing connection has
   * been established.
   */
  public static final short CONNECT_READY = WRITE_READY;

  /**
   * Specifies that an error has occured on this SelectItem.
   * Invoking the requested read, write, connect, etc. operation will
   * throw the appropriate exception. Only valid for revents.
   */
  public static final short SELECT_ERROR = (short)0x80;
  
}

