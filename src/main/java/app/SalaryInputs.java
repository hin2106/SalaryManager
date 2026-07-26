package app;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class SalaryInputs {
    public BigDecimal baseSalary = BigDecimal.ZERO;
    /** Giữ để đọc hồ sơ cũ; hồ sơ mới dùng hai đơn giá bên dưới. */
    public BigDecimal hourlyRate = BigDecimal.ZERO;
    public BigDecimal dayHourlyRate = BigDecimal.ZERO;
    public BigDecimal nightHourlyRate = BigDecimal.ZERO;
    public Map<String, BigDecimal> fixedIncome = defaults(
            "Phụ cấp", "Chuyên cần", "Đời sống", "Thâm niên / năm",
            "Xăng xe", "Nhà ở", "Điện thoại", "Hỗ trợ khác");
    public Map<String, BigDecimal> variableIncome = defaults(
            "Thưởng", "Thưởng năng suất", "Thưởng KPI", "Thưởng lễ", "Thưởng khác");
    public Map<String, BigDecimal> deductions = defaults("Tạm ứng", "Phạt", "Trừ khác");

    private static Map<String, BigDecimal> defaults(String... names) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (String name : names) values.put(name, BigDecimal.ZERO);
        return values;
    }
}
