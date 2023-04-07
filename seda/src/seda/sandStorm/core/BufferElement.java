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

package seda.sandStorm.core;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;

/**
 * A BufferElement is a QueueElementIF which represents a memory buffer.
 *
 * @author Matt Welsh 
 */
public class BufferElement implements QueueElementIF {

  /** 
   * The data associated with this BufferElement.
   */
  public byte data[];

  /**
   * The completion queue associated with this buffer.
   */
  public SinkIF compQ;

  /**
   * A user-defined tag object associated with this buffer.
   * Can be used as a back-pointer from the buffer to application
   * state, e.g., for handling completions.
   */
  public Object userTag;

  /**
   * The size of the data associated with this BufferElement. May not be
   * equal to data.length; may be any value less than or equal to 
   * (data.length - offset).
   */
  public int size;

  /**
   * The offet into the data associated with this BufferElement. 
   */
  public int offset;

  /**
   * Create a BufferElement with the given data, an offset of 0, and a 
   * size of data.length.
   */
  public BufferElement(byte data[]) {
    this(data, 0, data.length, null);
  }

  /**
   * Create a BufferElement with the given data, an offset of 0, and a 
   * size of data.length, with the given completion queue.
   */
  public BufferElement(byte data[], SinkIF compQ) {
    this(data, 0, data.length, compQ);
  }

  /**
   * Create a BufferElement with the given data, offset, and size.
   */
  public BufferElement(byte data[], int offset, int size) {
    this(data, offset, size, null);
  }

  /**
   * Create a BufferElement with the given data, offset, size, and 
   * completion queue.
   */
  public BufferElement(byte data[], int offset, int size, SinkIF compQ) {
    this.data = data;
    if ((offset >= data.length) || (size > (data.length - offset))) {
      throw new IllegalArgumentException("BufferElement created with invalid offset and/or size (off="+offset+", size="+size+", data.length="+data.length+")");
    }
    this.offset = offset;
    this.size = size;
    this.compQ = compQ;
  }

  /**
   * Create a BufferElement with a new data array of the given size.
   */
  public BufferElement(int size) {
    this(new byte[size], 0, size, null);
  }

  /**
   * Return the data.
   */
  public byte[] getBytes() {
    return data;
  }

  /**
   * Return the size.
   */
  public int getSize() {
    return size;
  }

  /**
   * Return the offset.
   */
  public int getOffset() {
    return offset;
  }

  /**
   * Return the completion queue for this buffer.
   */
  public SinkIF getCompletionQueue() {
    return compQ;
  }

}

