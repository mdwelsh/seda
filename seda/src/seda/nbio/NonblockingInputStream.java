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

import java.io.IOException;
import java.io.InputStream;

/**
 * A NonblockingInputStream is an InputStream which implements nonblocking
 * semantics. The only additional method is nbRead() which performs a
 * nonblocking read of one byte. The read(byte[]) and read(byte[], int, int)
 * methods are also nonblocking. The standard read(byte) call is blocking
 * as there is no way to indicate that nothing was read (a -1 means
 * an error occurred). 
 */
public abstract class NonblockingInputStream extends InputStream {

  /**
   * Perform a <b>blocking</b> read of one byte from this input stream.
   * Returns -1 if the end of the stream has been reached.
   * Use nbRead() to perform a non-blocking read of one byte. 
   */
  public abstract int read() throws IOException;

  /**
   * Perform a non-blocking read of one byte from this input stream.
   * Returns -1 if no data is available, or throws an EOFException if the
   * end of the stream has been reached. Use read() to perform a blocking 
   * read of one byte.
   */
  public abstract int nbRead() throws IOException;

  /**
   * Perform a non-blocking read of up to <code>b.length</code> bytes 
   * from the underlying stream. 
   *
   * @return The total number of bytes read into the buffer, 0 if 
   *         no data was available, or -1 if the end of the stream has
   *         been reached.
   *
   */
  public abstract int read(byte b[]) throws IOException;

  /**
   * Perform a non-blocking read of up to <code>len</code> bytes from the 
   * underlying stream into the byte array <code>b</code> starting at offset 
   * <code>off</code>.
   *
   * @return The total number of bytes read into the buffer, 0 if 
   *         no data was available, or -1 if the end of the stream has
   *         been reached.
   */
  public abstract int read(byte b[], int off, int len) throws IOException;

  /**
   * Skip n bytes of input. This is a <b>blocking</b> operation.
   */
  public abstract long skip(long n) throws IOException;

  public abstract int available() throws IOException;

  public abstract void close() throws IOException;

}
