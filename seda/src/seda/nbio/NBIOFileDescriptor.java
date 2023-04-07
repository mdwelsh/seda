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
 * This is a package-internal class used to represent a UNIX file descriptor.
 */
class NBIOFileDescriptor {

  int fd;

  NBIOFileDescriptor() {
    fd = -1;
  }

  NBIOFileDescriptor(int fd) {
    this.fd = fd;
  }

  void setFD(int fd) {
    this.fd = fd;
  }

  int getFD() {
    return fd;
  }

  NBIOFileDescriptor getClone() {
    return new NBIOFileDescriptor(this.fd);
  }

  public int hashCode() {
    return fd;
  }

  public boolean equals(Object o) {
    if (!(o instanceof NBIOFileDescriptor)) return false;
    NBIOFileDescriptor thefd = (NBIOFileDescriptor)o;
    if (thefd.fd == this.fd) return true;
    else return false;
  }

}
