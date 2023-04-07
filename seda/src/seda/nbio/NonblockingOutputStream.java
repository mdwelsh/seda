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
import java.io.OutputStream;

/**
 * A NonblockingOutputStream is an OutputStream with nonblocking semantics.
 * The various write() methods are <b>blocking</b>, while the
 * nbWrite() methods are <b>nonblocking</b>. It was necessary to introduce
 * new methods as the original write() calls return void, and hence there
 * is no way to indicate that only a portion of the request was written.
 */
public abstract class NonblockingOutputStream extends OutputStream {

  /**
   * Perform a <b>blocking</b> write of one byte to this output stream.
   * Throws an EOFException if the end of stream has been reached.
   * Use nbWrite() to perform a non-blocking write of one byte. 
   */
  public abstract void write(int b) throws IOException;

  /**
   * Perform a blocking write of <code>b.length</code> bytes 
   * to the underlying stream. Use nbWrite() to perform a nonblocking
   * write.
   *
   */
  public abstract void write(byte b[]) throws IOException;

  /**
   * Perform a blocking write of <code>len</code> bytes to the 
   * underlying stream from the byte array <code>b</code> starting at offset 
   * <code>off</code>. Use nbWrite() to perform a nonblocking write.
   */
  public abstract void write(byte b[], int off, int len) throws IOException;

  /**
   * Perform a non-blocking write of one byte to this output stream.
   * Returns 1 if the data was written or 0 if it could not be.
   * Throws an EOFException if the end of the stream has been reached. 
   * Use write() to perform a blocking write of one byte.
   */
  public abstract int nbWrite(byte b) throws IOException;

  /**
   * Perform a nonblocking write of up to <code>b.length</code> bytes 
   * to the underlying stream. Returns the number of bytes written, or
   * 0 if nothing was written. Use write() to perform a blocking
   * write.
   */
  public abstract int nbWrite(byte b[]) throws IOException;

  /**
   * Perform a nonblocking write of up to <code>len</code> bytes 
   * to the underlying stream starting at offset <code>off</code>. 
   * Returns the number of bytes written, or 0 if nothing was written. 
   * Use write() to perform a blocking write.
   */
  public abstract int nbWrite(byte b[], int off, int len) throws IOException;

  /**
   * Flush the underlying output stream. This is a <b>blocking</b> operation.
   */
  public abstract void flush();

  public abstract void close() throws IOException;

}
