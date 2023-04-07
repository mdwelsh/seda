//-*-java-*-
/**
 * McastSender.java
 *
 * Author: Rob von Behren <jrvb@cs.berkeley.edu>
 * Created: 10/14/01
 * 
 * This software is copyrighted by Rob von Behren and the Regents of
 * the University of California.  The following terms apply to all
 * files associated with the software unless explicitly disclaimed in
 * individual files.
 * 
 * The authors hereby grant permission to use this software without
 * fee or royalty for any non-commercial purpose.  The authors also
 * grant permission to redistribute this software, provided this
 * copyright and a copy of this license (for reference) are retained
 * in all distributed copies.
 *
 * For commercial use of this software, contact the authors.
 * 
 * IN NO EVENT SHALL THE AUTHORS OR DISTRIBUTORS BE LIABLE TO ANY
 * PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL
 * DAMAGES ARISING OUT OF THE USE OF THIS SOFTWARE, ITS DOCUMENTATION,
 * OR ANY DERIVATIVES THEREOF, EVEN IF THE AUTHORS HAVE BEEN ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE AUTHORS AND DISTRIBUTORS SPECIFICALLY DISCLAIM ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND
 * NON-INFRINGEMENT.  THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS,
 * AND THE AUTHORS AND DISTRIBUTORS HAVE NO OBLIGATION TO PROVIDE
 * MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 **/

import seda.nbio.NonblockingMulticastSocket;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Multicast listener - writes contents of received packets to the console
 *
 * @author  <A HREF="http://www.cs.berkeley.edu/~jrvb/">Rob von Behren</A> 
 *      &lt;<A HREF="mailto:jrvb@cs.berkeley.edu">jrvb@cs.berkeley.edu</A>&gt;
 **/


public class McastSender {


   public static void main(String argv[]) throws Exception {
      
      // process command line
      if(argv.length != 2) {
         System.err.println("Format: java McastSender <multicast addr> <port>");
         System.exit(1);
      }
      InetAddress addr = InetAddress.getByName(argv[0]);
      int port = Integer.parseInt(argv[1]);
      

      // create the multicast socket
      NonblockingMulticastSocket sock = new NonblockingMulticastSocket(port);
      sock.joinGroup(addr);
      sock.connect(addr,port);
      sock.seeLocalMessages(false);

      // spin, sending packets
      int len;
      int count = 0;
      while(true) {

         byte data[] = ("message "+count).getBytes();
         //DatagramPacket p = new DatagramPacket(data, 0, data.length, addr, port);
         DatagramPacket p = new DatagramPacket(data, 0, data.length);
         len = sock.nbSend(p);

         if(len == 0)   {System.out.println("message "+count+" failed --- will retry");}
         else           {System.out.println("sent message: '"+new String(data)+"'"); count++;}

         Thread.sleep(1000);
      }
   }
}

//////////////////////////////////////////////////
// Set the emacs indentation offset
// Local Variables: ***
// c-basic-offset:3 ***
// End: ***
//////////////////////////////////////////////////
