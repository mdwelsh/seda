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

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.lib.util.MultiByteArrayInputStream;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.TreeSet;

/**
 * This is a utility class that allows you to push multiple ATcpInPackets
 * in, and read bytes out as a stream. This is meant to be a convenience
 * for performing packet processing using the aSocket interfaces. 
 * This class also takes care of reordering packets according to the
 * ATcpInPacket sequence number; that is, if multiple threads in a stage
 * are receiving ATcpInPackets for the same connection, the aSocketInputStream
 * will internally reorder those packets.
 * 
 * @author Matt Welsh
 * @see MultiByteArrayInputStream
 */
public class aSocketInputStream extends MultiByteArrayInputStream {

  private static final boolean DEBUG = false;

  protected TreeSet outoforder;
  protected long nextSeqNum;

  /**
   * Create an aSocketInputStream with an initial sequence number of 1.
   */
  public aSocketInputStream() {
    super();
    outoforder = new TreeSet(new seqNumComparator());
    nextSeqNum = 1;
  }

  /**
   * Create an aSocketInputStream using the given initial sequence number.
   */
  public aSocketInputStream(long initialSeqNum) {
    super();
    outoforder = new TreeSet(new seqNumComparator());
    nextSeqNum = initialSeqNum;
  }

  // Internal class used to reorder elements of 'outoforder' according
  // to sequence number
  class seqNumComparator implements Comparator {
    public int compare(Object o1, Object o2) throws ClassCastException {
      ATcpInPacket p1 = (ATcpInPacket)o1;
      ATcpInPacket p2 = (ATcpInPacket)o2;
      long sn1 = p1.seqNum;
      long sn2 = p2.seqNum;

      if (sn1 == sn2) return 0;
      else if (sn1 < sn2) return -1;
      else return 1;
    }
  }

  /**
   * Add a packet to this aSocketInputStream. Reorders packets internally
   * so that bytes will be read from this InputStream according to the 
   * sequence number order of the packets.
   */
  public synchronized void addPacket(ATcpInPacket pkt) {
    long sn = pkt.getSequenceNumber();
    if (sn == 0) {
      // No sequence number -- assume it's in order, but don't increment
      // the nextSeqNum
      addArray(pkt.getBytes());
      return;
    } else if (sn == nextSeqNum) {
      addArray(pkt.getBytes());
      nextSeqNum++;
      // seqNum of 0 is special
      if (nextSeqNum == 0) nextSeqNum = 1;
    } else {
      // Assume out of order. Don't treat (sn < nextSeqNum)
      // differently than (sn > nextSeqNum), since we have
      // wraparound.
      outoforder.add(pkt);
    }

    // Push any 'ready' outoforder elements
    try {
      ATcpInPacket first = (ATcpInPacket)outoforder.first();
      while (first != null && first.seqNum == nextSeqNum) {
	outoforder.remove(first);
	addArray(first.getBytes());
	nextSeqNum++;
	// seqNum of 0 is special
	if (nextSeqNum == 0) nextSeqNum = 1;
	first = (ATcpInPacket)outoforder.first();
      }
    } catch (NoSuchElementException e) {
      // Ignore
    }
  }

  /**
   * Reinitialize the state of this input stream, clearing all
   * internal data and pointers. The next sequence number will 
   * be preserved.
   */
  public synchronized void clear() {
    super.clear();
    outoforder = new TreeSet(new seqNumComparator());
  }

  /**
   * Return the next expected sequence number.
   */
  public synchronized long getNextSequenceNumber() {
    return nextSeqNum;
  }

}
