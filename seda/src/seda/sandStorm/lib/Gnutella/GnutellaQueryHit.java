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



/**
 * A Gnutella query hit.
 */
public class GnutellaQueryHit {
  public int index;
  public int size;
  public String filename;

  //static GnutellaQueryHit[] read(int n, byte payload, int offset) {
  //  int index;
  //  int size; 
  //  String fname;
  //  int off = offset;
  //  int num = 0;

   // while ((off < payload.length-16) && (num < n)) {
   //   index = GnutellaPacket.readLEInt(payload, off); off += 4;
   //   size = GnutellaPacket.readLEInt(payload, off); off += 4;
   //   // Look for double null
   //   int n;
   //   for (n = off; n < payload.length-17; n++) {
   //     if (payload[n] == 0) break;
   //   }
   //   if (payload[n+1] != 0) {
   //     off = n+1; continue;
   //   }
   //   filename = new String(payload, off, n-1);
   //   off = n+2;

  public GnutellaQueryHit(byte payload[], int offset) {
    System.err.println("QueryHit: index "+index+", size "+size+", fname "+filename);
  }

  public GnutellaQueryHit(int index, int size, String filename) {
    this.index = index;
    this.size = size;
    this.filename = filename;
  }

  // Return size in bytes
  int getSize() {
    // Ends in double-NULL
    return 8+filename.length()+2;
  }

  // Dump to given byte array
  public void dump(byte barr[], int offset) {
    GnutellaPacket.writeLEInt(index, barr, offset);
    GnutellaPacket.writeLEInt(size, barr, offset+4);
    byte data[] = filename.getBytes();
    System.arraycopy(data, 0, barr, offset+8, data.length);
    barr[offset+8+data.length] = (byte)0;
    barr[offset+8+data.length+1] = (byte)0;
  }

}
