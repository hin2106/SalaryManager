package app;

import java.time.LocalDate;

public class AttendanceDay {
    public String date;
    public AttendanceStatus status;
    public ShiftType shiftType;
    public double regularHours;
    public double overtimeHours;
    public String note;

    public AttendanceDay() {}
    public AttendanceDay(LocalDate date, AttendanceStatus status, double regularHours,
                         double overtimeHours, String note) {
        this.date = date.toString();
        this.status = status;
        this.shiftType = date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                ? ShiftType.SUNDAY_DAY : ShiftType.DAY;
        this.regularHours = regularHours;
        this.overtimeHours = overtimeHours;
        this.note = note;
    }
    public LocalDate localDate() { return LocalDate.parse(date); }
}
