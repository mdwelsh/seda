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
 * Package-internal class implementing NonblockingOutputStream for
 * nonblocking sockets.
 */
class NonblockingSocketOutputStream extends NonblockingOutputStream {

  private static final boolean PROFILE = false;

  private NBIOFileDescriptor fd;
  private boolean eof;
  private NonblockingSocketImpl impl;
  private byte temp[] = new byte[1];

  private static final int SKIPBUFLEN = 4096;

  /* Non-blocking write on the underlying socket. 
   * Returns -1 if EOF has been reached, 0 if no data was written, otherwise
   * the number of bytes written
   */
  private native int nbSocketWrite(byte b[], int off, int len) throws IOException;

  NonblockingSocketOutputStream(NonblockingSocketImpl impl) {
    fd = impl.getFileDescriptor();
    this.impl = impl;
  }

  /**
   * Perform a <b>blocking</b> write of one byte to this output stream.
   * Throws an EOFException if the end of stream has been reached.
   * Use nbWrite() to perform a non-blocking write of one byte. 
   */
  public void write(int b) throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    int n;
    temp[0] = (byte)b;
    // Spin until we write a byte -- ugly
    while ((n = nbSocketWrite(temp, 0, 1)) == 0) ;
    if (n < 0) {
      eof = true;
      throw new EOFException("EOF on "+toString());
    }
  }


  /**
   * Perform a blocking write of <code>b.length</code> bytes 
   * to the underlying stream. Use nbWrite() to perform a nonblocking
   * write.
   *
   */
  public void write(byte b[]) throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    int n, count = 0;
    while (count < b.length) {
      n = nbSocketWrite(b, count, (b.length - count));
      if (n < 0) {
        eof = true;
        throw new EOFException("EOF on "+toString());
      }
      count += n;
    }
  }

  /**
   * Perform a blocking write of <code>len</code> bytes to the 
   * underlying stream from the byte array <code>b</code> starting at offset 
   * <code>off</code>. Use nbWrite() to perform a nonblocking write.
   */
  public void write(byte b[], int off, int len) throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    int n, count = 0;
    while (count < len) {
      n = nbSocketWrite(b, count+off, (len - count));
      if (n < 0) {
        eof = true;
        throw new EOFException("EOF on "+toString());
      }
      count += n;
    }
  }

  /**
   * Perform a non-blocking write of one byte to this output stream.
   * Returns 1 if the data was written or 0 if it could not be.
   * Throws an EOFException if the end of the stream has been reached. 
   * Use write() to perform a blocking write of one byte.
   */
  public int nbWrite(byte b) throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    temp[0] = (byte)b;
    int n = nbSocketWrite(temp, 0, 1);
    if (n < 0) {
      eof = true;
      throw new EOFException("EOF on "+toString());
    }
    return n;
  }

  /**
   * Perform a nonblocking write of up to <code>b.length</code> bytes 
   * to the underlying stream. Returns the number of bytes written, or
   * 0 if nothing was written. Use write() to perform a blocking
   * write.
   */
  public int nbWrite(byte b[]) throws IOException {
    if (eof) throw new EOFException("EOF on "+toString());
    int n = nbSocketWrite(b, 0, b.length);
    if (n < 0) {
      eof = true;
      throw new EOFException("EOF on "+toString());
    }
    return n;
  }

  /**
   * Perform a nonblocking write of up to <code>len</code> bytes 
   * to the underlying stream starting at offset <code>off</code>. 
   * Returns the number of bytes written, or 0 if nothing was written. 
   * Use write() to perform a blocking write.
   */
  public int nbWrite(byte b[], int off, int len) throws IOException {
    if (PROFILE) SelectSet.tracer.trace("nbWrite called");

    if (eof) throw new EOFException("EOF on "+toString());
    int n = nbSocketWrite(b, off, len);
    if (PROFILE) SelectSet.tracer.trace("nbSocketWrite returned");
    if (n < 0) {
      eof = true;
      throw new EOFException("EOF on "+toString());
    }
    return n;
  }

  /**
   * flush() does nothing in this implementation.
   */
  public void flush() {
  }

  public void close() throws IOException {
    impl.close();
  }

}
