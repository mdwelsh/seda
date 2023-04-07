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

/* This is a simple test program demonstrating the NBIO library.
 * It creates a single nonblocking server socket, issues a blocking
 * accept(), and then continuously issues nonblocking reads.
 */

import seda.nbio.NonblockingServerSocket;
import seda.nbio.NonblockingSocket;

import java.io.InputStream;

public class NBIOServer {

  public static void main(String args[]) {

    try {
      System.err.println("NBIO server starting...");
      NonblockingServerSocket s = new NonblockingServerSocket(4046);
      NonblockingSocket clisock = s.accept();

      System.err.println("Got connection from "+clisock.getInetAddress().getHostName());
      InputStream is = clisock.getInputStream();
      byte barr[] = new byte[1024];

      while (true) {
        int c = is.read(barr,0,1024);
        if (c == -1) {
          System.err.println("READ ERROR");
        } else if (c == 0) {
          System.err.println("READ NOTHING");
        } else {
          String str = new String(barr,0,c);
          System.err.println("READ: "+str);
        }
      }

    } catch (Exception e) {
      System.err.println("NBIOServer: Caught exception: "+e);
      e.printStackTrace();
      System.exit(-1);
    }

  }

}
