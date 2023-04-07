/* 
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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

/*
 * This file implements native method bindings for MDWUtil.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include <time.h>
#include <sys/time.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/stat.h>

#ifdef SOLARIS
#include <stropts.h>
#include <sys/filio.h>
#endif

#include "MDWUtil.h"

#define DEBUG(_x) 

/*
 * Class:     seda_util_MDWUtil
 * Method:    currentTimeUsec
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_seda_util_MDWUtil_currentTimeUsec(JNIEnv *env, jclass theclass) {

  struct timeval tv;
  gettimeofday(&tv, NULL);
  DEBUG(fprintf(stderr, "seconds: %ld\n", tv.tv_sec));  
  DEBUG(fprintf(stderr, "microseconds: %ld\n", tv.tv_usec));  
  DEBUG(fprintf(stderr, "old math: %lld\n",
		(jlong)((tv.tv_sec * 1000000) + tv.tv_usec)));
  DEBUG(fprintf(stderr, "new math: %lld\n",
		(jlong)((tv.tv_sec * (jlong)1000000) + tv.tv_usec)));
  
  return (jlong)((tv.tv_sec * (jlong)1000000) + tv.tv_usec);
}


/*
 * Class:     seda_util_MDWUtil
 * Method:    usleep
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_seda_util_MDWUtil_usleep(JNIEnv *env, jclass theclass, jlong delay) {

  struct timespec spec, remspec;
  spec.tv_sec = delay / 1000000;
  spec.tv_nsec = (delay % 1000000) * 1000;
  while (nanosleep(&spec, &remspec) == EINTR) {
    spec.tv_sec = remspec.tv_sec;
    spec.tv_nsec = remspec.tv_nsec;
  }


}

