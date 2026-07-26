package app;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PayrollCalculator {
    private static final BigDecimal PERSONAL_DEDUCTION = new BigDecimal("15500000");
    private static final BigDecimal DEPENDENT_DEDUCTION = new BigDecimal("6200000");
    private PayrollCalculator() {}

    public static PayrollResult calculate(Employee employee, BigDecimal standardDays,
            BigDecimal workedDays, BigDecimal overtimeHours, BigDecimal bonus,
            BigDecimal otherDeduction) {
        nonNegative(standardDays, "Ngày công chuẩn");
        nonNegative(workedDays, "Ngày công thực tế");
        nonNegative(overtimeHours, "Giờ tăng ca");
        nonNegative(bonus, "Thưởng");
        nonNegative(otherDeduction, "Khấu trừ khác");
        if (standardDays.signum() == 0) throw new IllegalArgumentException("Ngày công chuẩn phải lớn hơn 0.");

        BigDecimal regular = employee.baseSalary().multiply(workedDays)
                .divide(standardDays, 0, RoundingMode.HALF_UP);
        BigDecimal hourly = employee.baseSalary()
                .divide(standardDays.multiply(new BigDecimal("8")), 8, RoundingMode.HALF_UP);
        BigDecimal overtime = hourly.multiply(new BigDecimal("1.5")).multiply(overtimeHours)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal gross = regular.add(employee.allowance()).add(overtime).add(bonus);
        BigDecimal insurance = employee.baseSalary().multiply(new BigDecimal("0.105"))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal taxable = gross.subtract(insurance).subtract(PERSONAL_DEDUCTION)
                .subtract(DEPENDENT_DEDUCTION.multiply(BigDecimal.valueOf(employee.dependents())))
                .max(BigDecimal.ZERO);
        BigDecimal tax = progressiveTax(taxable);
        return new PayrollResult(regular, overtime, gross, insurance, taxable, tax,
                otherDeduction, gross.subtract(insurance).subtract(tax).subtract(otherDeduction));
    }

    static BigDecimal progressiveTax(BigDecimal income) {
        // Biểu thuế 5 bậc áp dụng từ kỳ tính thuế 2026.
        long[] limits = {10_000_000L, 30_000_000L, 60_000_000L, 100_000_000L};
        String[] rates = {"0.05", "0.10", "0.20", "0.30", "0.35"};
        BigDecimal remaining = income, previous = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (int i = 0; i < rates.length; i++) {
            BigDecimal limit = i < limits.length ? BigDecimal.valueOf(limits[i]) : income;
            BigDecimal taxableBand = remaining.min(limit.subtract(previous)).max(BigDecimal.ZERO);
            tax = tax.add(taxableBand.multiply(new BigDecimal(rates[i])));
            remaining = remaining.subtract(taxableBand);
            previous = limit;
            if (remaining.signum() <= 0) break;
        }
        return tax.setScale(0, RoundingMode.HALF_UP);
    }

    private static void nonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(label + " không được âm.");
    }
}
