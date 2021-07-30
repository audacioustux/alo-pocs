class TimeDiff {
    public enum DiffType {
        NS, US
    };

    public DiffType diffType;
    public long elapse;
    public String label;

    public TimeDiff(String label, DiffType diffType) {
        this.label = label;
        this.diffType = diffType;
        this.elapse = current();
    }

    public long current() {
        long nanotime = System.nanoTime();
        return (diffType == DiffType.NS) ? nanotime : nanotime / 1000;
    }

    public void stop() {
        System.out.println(String.format("%s : %s %s", label, current() - elapse, diffType));
    }
}
