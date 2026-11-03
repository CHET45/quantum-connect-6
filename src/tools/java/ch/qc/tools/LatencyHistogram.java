package ch.qc.tools;

/** Call durations in 10-microsecond buckets up to 2 s; thread-safe, mergeable. */
public final class LatencyHistogram {
  private static final int BUCKET_NANOS = 10_000;
  private final long[] buckets = new long[200_001];
  private long count;
  private long maxNanos;

  public synchronized void record(long nanos) {
    int b = (int) Math.min(buckets.length - 1, Math.max(0, nanos / BUCKET_NANOS));
    buckets[b]++;
    count++;
    maxNanos = Math.max(maxNanos, nanos);
  }

  public synchronized void merge(LatencyHistogram o) {
    synchronized (o) {
      for (int i = 0; i < buckets.length; i++) {
        buckets[i] += o.buckets[i];
      }
      count += o.count;
      maxNanos = Math.max(maxNanos, o.maxNanos);
    }
  }

  public synchronized long count() {
    return count;
  }

  /** Upper edge of the bucket holding quantile q, in milliseconds. */
  public synchronized double quantileMillis(double q) {
    if (count == 0) {
      return 0;
    }
    long rank = (long) Math.ceil(q * count);
    long seen = 0;
    for (int i = 0; i < buckets.length; i++) {
      seen += buckets[i];
      if (seen >= rank) {
        return (i + 1) * BUCKET_NANOS / 1e6;
      }
    }
    return maxNanos / 1e6;
  }

  public synchronized double maxMillis() {
    return maxNanos / 1e6;
  }

  public String summary() {
    return String.format(java.util.Locale.ROOT, "n=%d p50=%.2fms p99=%.2fms p99.99=%.2fms max=%.2fms",
        count(), quantileMillis(0.5), quantileMillis(0.99), quantileMillis(0.9999), maxMillis());
  }
}
