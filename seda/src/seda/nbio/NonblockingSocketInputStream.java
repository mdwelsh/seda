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

import java.io.EOFException;
import java.io.IOException;

/**
 * Package-internal class implementing NonblockingInputStream for
 * nonblocking sockets.
 */
class NonblockingSocketInputStream extends NonblockingInputStream {

  private NBIOFileDescriptor fd;
  private boolean eof;
  private NonblockingSocketImpl impl;
  private byte temp[] = new byte[1];

  private static final int SKIPBUFLEN = 4096;

  /* Non-blocking read on the underlying socket. 
   * Returns -1 if EOF has been reached, 0 if no data is available, otherwise
   * the number of bytes read.
   */
  private native int nbSocketRead(byte b[], int off, int len) throws IOException;

  NonblockingSocketInputStream(NonblockingSocketImpl impl) {
    fd = impl.getFileDescriptor();
    this.impl = impl;
  }

  /**
   * Perform a <b>blocking</b> read of one byte from this input stream.
   * Returns -1 if the end of the stream has been reached.
   * Use nbRead() to perform a non-blocking read of one byte. 
   */
  public int read() throws IOException {
    if (eof) return -1;
    int n;
    // Spin until we read a byte -- ugly
    while ((n = read(temp, 0, 1)) == 0) ;
    if (n < 0) {
      eof = true;
      return -1;
    }
    return temp[0] & 0xff;
  }

  /**
   * Perform a non-blocking read of one byte from this input stream.
   * Returns -1 if no data is available, or throws an EOFException if the
   * end of the stream has been reached. Use read() to perform a blocking 
   * read of one byte.
   */
  public int nbRead() throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    int n = read(temp, 0, 1);
    if (n == 0) return -1;
    if (n < 0) {
      eof = true;
      throw new EOFException("EOF on "+toString());
    }
    return temp[0] & 0xff;
  }

  /**
   * Perform a non-blocking read of up to <code>b.length</code> bytes 
   * from the underlying stream. 
   *
   * @return The total number of bytes read into the buffer, 0 if 
   *         no data was available, or -1 if the end of the stream has
   *         been reached.
   *
   */
  public int read(byte b[]) throws IOException {
    return read(b, 0, b.length);
  }

  /**
   * Perform a non-blocking read of up to <code>len</code> bytes from the 
   * underlying stream into the byte array <code>b</code> starting at offset 
   * <code>off</code>.
   *
   * @return The total number of bytes read into the buffer, 0 if 
   *         no data was available, or -1 if the end of the stream has
   *         been reached.
   */
  public int read(byte b[], int off, int len) throws IOException {
    if (eof) return -1;
    int n = nbSocketRead(b, off, len);
    if (n < 0) {
      eof = true;
      return -1;
    }
    return n;
  }

  /**
   * Skip n bytes of input. This is a <b>blocking</b> operation.
   */
  public long skip(long n) throws IOException {
    if (n <= 0) return 0;
    int buflen = (int)Math.min(SKIPBUFLEN, n);
    byte data[] = new byte[buflen];
    while (n > 0) {
      int r = read(data, 0, (int)Math.min((long)buflen, n));
      if (r < 0) break;
      n -= r;
    }
    return n;
  }

  public int available() throws IOException {
    return impl.available();
  }

  public void close() throws IOException {
    impl.close();
  }

}
