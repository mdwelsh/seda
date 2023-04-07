/* 
 * Copyright (c) 2001 by Matt Welsh and The Regents of the University of 
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

package seda.sandStorm.internal;

import seda.sandStorm.api.*;
import seda.sandStorm.api.internal.*;
import seda.sandStorm.main.SandstormConfig;

import java.util.Enumeration;
import java.util.Vector;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * TPPThreadManager is a thread manager implementation which provides
 * one thread per CPU.
 * 
 * @author   Matt Welsh
 */

class TPPThreadManager implements ThreadManagerIF {

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_VERBOSE = false;

  private int num_cpus, max_threads;
  private LinkedList stages;
  private LinkedList threads;
  private ThreadGroup tg;
  private boolean crashOnException;

  /**
   * An <code>Iterator</code> interface to the list of stages.
   * The {@link #schedule_queue} method uses this variable to implement
   * scheduling policies that are more interesting than round-robin.
   */
  private Iterator run_queue;

  /**
   * All accesses to <code>run_queue</code> must be guarded by this lock,
   * because the former variable may be overwritten.
   */
  private ScheduleLock run_queue_lock = new ScheduleLock();

  /**
   * Create an TPPThreadManager which attempts to schedule stages on
   * num_cpus CPUs, and caps its thread usage to max_threads.
   */
  TPPThreadManager(SandstormConfig config)
  {
    this.num_cpus = config.getInt("global.TPPTM.numCpus");
    this.max_threads = config.getInt("global.TPPTM.maxThreads");
    this.crashOnException = config.getBoolean("global.crashOnException");    

    stages = new LinkedList();
    threads = new LinkedList();

    tg = new ThreadGroup("TPPThreadManager");
    synchronized(run_queue_lock)
    {
      for(int i = 0; i < num_cpus; i++)
      {
        String name = new String("TPPTM-"+i);
        Thread t = new Thread(tg, new appThread(name), name);
        threads.add(t);
      }
    }
    ListIterator i = threads.listIterator();
    while(i.hasNext())
      ((Thread)i.next()).start();
  }

  /**
   * Register a stage with this thread manager.
   */
  public void register(StageWrapperIF stage)
  {
    synchronized(run_queue_lock)
    {
      stages.add(new StageQueueElement(stage, false));
      run_queue_lock.notifyAll();
    } 
  }

  /**
   * Deregister a stage with this thread manager.
   */
  public void deregister(StageWrapperIF stage)
  {
    synchronized(run_queue_lock)
    {
      if(!stages.remove(new StageQueueElement(stage, false)))
	throw new IllegalArgumentException("Stage "+stage+" not registered with this TM");
    }
  }

  /**
   * Deregister all stage with this thread manager.
   */
  public void deregisterAll()
  {
    synchronized(run_queue_lock)
    {
      Iterator i = stages.listIterator();
      while(i.hasNext())
      {
	StageQueueElement stage = (StageQueueElement)i.next();
	i.remove();
      }
      tg.stop();
    }
  }

  /**
   * Wake any thread waiting for work.  This is called by the
   * an enqueue* method of FiniteQueue.
   **/
  public void wake()
  {
    synchronized(run_queue_lock)
    {
      run_queue_lock.notifyAll();
    }
  }

  /**
   * Orders the stages and assigns <code>this.run_queue</code>
   * Currently implements round-robin with the stages ordered by the size
   * of their event queue.
   */
  private int schedule_queue() 
  {
    // calling method already called synchronized(run_queue_lock)
    TreeMap sorted_queue = new TreeMap();

    ListIterator i = stages.listIterator();
    while(i.hasNext())
    {
      StageQueueElement element = (StageQueueElement)i.next();
      SourceIF src = element.stage.getSource();
      int size = src.size();
      // Invert ordering so iterator produces decreasing order.
      if(size > 0 && !element.locked)
      {
	if(sorted_queue==null)
	  sorted_queue = new TreeMap();
	sorted_queue.put(new StageQueueKey(-1*size, src.hashCode()), element);
      }
    }
    this.run_queue = (sorted_queue==null ? null : sorted_queue.values().iterator());
    return (sorted_queue==null ? 0 : sorted_queue.size());
  }

  /**
   * Loop until a stage is available to be scheduled.
   * The loop alternates creating a schedule and sleeping if there is no work.
   */
  private StageQueueElement scheduleNextStage()
  {
    StageQueueElement element = null;
    synchronized(run_queue_lock)
    {
      // Loop until schedule is available
      while((run_queue == null) || !run_queue.hasNext())
      {
	int num_stages = schedule_queue();

       	if(num_stages <= 0)
	{
	  try 
	  {
	    if(DEBUG_VERBOSE) System.err.println(Thread.currentThread().getName()+": No work.  Going to sleep.");

	    run_queue_lock.wait();

	    if(DEBUG_VERBOSE) System.err.println(Thread.currentThread().getName()+": Awake.  Lets see if theres work.");
	    
	    continue;
	  } 
	  catch (InterruptedException ie) 
	  {
	    // Ignore
	  }
	}

	if(DEBUG_VERBOSE) System.err.println(Thread.currentThread().getName()+": created a new schedule with "+num_stages + " stages.");

      }

      // Find some work.
      EventHandlerIF handler = null;
      while(run_queue.hasNext())
      {
	element = (StageQueueElement)run_queue.next();
	handler = element.stage.getEventHandler();

	if(!(handler instanceof SingleThreadedEventHandlerIF))
	{
	  // locked stays false for Multithreaded stages.
	  break;
	}
	else if((handler instanceof SingleThreadedEventHandlerIF) && 
		!element.locked)
	{
	  element.locked = true;
	  break;
	}
	else // if((handler instanceof SingleThreadedEventHandlerIF) &&
	    //    element.locked)
	{
	  element = null;
	  continue;
	}
      }
    }
    return element;
  }

  /**
   * Internal class representing a single TPPTM-managed thread.
   */
  class appThread implements Runnable
  {
    private String name;

    appThread(String name)
    {
      this.name = name;
    }

    public void run()
    {
      System.err.println(name+": starting");

      while(true) 
      {
	// Wait until we have some stages 
	if(stages.size() == 0) // <- Not safe to set outside monitor
	{                      //    But do not want to pay synch cost 
	                       //    every iteration.
	  synchronized(run_queue_lock)
	  {
	    try 
	    {
	      if(stages.size() == 0)
		run_queue_lock.wait();
	    } 
	    catch (InterruptedException ie) 
	    {
	      // Ignore
	    }
	  }
	  continue;
	}

	// Schedule the next stage to run.
	// Make sure that a SingleThreaded stage is only run once;
	// that is, not run concurrently with itself.
	StageQueueElement element = scheduleNextStage();
	if(element == null)
	  continue;

	try 
	{
	  if(DEBUG_VERBOSE) System.err.println(name+": inspecting "+element.stage);

	  SourceIF src = element.stage.getSource();
	  QueueElementIF qelarr[] = src.dequeue_all();
	  EventHandlerIF handler = element.stage.getEventHandler();

	  if(qelarr != null)
	  {
	    if(DEBUG) System.err.println(name+": dequeued "+qelarr.length+" elements for "+element.stage);

	    // No need to pay synchronization cost here for
	    // stages that are SingleThreaded
	    handler.handleEvents(qelarr);		    

	    if(DEBUG) System.err.println(name+": returned from handleEvents for "+element.stage);
	  } 
	  else 
	  {
	    if(DEBUG_VERBOSE) System.err.println(name+": got null on dequeue for"+element.stage);
	  }

	  if(handler instanceof SingleThreadedEventHandlerIF)
	  {
	    synchronized(run_queue_lock)
	    {
	      element.locked = false;
	      run_queue_lock.notifyAll();
	    }
	  }
	} 
	catch(Exception e)
	{
	  System.err.println("TPPThreadManager: appThread ["+name+"] got exception "+e);
	  e.printStackTrace();
	  if(crashOnException)
	  {
	    System.err.println("Sandstorm: Crashing runtime due to exception - goodbye");
	    System.exit(-1);
	  }
	}
      }
    }
  }


  private class StageQueueKey implements Comparable
  {
    private int size;
    private int hashCode;

    public StageQueueKey(int size, int hashCode)
    {
      this.size = size;
      this.hashCode = hashCode;
    }

    public int compareTo(Object o)
    {
      StageQueueKey other_key = (StageQueueKey)o;

      if (size < other_key.size)
	return -1;
      else if (size > other_key.size)
	return 1;
      else // size == other_key.size
      {
	if(hashCode < other_key.hashCode)
	  return -1;
	else if(hashCode > other_key.hashCode)
	  return 1;
	else // hashCode == other_key.hashCode
	  return 0;
      }
    }
  }

  private class StageQueueElement
  {
    public StageWrapperIF stage;
    public boolean locked;
    public StageQueueElement(StageWrapperIF stage, boolean locked)
    {
      this.stage = stage;
      this.locked = locked;
    }

    public boolean equals(Object other)
    {
      if(other == this)
	return true;
      else if( !(other instanceof StageQueueElement) &&
	       !(other instanceof StageWrapperIF))
	return false;
      else 
      {
	StageWrapperIF other_stage = null;
	if(other instanceof StageQueueElement)
	  other_stage = ((StageQueueElement)other).stage;
	else
	  other_stage = (StageWrapperIF)other;

	return(this.stage == other_stage);
      }
    }
      
    public int hashCode()
    {
      return stage.hashCode();
    }
  }

  private class ScheduleLock{}
}

