package app;

import java.math.BigDecimal;

public record SalarySummary(int normalDays, int sundayDays, int leaveDays, int noOtDays,
        double regularHours, double weekdayOtHours, double sundayOtHours, double holidayOtHours,
        BigDecimal hourlyRate, BigDecimal regularPay, BigDecimal overtimePay,
        BigDecimal otherIncome, BigDecimal grossIncome, BigDecimal insurance,
        BigDecimal personalIncomeTax, BigDecimal manualDeductions, BigDecimal totalDeductions,
        BigDecimal netSalary, String formula) {
    public double totalOtHours() { return weekdayOtHours + sundayOtHours + holidayOtHours; }
    public double totalHours() { return regularHours + totalOtHours(); }
}
