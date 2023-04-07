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

package seda.sandStorm.lib.Gnutella;

import java.util.Random;

/**
 * This class represents the GUID contained in Gnutella network packets.
 */
public class GnutellaGUID implements GnutellaConst {

  private static final boolean DEBUG = false;

  byte data[];
  private int hash;

  private static Random rand = null;

  public GnutellaGUID() {
    if (rand == null) rand = new Random();
    data = new byte[16];
    rand.nextBytes(data);
    hash = GnutellaPacket.readLEInt(data, 0);
  }

  public GnutellaGUID(byte barr[], int offset) {
    data = new byte[16];
    System.arraycopy(barr, offset, data, 0, 16);
    hash = GnutellaPacket.readLEInt(data, 0);
  }

  public void dump(byte barr[], int offset) {
    System.arraycopy(data, 0, barr, offset, 16);
  }
  
  public String toString() {
    String s = "[GUID ";
    for (int i = 0; i < 16; i++) {
      int c = data[i] & 0xff;
      String s1 = Integer.toHexString(c);
      if (c < 0x10) s += "0"+s1;
      else s += s1;

    }
    s += "]";
    return s;
  }

  public int hashCode() {
    return hash;
  }

  public boolean equals(Object o) {
    if (!(o instanceof GnutellaGUID)) return false;
    GnutellaGUID guid = (GnutellaGUID)o;
    boolean same = true;
    for (int i = 0; i < 16; i++) {
      if (guid.data[i] != data[i]) same = false;
    }
    return same;
  }

}
