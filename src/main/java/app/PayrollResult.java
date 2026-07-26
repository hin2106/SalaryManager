package app;

import java.math.BigDecimal;

public record PayrollResult(BigDecimal regularSalary, BigDecimal overtimePay,
        BigDecimal grossIncome, BigDecimal insurance, BigDecimal taxableIncome,
        BigDecimal personalIncomeTax, BigDecimal otherDeduction, BigDecimal netSalary) {}
