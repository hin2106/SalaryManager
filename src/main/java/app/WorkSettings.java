package app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class WorkSettings {
    public String shiftStart = "08:00";
    public String shiftEnd = "17:00";
    public String lunchStart = "12:00";
    public String lunchEnd = "13:00";
    public double regularHoursPerDay = 8;
    public String weekdayOtStart = "17:30";
    public String weekdayOtEnd = "20:30";
    public String sundayOtStart = "08:00";
    public String sundayOtEnd = "17:00";
    public double defaultOtHours = 3;
    public double weekdayOtRate = 1.5;
    public double sundayOtRate = 2.0;
    public double holidayOtRate = 3.0;
    public double standardMonthlyHours = 208;
    // 0 = tự động lấy 25% đơn giá giờ (đúng công thức bảng Grand Luise).
    public double shiftAllowance = 0;
    public String dayStart = "08:00";
    public String dayRegularEnd = "17:00";
    public String dayOtEnd = "20:00";
    public String dayExtendedEnd = "22:00";
    public double dayRegularBlockHours = 8;
    public double dayRegularRate = 1.0;
    public double dayOtBlockHours = 3;
    public double dayOtRate = 1.5;
    public double dayExtraRate = 1.0;

    public String nightStart = "20:00";
    public String nightBlock2Start = "22:00";
    public String nightPremiumStart = "05:00";
    public String nightRegularEnd = "06:00";
    public String nightEnd = "08:00";
    public double nightBlock1Hours = 2;
    public double nightBlock1Rate = 1.2;
    public double nightBlock2Hours = 6;
    public double nightBlock2Rate = 1.3;
    public double nightPremiumHours = 1;
    public double nightPremiumRate = 2.0;
    public double nightExtraRate = 1.5;

    public double sundayDayBaseHours = 11;
    public double sundayDayRate = 2.0;
    public double sundayDayExtraRate = 1.5;
    public double sundayNightBlock1Hours = 2;
    public double sundayNightBlock1Rate = 2.0;
    public double sundayNightBlock2Hours = 7;
    public double sundayNightBlock2Rate = 2.5;
    public double sundayNightExtraRate = 2.0;
    public int fixedWorkDays = 0;
    public Set<Integer> workDays = new LinkedHashSet<>(List.of(1,2,3,4,5,6));
    public Set<String> extraHolidays = new LinkedHashSet<>();

    public boolean isWorkDay(DayOfWeek day) { return workDays.contains(day.getValue()); }
    public boolean isHoliday(LocalDate date) {
        int month = date.getMonthValue(), day = date.getDayOfMonth();
        return (month == 1 && day == 1) || (month == 4 && day == 30)
                || (month == 5 && day == 1) || (month == 9 && day == 2)
                || extraHolidays.contains(date.toString());
    }
}
