package app;

import java.math.BigDecimal;

public record Employee(long id, String code, String name, String department, String position,
                       BigDecimal baseSalary, BigDecimal allowance, int dependents, boolean active) {
    @Override public String toString() { return code + " · " + name; }
}
