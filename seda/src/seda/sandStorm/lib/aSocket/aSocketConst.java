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

package seda.sandStorm.lib.aSocket;

/**
 * Internal constants used by the aSocket implementation.
 */
public interface aSocketConst {

  /** The size of the internal read buffer in bytes */
  public static final int READ_BUFFER_SIZE = 16384;
  /** Indicates whether the reader should copy data into a new buffer */
  public static final boolean READ_BUFFER_COPY = true;
  /** Number of times to try to finish a socket write */
  public static final int TRYWRITE_SPIN = 10;
  /** Maximum number of bytes to try writing at once; -1 if no limit */
  public static final int MAX_WRITE_LEN = -1; 
  /** Maximum number of write reqs on a socket to process at once */
  public static final int MAX_WRITE_REQS_PER_SOCKET = 1000;
  /** Maximum number of writes to process at once */
  public static final int MAX_WRITES_AT_ONCE = -1;
  /** Maximum number of accepts to process at once */
  public static final int MAX_ACCEPTS_AT_ONCE = 1000;
  /**
   * Number of empty writes after which write-ready mask is disabled.
   * If set to -1, no disable will occur.
   */
  public static final int WRITE_MASK_DISABLE_THRESHOLD = 10;

  /** Time in ms to sleep waiting for select */
  public static final int SELECT_TIMEOUT = 1000;
  /** Number of times to spin on select */
  public static final int SELECT_SPIN = 1;
  /** Time in ms to sleep waiting on event queue */
  public static final int EVENT_QUEUE_TIMEOUT = 1000;
  /** Number of times to spin on event queue */
  public static final int EVENT_QUEUE_SPIN = 10;

  /** Maximum aggregation constant for aSocketRCTM. */
  public static final int LARGE_AGGREGATION = 4096;
  /** Number of measurements to use when adjusting rate in aSocketRCTM. */
  public static final int MEASUREMENT_SIZE = 200;

  public static final String READSTAGE_NAME = "aSocket ReadStage";
  public static final String WRITESTAGE_NAME = "aSocket WriteStage";
  public static final String LISTENSTAGE_NAME = "aSocket ListenStage";

}
