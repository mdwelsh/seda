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

package seda.sandStorm.lib.aDisk;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkException;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.SimpleSink;

import java.io.IOException;

/**
 * This class represents an asynchronous file I/O interface.
 * <p>
 * To use this class, the user creates an AFile corresponding to a given
 * filename. AFile implements SinkIF, and as such you can enqueue
 * I/O requests to be performed on this file; all such requests must be
 * subclasses of AFileRequest. The <tt>read</tt>, <tt>write</tt>, and
 * <tt>seek</tt> methods are also provided for convenience.
 *
 * @author Matt Welsh
 * @see SinkIF, AFileRequest
 */
public class AFile extends SimpleSink {

  private String fname;
  private AFileImpl impl;
  private SinkIF compQ;

  /**
   * Open the file with the given pathname. 
   *
   * @param name A system-dependent filename.
   * @param compQ The default completion queue on which read and write
   *   completion events will be posted. A completion queue can
   *   be specified for each individual request by setting the 'compQ'
   *   field in the associated AFileRequest.
   * @param create If true, creates the file if it does not exist.
   * @param create If true, opens the file in read-only mode.
   *
   * @exception FileNotFoundException If the file does not exist and 
   *  'create' is false.
   */
  public AFile(String name, SinkIF compQ, boolean create, boolean readOnly) throws IOException {
    AFileMgr.initialize();
    this.compQ = compQ;
    this.fname = name;

    switch (AFileMgr.getImpl()) {
      case AFileMgr.THREADPOOL_IMPL: 
	impl = new AFileTPImpl(this, name, compQ, create, readOnly, (AFileTPTM)AFileMgr.getTM());
	break;
      default:
	throw new LinkageError("Error: AFileMgr has bad value for IMPL_TO_USE; this is a bug - please contact <mdw@cs.berkeley.edu>");
    }
  }

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public synchronized void enqueue(QueueElementIF req) throws SinkException {
    impl.enqueue(req);
  }

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public synchronized boolean enqueue_lossy(QueueElementIF req) {
    return impl.enqueue_lossy(req);
  }

  /**
   * Enqueues the given requests (which must be AFileRequests)
   * to the file.
   */
  public synchronized void enqueue_many(QueueElementIF[] elements) throws SinkException {
    impl.enqueue_many(elements);
  }

  // The following are convenience methods ---------------------------------

  /**
   * Enqueues a write request at the current file offset.
   */
  public synchronized void write(BufferElement buf) throws SinkException {
    this.enqueue(new AFileWriteRequest(buf, compQ));
  }

  /**
   * Enqueues a write request at the given file offset. This is 
   * equivalent to a call to seek() before write().
   */
  public synchronized void write(BufferElement buf, int offset) throws SinkException {
    this.enqueue(new AFileSeekRequest(offset, null));
    this.enqueue(new AFileWriteRequest(buf, compQ));
  }

  /**
   * Enqueues a read request at the current file offset.
   */
  public synchronized void read(BufferElement buf) throws SinkException {
    this.enqueue(new AFileReadRequest(buf, compQ));
  }

  /**
   * Enqueues a read request at the given file offset. This is equivalent 
   * to a call to seek() before read().
   */
  public synchronized void read(BufferElement buf, int offset) throws SinkException {
    this.enqueue(new AFileSeekRequest(offset, null));
    this.enqueue(new AFileReadRequest(buf, compQ));
  }

  /**
   * Position the file to the given offset. As with read and writes,
   * seek requests are performed asynchronously; only read and write
   * requests enqueued after the seek operation will use the new file
   * offset.
   */
  public synchronized void seek(int offset) throws SinkException {
    this.enqueue(new AFileSeekRequest(offset, compQ));
  }

  /**
   * Return information on the properties of the file.
   */
  public AFileStat stat() {
    return impl.stat();
  }

  public String getFilename() {
    return fname;
  }

  /**
   * Close the file after all enqueued requests have completed.
   * Disallows any additional requests to be enqueued on this file.
   * A SinkClosedEvent will be posted on the file's completion queue
   * when the close is complete.
   */
  public synchronized void close() {
    impl.close();
  }

  /**
   * Causes a SinkFlushedEvent to be posted on the file's completion queue
   * when all pending requests have completed.
   */
  public synchronized void flush() {
    impl.flush();
  }

  /**
   * Returns the implementation-specific object representing this AFile.
   * Package access only.
   */
  AFileImpl getImpl() {
    return impl;
  }

  public String toString() {
    return "AFile [fname="+fname+"]";
  }

}


