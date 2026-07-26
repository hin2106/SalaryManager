package app;

import java.util.ArrayList;
import java.util.List;

public class SalaryDocument {
    public long employeeId;
    public String employeeName;
    public String documentName;
    public String from;
    public String to;
    public SalaryInputs inputs = new SalaryInputs();
    public List<AttendanceDay> attendance = new ArrayList<>();
}
