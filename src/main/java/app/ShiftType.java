package app;

public enum ShiftType {
    DAY("Ca ngày"),
    NIGHT("Ca đêm"),
    SUNDAY_DAY("Chủ nhật ngày"),
    SUNDAY_NIGHT("Chủ nhật đêm");

    private final String label;
    ShiftType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
