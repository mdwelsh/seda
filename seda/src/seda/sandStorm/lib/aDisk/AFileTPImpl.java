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

import seda.sandStorm.api.*;
import seda.sandStorm.core.FiniteQueue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This is an implementation of AFile which uses a pool of threads
 * which perform blocking I/O (through the java.io.RandomAccessFile
 * class) on files. This is a portable implementation but is not
 * intended to be high-performance.
 *
 * @author Matt Welsh
 * @see AFile
 */
class AFileTPImpl extends AFileImpl implements QueueElementIF {
 
  private File f;
  RandomAccessFile raf;
  private AFile afile;
  private AFileTPTM tm;
  private SinkIF compQ;
  private FiniteQueue eventQ;
  private boolean readOnly;
  private boolean closed;

  /**
   * Create an AFileTPIMpl with the given AFile, filename, completion
   * queue, create/readOnly flags, and Thread Manager.
   */
  AFileTPImpl(AFile afile, String fname, SinkIF compQ, boolean create, boolean readOnly, AFileTPTM tm) throws IOException {
    this.afile = afile;
    this.tm = tm;
    this.compQ = compQ;
    this.readOnly = readOnly;

    eventQ = new FiniteQueue();

    f = new File(fname);
    if (!f.exists() && !create) {
      throw new FileNotFoundException("File not found: "+fname);
    } 
    if (f.isDirectory()) {
      throw new FileIsDirectoryException("Is a directory: "+fname);
    }

    if (readOnly) {
      raf = new RandomAccessFile(f, "r");
    } else {
      raf = new RandomAccessFile(f, "rw");
    }
    closed = false;
  }

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public void enqueue(QueueElementIF req) throws SinkException {
    AFileRequest areq = (AFileRequest)req;
    if (closed) {
      throw new SinkClosedException("Sink is closed");
    }
    if (readOnly && (areq instanceof AFileWriteRequest)) {
      throw new BadQueueElementException("Cannot enqueue write request for read-only file", areq);
    }
    areq.afile = afile;
    try {
      eventQ.enqueue(areq);
    } catch (SinkException se) {
      throw new InternalError("AFileTPImpl.enqueue got SinkException - this should not happen, please contact <mdw@cs.berkeley.edu>");
    }
    if (eventQ.size() == 1) {
      tm.fileReady(this);
    }
  }

  /**
   * Enqueues the given request (which must be an AFileRequest)
   * to the file.
   */
  public boolean enqueue_lossy(QueueElementIF req) {
    AFileRequest areq = (AFileRequest)req;
    if (closed || (readOnly && (areq instanceof AFileWriteRequest))) {
      return false;
    }
    areq.afile = afile;
    try {
      eventQ.enqueue(areq);
    } catch (SinkException se) {
      throw new InternalError("AFileTPImpl.enqueue got SinkException - this should not happen, please contact <mdw@cs.berkeley.edu>");
    }
    if (eventQ.size() == 1) {
      tm.fileReady(this);
    }
    return true;
  }

  /**
   * Enqueues the given requests (which must be AFileRequests)
   * to the file.
   */
  public void enqueue_many(QueueElementIF[] elements) throws SinkException {
    if (closed) {
      throw new SinkClosedException("Sink is closed");
    }
    for (int i = 0; i < elements.length; i++) {
      enqueue(elements[i]);
    }
  }

  /**
   * Return information on the properties of the file.
   */
  AFileStat stat() {
    AFileStat s = new AFileStat();
    s.afile = afile;
    s.isDirectory = f.isDirectory();
    s.canRead = f.canRead();
    s.canWrite = f.canWrite();
    s.length = f.length();
    return s;
  }

  /**
   * Close the file after all enqueued requests have completed.
   * Disallows any additional requests to be enqueued on this file.
   * A SinkClosedEvent will be posted on the file's completion queue
   * when the close is complete.
   */
  public void close() {
    enqueue_lossy(new AFileCloseRequest(afile, compQ));
    closed = true;
  }

  /**
   * Causes a SinkFlushedEvent to be posted on the file's completion queue
   * when all pending requests have completed.
   */
  public void flush() {
    enqueue_lossy(new AFileFlushRequest(afile, compQ));
  }

  /**
   * Return the per-file event queue.
   */
  QueueIF getQueue() {
    return eventQ;
  }

}


