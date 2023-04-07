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

package seda.sandStorm.lib.util;

import seda.nbio.NonblockingInputStream;

import java.io.EOFException;
import java.io.IOException;

/**
 * This class allows you to treat a list of byte arrays as a single
 * NonblockingInputStream. This is helpful for parsing data contained
 * within network packets, where the payload for one ADU might be 
 * spread across multiple packets. This is a *nonblocking* interface;
 * if you attempt to read data from it, and none is available, it will
 * return immediately.
 * 
 * @author Matt Welsh
 */
public class MultiByteArrayInputStream extends NonblockingInputStream {

  private static final boolean DEBUG = false;
  protected static final int NUMARRAYS = 2;

  protected boolean closed;
  protected int cur_offset;
  protected int cur_array;
  protected byte[] arrays[];
  protected int push_array;
  protected int mark_array, mark_offset;

  /**
   * Create a MultiByteArrayInputStream with the given array of
   * byte arrays.
   */
  public MultiByteArrayInputStream(byte barr[][]) {
    arrays = new byte[barr.length+NUMARRAYS][];
    System.arraycopy(barr, 0, arrays, 0, barr.length);
    push_array = barr.length;
    cur_offset = 0;
    cur_array = 0;
    mark_array = -1; mark_offset = -1;
    closed = false;
  }

  /**
   * Create an empty MultiByteArrayInputStream.
   */
  public MultiByteArrayInputStream() {
    arrays = new byte[NUMARRAYS][];
    push_array = 0;
    cur_offset = 0;
    cur_array = 0;
    mark_array = -1; mark_offset = -1;
    closed = false;
  }

  /**
   * Add an array to this MultiByteArrayInputStream.
   */
  public synchronized void addArray(byte barr[]) {
    arrays[push_array] = barr;
    push_array++; if (push_array == arrays.length) expandArrays();
  }

  // Expand arrays if too long
  protected void expandArrays() {
    byte[] oldarr[] = arrays;
    arrays = new byte[oldarr.length + NUMARRAYS][];
    System.arraycopy(oldarr, 0, arrays, 0, oldarr.length);
  }

  /**
   * Read the next byte from this stream.
   * Returns -1 if no data is available.
   */
  public synchronized int read() throws IOException {
    if (DEBUG) System.err.println("MBS: read() called");
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    if (cur_array == push_array) {
      return -1;
    } else {
      if (DEBUG) System.err.println("read: cur_array "+cur_array+" num "+arrays.length+" cur_offset "+cur_offset+" len "+arrays[cur_array].length);
	   
      int c = (int)(arrays[cur_array][cur_offset] & 0xff);
      cur_offset++;
      if (cur_offset == arrays[cur_array].length) {
	cur_offset = 0;
	cur_array++;
      }
      return c;
    }
  }

  /**
   * Read the next byte from this stream.
   * Returns -1 if no data is available.
   */
  public synchronized int nbRead() throws IOException {
    if (DEBUG) System.err.println("MBS: nbRead() called");
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    int c;
    if (cur_array == push_array) {
      return -1;
    } else {
      c = (int)(arrays[cur_array][cur_offset] & 0xff);
      cur_offset++;
      if (cur_offset == arrays[cur_array].length) {
	cur_offset = 0;
	cur_array++;
      }
      return c;
    }
  }

  /**
   * Read data from this input stream into the given byte array starting
   * at offset 0 for b.length bytes. Returns the actual number of bytes
   * read; returns -1 if no data is available.
   */
  public synchronized int read(byte b[]) throws IOException {
    if (DEBUG) System.err.println("MBS: read(byte[]) called, size "+b.length);
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    return read(b, 0, b.length);
  }

  /**
   * Read data from this input stream into the given byte array starting
   * at offset 'off' for 'len' bytes. Returns the actual number of bytes
   * read; returns -1 if no data is available.
   */
  public synchronized int read(byte b[], int off, int len) throws IOException {
    if (DEBUG) System.err.println("MBS: read(byte[], int, int) called, size "+b.length+", off "+off+", len "+len);
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    int n = off;
    int total = 0;
    int last = Math.min(off+len, b.length);

    if (DEBUG) System.err.println("MBS: read(byte[], int, int): cur_array "+cur_array+", push_array "+push_array+", arrays.length "+arrays.length+", n "+n+", last "+last);
    
    if (cur_array == push_array) return -1;

    while ((cur_array < arrays.length) && (cur_array != push_array) && (n < last)) {
      if (DEBUG) System.err.println("MBS: read(byte[], int, int): cur_array "+cur_array+", push_array "+push_array+", arrays.length "+arrays.length+", n "+n+", last "+last);

      int num_left = arrays[cur_array].length - cur_offset;
      int tocopy = Math.min(num_left, last - n);
      System.arraycopy(arrays[cur_array], cur_offset, b, n, tocopy);
      total += tocopy;
      n += tocopy;
      cur_offset += tocopy;
      if (cur_offset == arrays[cur_array].length) {
	cur_offset = 0;
	cur_array++;
      }
    }
    return total;
  }

  /**
   * Skip n bytes in this stream; returns the number of bytes
   * actually skipped (which may be less than the number requested).
   */
  public synchronized long skip(long n) throws IOException {
    if (DEBUG) System.err.println("MBS: skip() called, n="+n);
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    int requested = Math.min((int)n, Integer.MAX_VALUE);
    int totalskipped = 0;

    if (cur_array == push_array) return 0;

    while ((cur_array < arrays.length) && (requested > 0)) {
      int num_left = arrays[cur_array].length - cur_offset;
      int toskip = Math.min(num_left, requested);
      totalskipped += toskip;
      requested -= toskip;
      cur_offset = 0;
      cur_array++;
    }
    return totalskipped;
  }

  /**
   * Return the number of bytes available for reading.
   */
  public synchronized int available() throws IOException {
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    if (cur_array == push_array) return 0;
    int num_left = arrays[cur_array].length - cur_offset;
    for (int i = cur_array+1; i < arrays.length; i++) {
      if (arrays[i] == null) break;
      num_left += arrays[i].length;
    }
    if (DEBUG) System.err.println("MBS: available() called, num_left="+num_left);
    return num_left;
  }

  /**
   * Close this stream.
   */
  public synchronized void close() throws IOException {
    if (DEBUG) System.err.println("MBS: close() called");
    if (closed) throw new EOFException("MultiByteArrayInputStream is closed!");
    arrays = null; // Facilitate GC
    closed = true;
  }

  /**
   * Returns true, since mark() and reset() are supported.
   */
  public boolean markSupported() {
    return true;
  }

  /**
   * Returns the stream to the position of the previous mark().
   */
  public synchronized void reset() throws IOException {
    if (DEBUG) System.err.println("MBS: reset() called");
    if (mark_array == -1) throw new IOException("MultiByteArrayInputStream not marked!");
    cur_array = mark_array;
    cur_offset = mark_offset;
  }

  /**
   * Set the stream's mark to the current position.
   * 'readlimit' is ignored, since there is no limit to how many
   * bytes can be read before the mark is invalidated.
   */ 
  public synchronized void mark(int readlimit) {
    if (DEBUG) System.err.println("MBS: mark() called, readlimit="+readlimit);
    mark_array = cur_array;
    mark_offset = cur_offset;
  }

  /**
   * Return the number of bytes registered.
   */
  public synchronized int numArrays() {
    return push_array;
  }

  /**
   * Reset this input stream - clear all internal data and pointers to
   * a fresh initialized state. 
   */
  public synchronized void clear() {
    arrays = new byte[NUMARRAYS][];
    push_array = 0;
    cur_offset = 0;
    cur_array = 0;
    mark_array = -1; mark_offset = -1;
    closed = false;
  }

}
