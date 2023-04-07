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

package seda.sandStorm.main;

import seda.sandStorm.api.SandstormConfigIF;
import seda.sandStorm.api.StageNameAlreadyBoundException;

import java.io.*;
import java.util.*;

/**
 * This class is used to pass configuration parameters into Sandstorm
 * at startup time. It reads initial configuration parameters from a
 * file, using an XML-like format. Various operations can be performed
 * upon this class to modify the configuration of the Sandstorm runtime.
 *
 * @author Matt Welsh
 * @see Sandstorm
 * @see Main
 *
 */
public class SandstormConfig implements Cloneable,SandstormConfigIF {

  private static final boolean DEBUG = false;
  private static final String DELIM_CHAR = ".";
  private static final int MAX_DIRECTIVE_LENGTH = 80;
  public static final String LIST_ELEMENT_DELIMITER = " ";

  private configSection root;
  private Hashtable stages;

  /** Value for defaultThreadMgr to use the thread-per-CPU thread manager. */
  public static final String THREADMGR_TPPTM = "TPPTM";
  /** Value for defaultThreadMgr to use the thread-per-stage thread manager. */
  public static final String THREADMGR_TPSTM = "TPSTM";
  /** Value for defaultThreadMgr to use the aggregating TPSTM. */
  public static final String THREADMGR_AggTPSTM = "AggTPSTM";

  /** String value for setting boolean configuration entries to true. */
  public static final String CONFIG_TRUE = "true";
  /** String value for setting boolean configuration entries to false. */
  public static final String CONFIG_FALSE = "false";

  /** 
   * The set of default values for the Sandstorm configuration.
   * In order to modify the default configuration used by Sandstorm,
   * edit SandstormConfig.java and recompile.
   */
  public static final String[] defaults = {
    "global.defaultThreadManager", THREADMGR_TPSTM,

    "global.threadPool.initialThreads", "1",
    "global.threadPool.minThreads", "1",
    "global.threadPool.maxThreads", "20",
    "global.threadPool.blockTime", "1000",
    "global.threadPool.sizeController.enable", CONFIG_FALSE,
    "global.threadPool.sizeController.delay", "2000",
    "global.threadPool.sizeController.threshold", "1000", 
    "global.threadPool.sizeController.idleTimeThreshold", "1000",

    "global.batchController.enable", CONFIG_FALSE,
    "global.batchController.minBatch", "1",
    "global.batchController.maxBatch", "-1",

    "global.profile.enable", CONFIG_FALSE,
    "global.profile.delay", "1000",
    "global.profile.filename", "sandstorm-profile.txt",
    "global.profile.sockets", CONFIG_FALSE,
    "global.profile.graph", CONFIG_FALSE,
    "global.profile.graphfilename", "sandstorm-graph.txt",

    /* Deprecated */
    "global.AggTPSTM.governor.enable", CONFIG_FALSE,
    "global.AggTPSTM.governor.delay", "2000",
    "global.AggTPSTM.governor.threshold", "1000", 

    /* Deprecated */
    "global.TPPTM.numCpus", "1",
    "global.TPPTM.maxThreads", "1",

    "global.aSocket.enable", CONFIG_TRUE,
    "global.aSocket.provider", "NBIO",
    "global.aSocket.rateController.enable", CONFIG_FALSE,
    "global.aSocket.rateController.rate", "100000.0",

    "global.aDisk.enable", CONFIG_FALSE,
    "global.aDisk.threadPool.initialThreads", "1",
    "global.aDisk.threadPool.minThreads", "1",
    "global.aDisk.threadPool.maxThreads", "20",
    "global.aDisk.threadPool.sizeController.enable", CONFIG_TRUE,
    "global.aDisk.threadPool.sizeController.delay", "2000",
    "global.aDisk.threadPool.sizeController.threshold", "20",
  };

  private Hashtable cmdLineArgs;

  /** Default initialization arguments passed to every stage. */
  public Hashtable defaultInitArgs;

  /**
   * Create a new SandstormConfig with the default settings.
   */
  public SandstormConfig() {
    stages = new Hashtable(1);
    root = new configSection("sandstorm");

    // Set default values
    for (int i = 0; i < defaults.length; i+=2) {
      String key = defaults[i];
      String val = defaults[i+1];
      if (getString(key) == null) {
	putString(key, val);
      }
    }
  }

  /**
   * Create a new SandstormConfig with the default settings, with
   * the given default init args, which will be passed to every stage.
   * Each element of defaultArgs[] is a String in the format
   * "key=value". If "key" contains a dot ("."), then it will be
   * treated as a key to be added to the Sandstorm configuration's
   * global parameters. Otherwise, the key-value pair will be added to 
   * the "initargs" section for each stage.
   *
   */
  public SandstormConfig(String defaultArgs[]) throws IOException {
    this();
    if (defaultArgs != null) this.cmdLineArgs = stringArrayToHT(defaultArgs);
  }

  /**
   * Create a new SandstormConfig, reading the configration from the
   * given file. The configuration file uses an XML-like structure;
   * see the Sandstorm documentation for more information on its format.
   */
  public SandstormConfig(String fname) throws IOException {
    this();
    readFile(fname);
  }

  /**
   * Create a new SandstormConfig, reading the configration from the
   * given file. The configuration file uses an XML-like structure;
   * see the Sandstorm documentation for more information on its format.
   * 
   * @param defaultInitArgs Default initialization arguments passed to
   *   every stage. These override any arguments found in the config file.
   *   Each element of this array must be a string with the format
   *   <tt>"key=value"</tt>.
   */
  public SandstormConfig(String fname, String defaultArgs[]) throws IOException {
    this(defaultArgs);
    readFile(fname);
  }

  // Return the value associated with the given key in cs; recursive.
  private String getVal(configSection cs, String key) {
    String car, cdr;
    int c = key.indexOf(DELIM_CHAR);
    if (c == -1) { 
      car = key; cdr = null; 
    } else {
      car = key.substring(0, c);
      cdr = key.substring(c+1, key.length());
    }
    if (DEBUG) System.err.println("getVal: cs="+cs+" key="+key+" car="+car+", cdr="+cdr);

    if (cdr == null) {
      // OK, we are at a terminal node
      return cs.getVal(car);
    } else {
      // We are at an intermediate node
      configSection subsec = cs.getSubsection(car);
      if (subsec == null) return null;
      else return getVal(subsec, cdr);
    }
  }

  // Set the given value in cs; recursive.
  private void putVal(configSection cs, String key, String val) {
    String car, cdr;
    int c = key.indexOf(DELIM_CHAR);
    if (c == -1) { 
      car = key; cdr = null; 
    } else {
      car = key.substring(0, c);
      cdr = key.substring(c+1, key.length());
    }

    if (cdr == null) {
      // OK, we are at a terminal node
      cs.putVal(key, val);
      return;
    } else {
      // We are at an intermediate node
      configSection subsec = cs.getSubsection(car);
      if (subsec == null) {
	subsec = new configSection(car);
	cs.addSubsection(subsec);
      }
      putVal(subsec, cdr, val);
      return;
    }
  }

  /**
   * Return the configuration option associated with the given key
   * as a String. Returns null if not set.
   */
  public String getString(String key) {
    return getString(key, null);
  }

  /**
   * Return the configuration option associated with the given key
   * as a String. Returns default if not set.
   */
  public String getString(String key, String defaultval) {
    String val = getVal(root, key);
    if (val == null) return defaultval;
    else return val;
  }

  /**
   * Set the given configuration option specified as a String.
   */
  public void putString(String key, String val) {
    putVal(root, key, val);
  }

  /**
   * Return the configuration option associated with the given key
   * as a boolean. Returns false if not set.
   */
  public boolean getBoolean(String key) {
    return getBoolean(key, false);
  }

  /**
   * Return the configuration option associated with the given key
   * as a boolean. Returns default if not set.
   */
  public boolean getBoolean(String key, boolean defaultval) {
    String val = getVal(root, key);
    if (val == null) return defaultval;
    if (val.equals("true") || val.equals("TRUE")) return true;
    else return false;
  }

  /**
   * Set the given configuration option specified as a boolean.
   */
  public void putBoolean(String key, boolean val) {
    putVal(root, key, (val)?(CONFIG_TRUE):(CONFIG_FALSE));
  }

  /**
   * Return the configuration option associated with the given key
   * as an int. Returns -1 if not set or if the value of the key cannot
   * be expressed as an int.
   */
  public int getInt(String key) {
    return getInt(key, -1);
  }

  /**
   * Return the configuration option associated with the given key
   * as an int. Returns default if not set or if the value of the
   * key cannot be expressed as an int.
   */
  public int getInt(String key, int defaultval) {
    String val = getVal(root, key);
    if (val == null) return defaultval;
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException nfe) {
      return defaultval;
    }
  }

  /**
   * Set the given configuration option specified as an int.
   */
  public void putInt(String key, int val) {
    putVal(root, key, new Integer(val).toString());
  }

  /**
   * Return the configuration option associated with the given key
   * as a double. Returns -1 if not set or if the value of the key cannot
   * be expressed as a double.
   */
  public double getDouble(String key) {
    return getDouble(key, -1);
  }

  /**
   * Return the configuration option associated with the given key
   * as a double. Returns default if not set or if the value of the
   * key cannot be expressed as a double.
   */
  public double getDouble(String key, double defaultval) {
    String val = getVal(root, key);
    if (val == null) return defaultval;
    try {
      return new Double(val).doubleValue();
    } catch (NumberFormatException nfe) {
      return defaultval;
    }
  }

  /**
   * Get the string list value corresponding to the given key.
   * Returns null if not set.
   */
  public String[] getStringList(String key) {
    String ret[];
    String val = (String)getVal(root, key);
    if (val == null) return null;
    StringTokenizer st = new StringTokenizer(val, SandstormConfig.LIST_ELEMENT_DELIMITER);
    Vector v = new Vector(1);
    while (st.hasMoreElements()) {
      v.addElement(st.nextElement());
    }
    ret = new String[v.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = (String)v.elementAt(i);
    }
    return ret;
  }

  /**
   * Set the given configuration option specified as an int.
   */
  public void putDouble(String key, double val) {
    putVal(root, key, new Double(val).toString());
  }

  /**
   * Set the given key to the given string list value.
   */
  public void puttStringList(String key, String valarr[]) {
    String s = "";
    for (int i = 0; i < valarr.length; i++) {
      s += valarr[i];
      if (i != valarr.length-1) s += SandstormConfig.LIST_ELEMENT_DELIMITER;
    }
    putVal(root, key, s);
  }

  // Return enumeration of keys matching prefix starting with cs.
  // Recursive.
  private Enumeration getKeys(configSection cs, String prefix) {
      if(DEBUG)System.err.println("SandstormConfig.getKeys: cs = " + cs +
				  "; prefix = " + prefix);
    // We are at the end of the prefix
    if (prefix == null) {
      Vector v = new Vector(1);
      Enumeration e = cs.getKeys();
      if (e != null) {
	while (e.hasMoreElements()) {
	  v.addElement(e.nextElement());
	}
      }
      e = cs.getSubsections();
      if (e != null) {
	while (e.hasMoreElements()) {
	  configSection subsec = (configSection)e.nextElement();
	  v.addElement(subsec.getName()+DELIM_CHAR);
	}
      }
      return v.elements();
    }

    // First look for single item matching prefix
    int dlimx = prefix.indexOf(DELIM_CHAR);
    if (dlimx < 0) {
	String val = cs.getVal(prefix);
	if (val != null) {
	    Vector v = new Vector(1);
	    v.addElement(prefix);
	    return v.elements();
	}
    }
    // Look for subsection matching prefix
    String tok = dlimx < 0 ? prefix : prefix.substring(0, dlimx);
    configSection subsec = cs.getSubsection(tok);
    if (subsec == null)
	return null;
    String tok2 = dlimx < 0 ? null : prefix.substring(dlimx+1);
    return getKeys(subsec, tok2);
  }

  /**
   * Return an enumeration of the keys matching the given prefix.
   * A given key maps onto a set of child keys if it ends in a
   * "." character (that is, it is an internal node within the tree).
   * A key not ending in "." is a terminal node and maps onto a
   * value that may be obtained using getString, getInt, or getDouble.
   */
  public Enumeration getKeys(String prefix) {
    return getKeys(root, prefix);
  }

  /**
   * Return an enumeration of the top-level keys in this configuration.
   */
  public Enumeration getKeys() {
    return getKeys(root, null);
  }

  /**
   * Return a copy of this object.
   */
  public SandstormConfig getCopy() {
    try {
      return (SandstormConfig)(this.clone());
    } catch (CloneNotSupportedException e) {
      throw new Error("Internal error: SandstormConfig must support clone!");
    }
  }

  /** 
   * Add a stage to this SandstormConfig. 
   *
   * @param stageName The name of the stage as it should be registered.
   * @param className The fully-qualified class name of the stage event
   *  handler.
   * @param initargs The initial arguments to pass into the stage.
   */
  public void addStage(String stageName, String className, String initargs[])
  throws StageNameAlreadyBoundException, IOException {
    if (stages.get(stageName) != null) {
      throw new StageNameAlreadyBoundException("Stage "+stageName+" already registered in SandstormConfig");
    }

    stageDescr descr = new stageDescr();
    descr.stageName = stageName;
    descr.className = className;
    descr.initargs = stringArrayToHT(initargs);
    stages.put(stageName, descr);
  }

  /**
   * Return an Enumeration of the stages specified by this SandstormConfig.
   */
  public Enumeration getStages() {
    return stages.elements();
  }

  /**
   * Read the configuration from the given file.
   */
  public void readFile(String fname) throws IOException {

    Reader in = new directiveReader(new BufferedReader(new FileReader(fname)));
    root = new configSection(in);

    configSection global_initargs = null;

    if (!root.getName().equals("sandstorm")) throw new IOException("Outermost section config file named "+root.getName()+", expecting sandstorm");

    // Set default values
    for (int i = 0; i < defaults.length; i+=2) {
      String key = defaults[i];
      String val = defaults[i+1];
      if (getString(key) == null) {
	putString(key, val);
      }
    }

    // Set command line values
    this.defaultInitArgs = new Hashtable();
    if (cmdLineArgs != null) {
      Enumeration e = cmdLineArgs.keys();
      while (e.hasMoreElements()) {
	String key = (String)e.nextElement();
	if (key.indexOf('.') != -1) {
	  putString(key, (String)cmdLineArgs.get(key));
	} else {
	  this.defaultInitArgs.put(key, (String)cmdLineArgs.get(key));
	}
      }
    }

    if (DEBUG) {
      System.err.println("DOING DUMP: -----------------------");
      root.dump();
      System.err.println("DONE WITH DUMP ---------------------");
    }

    // Get global init args
    configSection global = root.getSubsection("global");
    if (global != null) {
      global_initargs = global.getSubsection("initargs");
    }

    // Get stages
    if (DEBUG) System.err.println("Parsing stages");
    configSection stagesec = root.getSubsection("stages");
    if (stagesec != null) {
      for (int i = 0; i < stagesec.subsections.size(); i++) {
	configSection sec = (configSection)stagesec.subsections.elementAt(i);
	stageDescr descr = new stageDescr();
	descr.stageName = sec.getName();
	descr.className = sec.getVal("class");
	if (descr.className == null) throw new IOException("Missing class name in <stage> section of config file");
        if (DEBUG) System.err.println("Parsing stage "+descr.stageName);

      	descr.initargs = new Hashtable(1);

	// Add global args
	if (global_initargs != null) {
	  Enumeration e2 = global_initargs.getKeys();
	  while (e2.hasMoreElements()) {
	    String key = (String)e2.nextElement();
	    String val = global_initargs.getVal(key);
	    descr.initargs.put(key, val);
	  }
	}

	// Add stage-specific args
	configSection args = sec.getSubsection("initargs");
	if (args != null) {
	  Enumeration e2 = args.getKeys();
	  while (e2.hasMoreElements()) {
	    String key = (String)e2.nextElement();
	    String val = args.getVal(key);
	    descr.initargs.put(key,val);
	  }
	}

	// Add defaultInitArgs
	if (defaultInitArgs != null) {
	  Enumeration e2 = defaultInitArgs.keys();
	  while (e2.hasMoreElements()) {
	    String key = (String)e2.nextElement();
	    String val = (String)defaultInitArgs.get(key);
	    descr.initargs.put(key, val);
	  }
	}

	try {
	  String val = sec.getVal("queueThreshold");
	  if (val == null) descr.queueThreshold = -1;
	  else descr.queueThreshold = Integer.parseInt(val);
	} catch (NumberFormatException ne) {
	  descr.queueThreshold = -1;
	}
	if (DEBUG) System.err.println("Adding stage "+descr.stageName);
        stages.put(descr.stageName, descr);
      }
    }
  }


  // ----------------------------------------------------------------------

  // Convert an array of "key=value" strings to a Hashtable
  private Hashtable stringArrayToHT(String arr[]) throws IOException {
    if (arr == null) return null;
    Hashtable ht = new Hashtable(1);
    for (int i = 0; i < arr.length; i++) {
      StringTokenizer st = new StringTokenizer(arr[i], "=");
      String key;
      String val;
      try {
	key = st.nextToken();
	val = st.nextToken();
	while (st.hasMoreTokens()) val += "="+st.nextToken();
      } catch (NoSuchElementException e) {
	throw new IOException("Could not convert string '"+arr[i]+"' to key=value pair");
      }
      ht.put(key, val);
    }
    return ht;
  }

  // Internal class to represent configuration file format
  class configSection {
    private String secname;
    private StreamTokenizer tok;
    private Vector subsections;
    private Hashtable vals;

    private configSection() {
      subsections = new Vector(1);
      vals = new Hashtable(1);
    }

    configSection(Reader in) throws IOException {
      this();
      tok = new StreamTokenizer(in);
      tok.resetSyntax();
      tok.wordChars((char)0, (char)255);
      tok.whitespaceChars('\u0000', '\u0020');
      tok.commentChar('#');
      tok.eolIsSignificant(true);
      doRead();
    }

    private configSection(StreamTokenizer tok) throws IOException {
      this();
      this.tok = tok;
      tok.pushBack();
      tok.wordChars('0','9');
      doRead();
    }

    configSection(String name) {
      this();
      this.secname = name;
    }

    String getName() {
      return secname;
    }

    configSection getSubsection(String name) {
      for (int i = 0; i < subsections.size(); i++) {
	configSection sec = (configSection)subsections.elementAt(i);
	if (sec.getName().equals(name)) return sec;
      }
      return null;
    }

    void addSubsection(configSection subsec) {
      subsections.addElement(subsec);
    }

    // Return the string associated with key in this section
    // If not specified, null is returned
    String getVal(String key) {
      return (String)vals.get(key);
    }

    Enumeration getSubsections() {
      if (subsections == null) return null;
      return subsections.elements();
    }

    Enumeration getKeys() {
      return vals.keys();
    }

    int numKeys() {
      return vals.size();
    }

    Hashtable getVals() {
      return vals;
    }

    void putVal(String key, String val) {
      vals.put(key, val);
    }

    // Read next section name, parse recursively until we see the
    // end of that section
    private void doRead() throws IOException {
      String word, key, value;
      boolean read_secname = false;

      // Get initial section name
      word = nextWord();
      if (word.startsWith("<") && word.endsWith(">")) {
	secname = word.substring(1,word.length()-1);
      } else {
	throw new IOException("No section name found at line "+tok.lineno()+" of config file, read "+word);
      }

      boolean done = false;
      while (!done) {

	key = null;
	while (true) {
	  // Read key
	  word = nextWord();
	  if (word.startsWith("<") && word.endsWith(">")) {
	    String val = word.substring(1,word.length()-1);
	    if (val.equals("/"+secname)) {
	      // Done reading this section
	      done = true; break;
	    } else {
	      // Found a new section; recurse
	      configSection subsec = new configSection(tok);
	      if (getSubsection(subsec.getName()) != null) {
		throw new IOException("subsection "+subsec.getName()+" redefined at line "+tok.lineno()+" of config file");
	      }
	      if (vals.get(subsec.getName()) != null) {
		throw new IOException("subsection "+subsec.getName()+" conflicts with key "+subsec.getName()+" at line "+tok.lineno()+" of config file");
	      }

	      subsections.addElement(subsec);
	    }
	  } else {
	    key = word;
	    break;
	  }
	}

	if (done) break;

	// Read value
        word = nextLine();
        if (word.startsWith("<") && word.endsWith(">")) {
	  // Bad format: Should not have section tag here
	  throw new IOException("Unexpected section tag "+word+" on line "+tok.lineno()+" of config file");
	} else {
	  value = word;
	}

	if (key == null) throw new IOException("key is null at line "+tok.lineno()+" of config file");
	if (vals.get(key) != null) {
	  throw new IOException("key "+key+" redefined at line "+tok.lineno()+" of config file");
	}
	if (getSubsection(key) != null) {
	  throw new IOException("key "+key+" conflicts with subsection "+key+" at line "+tok.lineno()+" of config file");
	}
	if (key.indexOf(DELIM_CHAR) != -1) {
	  throw new IOException("key "+key+" may not contain character '"+DELIM_CHAR+"' at line "+tok.lineno()+" of config file");
	}
	vals.put(key,value);
      }

    }

    // Read next whitespace-delimited word from tok
    private String nextWord() throws IOException {
      while (true) {
	int type = tok.nextToken();
	switch (type) {

	  case StreamTokenizer.TT_EOL:
	    continue;

	  case StreamTokenizer.TT_EOF: 
	    throw new EOFException("EOF in config file");

	  case StreamTokenizer.TT_WORD:
	    if (DEBUG) System.err.println("nextWord returning "+tok.sval);
	    return tok.sval;

	  case StreamTokenizer.TT_NUMBER:
	    if (DEBUG) System.err.println("nextWord returning number");
	    return Double.toString(tok.nval);

	  default: 
	    continue;
	}
      }
    }

    // Read rest of line from tok
    private String nextLine() throws IOException {
      String line = new String("");
      boolean first = true;

      while (true) {
	switch (tok.nextToken()) {

	  case StreamTokenizer.TT_EOL:
	    if (DEBUG) System.err.println("nextLine returning "+line);
	    return line;

	  case StreamTokenizer.TT_EOF: 
	    throw new EOFException("EOF in config file");

	  case StreamTokenizer.TT_WORD:
	    if (first) {
	      line = tok.sval;
	      first = false;
            } else {
	      line += " "+tok.sval;
	    }
	    break;

	  case StreamTokenizer.TT_NUMBER:
	    if (first) {
	      line = Double.toString(tok.nval); 
	      first = false;
	    } else {
	      line += " "+Double.toString(tok.nval);
	    }
	    break;

	  default: 
	    continue;
	}
      }
    }

    // Debugging only
    void dump() {
      System.err.println("<"+secname+">");
      Enumeration e = vals.keys();
      while (e.hasMoreElements()) {
	String key = (String)e.nextElement();
	String val = (String)vals.get(key);
	System.err.println("   "+key+" "+val);
      }

      for (int i = 0; i < subsections.size(); i++) {
	configSection sec = (configSection)subsections.elementAt(i);
	sec.dump();
      }
      System.err.println("</"+secname+">");
    }

    public String toString() {
      return "configSection <"+secname+">";
    }
  }

  /** 
   * Internal class to preprocess special directives in the 
   * config file.
   */
  class directiveReader extends Reader {
    private Reader under, includedFile, markStream;
    private boolean markIsIncluded = false, closed = false;
    private boolean inComment = false;

    directiveReader(Reader under) throws IOException {
      this.under = under;
      if (!under.markSupported()) {
	throw new IOException("SandstormConfig: Internal error: directiveReader.under must support mark() -- contact mdw@cs.berkeley.edu");
      }
    }

    public int read() throws IOException {
      if (closed) throw new IOException("directiveReader is closed");
      if (includedFile != null) {
	int ret = includedFile.read();
	if (ret == -1) includedFile = null;
	else return ret;
      }

      boolean done = false;

      while (!done) {

	int c = under.read();

	// Ignore special directives inside of comments
	if (c == '#') {
	  inComment = true;
	}
	if (c == '\n') {
	  inComment = false;
	}

	if (!inComment && (c == '<')) {
	  under.mark(100);
	  if (under.read() == '!') {
	    // Process special directive; read until '>'
	    String directive = "<!";
	    char c1 = ' ';
	    while (c1 != '>') {
	      try {
		c1 = (char)under.read();
		if (c1 == -1) throw new IOException("End of file");
	      } catch (IOException ioe) {
		throw new IOException("SandstormConfig: Unterminated directive "+directive.substring(0,Math.min(directive.length(),10))+" in configuration file");
	      }
	      directive += c1;
	    }
	    if (DEBUG) System.err.println("Got special directive: "+directive);

	    if (directive.startsWith("<!include")) {
	      StringTokenizer st = new StringTokenizer(directive);
	      String dir = st.nextToken();
	      String fname = st.nextToken();
	      fname = fname.substring(0, fname.length()-1).trim();
	      if (DEBUG) System.err.println("Including file: "+fname);
	      includedFile = new directiveReader(new BufferedReader(new FileReader(fname)));
	      int ret = includedFile.read();
	      if (ret == -1) { 
		includedFile = null;
		continue;
	      } else {
		return ret;
	      }
	    } else {
	      throw new IOException("SandstormConfig: Unrecognized directive "+directive+" in config file");
	    }

	  } else {
	    // Got a '<' with no following '!'
	    under.reset();
	    return c;
	  }
	} else {
	  // Got something other than '<'
	  return c; 
	}
      }
      // Should never get here
      return -1;
    }

    public int read(char cbuf[]) throws IOException {
      return read(cbuf, 0, cbuf.length);
    }

    public int read(char cbuf[], int off, int len) throws IOException {
      int n = 0;
      for (int i = off; i < len; i++) {
	int c = read();
	if (cbuf[i] == -1) return n;
	cbuf[i] = (char)c;
	n++;
      }
      return n;
    }

    public long skip(long n) throws IOException {
      if (n < 0) throw new IllegalArgumentException("directiveReader.skip: n must be nonzero");
      long skipped = 0;
      for (long l = n; l >= 0; l--) {
	int c = read();
	if (c == -1) return skipped;
	skipped++;
      }
      return skipped;
    }

    public boolean ready() throws IOException {
      if (includedFile != null) return includedFile.ready();
      return under.ready();
    }

    public boolean markSupported() {
      return true;
    }

    public void mark(int readAheadLimit) throws IOException {
      if (includedFile != null) {
	markStream = includedFile;
	markIsIncluded = true;
      } else {
	markStream = under;
      }
      markStream.mark(readAheadLimit);
    }

    public void reset() throws IOException {
      markStream.reset();
      if (markIsIncluded) includedFile = markStream;
    }

    public void close() throws IOException {
      if (includedFile != null) includedFile.close();
      under.close();
      closed = true;
    }
  }

}
