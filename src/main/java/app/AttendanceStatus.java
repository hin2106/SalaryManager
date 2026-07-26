package app;

public enum AttendanceStatus {
    WORK("Làm bình thường", "L"),
    SUNDAY_WORK("Làm ngày nghỉ", "CN"),
    PAID_LEAVE("Nghỉ phép", "P"),
    UNPAID_LEAVE("Nghỉ không lương", "NL"),
    HOLIDAY("Nghỉ lễ", "LỄ"),
    HALF_DAY("Nghỉ nửa ngày", "1/2"),
    NO_OT("Làm, không tăng ca", "KOT"),
    OFF("Ngày nghỉ", "N");

    private final String label;
    private final String shortLabel;
    AttendanceStatus(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }
    public String label() { return label; }
    public String shortLabel() { return shortLabel; }
    @Override public String toString() { return label; }
}
