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


/* This is a simple C test program which opens a nonblocking socket and
 * reads a sequence of packets from it.
 */

#include <stdio.h>

#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/tcp.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>

#undef NDEBUG
#include <assert.h>

#define PORT 4046

void main() {
  int fd;
  int arg;
  struct sockaddr_in him;
  char buf[4096];

  fd = socket(AF_INET, SOCK_STREAM, 0);
  setsockopt(fd,SOL_SOCKET, SO_REUSEADDR, (char *)&arg, 4);

  memset((char *)&him,  0, sizeof(him));
  him.sin_port = htons((short)0);
  him.sin_addr.s_addr = (unsigned long)htonl(INADDR_ANY);
  him.sin_family = AF_INET;

  assert(bind(fd, (struct sockaddr *)&him, sizeof(him)) >= 0);

  memset((char *)&him,  0, sizeof(him));
  him.sin_port = htons((short)PORT);
  him.sin_addr.s_addr = (unsigned long)htonl(INADDR_LOOPBACK);
  him.sin_family = AF_INET;

  assert(connect(fd, (struct sockaddr *)&him, sizeof(him)) >= 0);
  assert(fcntl(fd, F_SETFL, O_NONBLOCK) >= 0);

  while (1) {
    int n;
    fprintf(stderr,"Reading...\n");
    n = read(fd, buf, 4096);
    if (n >= 0) {
      fprintf(stderr,"Read %d bytes\n", n);
    } else {
      fprintf(stderr,"Got error: %d (errno=%d): %s\n", n, errno, strerror(errno));
    }
  }

}

