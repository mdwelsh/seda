import java.util.Enumeration;
import java.util.Hashtable;

public class StatsGatherer {

  private Hashtable histogram;
  private int bucketSize;
  private String name;
  private String tag;
  private double mean = -1;

  private int skipSamples;
  private int skip = 0;
  public int num = 0;
  public long maxVal = 0;
  public long cumulativeVal = 0;

  StatsGatherer(String name, String tag, int bucketSize, int skipSamples) {
    this.name = name;
    this.tag = tag;
    this.bucketSize = bucketSize;
    this.skipSamples = skipSamples;
    if (bucketSize != 0) {
      histogram = new Hashtable(1);
    }
  }

  public synchronized void add(long val) {
    if (skip < skipSamples) {
      skip++;
      return;
    }

    num++;

    if (val > maxVal) maxVal = val;
    cumulativeVal += val;

    if (bucketSize != 0) {
      Integer ct = new Integer((int)val / bucketSize);
      Integer bval = (Integer)histogram.remove(ct);

      if (bval == null) {
	histogram.put(ct, new Integer(1));
      } else {
	bval = new Integer(bval.intValue() + 1);
	histogram.put(ct, bval);
      }
    }
  }

  synchronized void dumpHistogram() {
    Enumeration e = histogram.keys();
    while (e.hasMoreElements()) {
      Integer bucket = (Integer)e.nextElement();
      int time = bucket.intValue() * bucketSize;
      int val = ((Integer)histogram.get(bucket)).intValue();
      System.err.println(tag+" "+time+" ms "+val+" count");
    }
    System.err.println("\n");
  }

  double mean() {
    if (num == 0) return 0.0;
    return (cumulativeVal * 1.0)/num;
  }

}

