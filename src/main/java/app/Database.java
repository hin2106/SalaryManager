package app;

import java.math.BigDecimal;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Database {
    private final String url = "jdbc:sqlite:database/salary.db";

    public Database() {
        try {
            Files.createDirectories(Path.of("database"));
            preserveInvalidDatabase();
        }
        catch (Exception e) { throw new IllegalStateException("Không thể tạo thư mục dữ liệu.", e); }
        initialize();
    }

    /**
     * Một số bản dự án cũ chứa tệp salary.db rỗng hoặc không phải SQLite.
     * Giữ tệp đó dưới dạng bản sao lưu rồi để SQLite tạo tệp mới hợp lệ.
     */
    private void preserveInvalidDatabase() throws Exception {
        Path database = Path.of("database", "salary.db");
        if (!Files.exists(database) || Files.size(database) == 0) return;

        byte[] expected = "SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] actual = new byte[expected.length];
        int bytesRead;
        try (InputStream input = Files.newInputStream(database)) {
            bytesRead = input.read(actual);
        }
        if (bytesRead == expected.length && Arrays.equals(actual, expected)) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = database.resolveSibling("salary.db.invalid-" + timestamp + ".bak");
        Files.move(database, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private Connection connect() throws SQLException {
        Connection connection=DriverManager.getConnection(url);
        try(Statement statement=connection.createStatement()){
            statement.execute("PRAGMA busy_timeout=3000");
        }
        return connection;
    }

    private void initialize() {
        String employees = """
            CREATE TABLE IF NOT EXISTS employees(
              id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
              department TEXT NOT NULL DEFAULT '', position TEXT NOT NULL DEFAULT '',
              base_salary NUMERIC NOT NULL DEFAULT 0, allowance NUMERIC NOT NULL DEFAULT 0,
              dependents INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)
            """;
        String payrolls = """
            CREATE TABLE IF NOT EXISTS payrolls(
              id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL,
              payroll_month TEXT NOT NULL, standard_days NUMERIC NOT NULL, worked_days NUMERIC NOT NULL,
              overtime_hours NUMERIC NOT NULL, bonus NUMERIC NOT NULL, other_deduction NUMERIC NOT NULL,
              gross_income NUMERIC NOT NULL, insurance NUMERIC NOT NULL, personal_income_tax NUMERIC NOT NULL,
              net_salary NUMERIC NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
              UNIQUE(employee_id,payroll_month), FOREIGN KEY(employee_id) REFERENCES employees(id))
            """;
        String settings = """
            CREATE TABLE IF NOT EXISTS app_settings(
              setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL)
            """;
        String documents = """
            CREATE TABLE IF NOT EXISTS salary_documents(
              id INTEGER PRIMARY KEY AUTOINCREMENT, employee_id INTEGER NOT NULL,
              document_name TEXT NOT NULL, date_from TEXT NOT NULL, date_to TEXT NOT NULL,
              payload TEXT NOT NULL, net_salary NUMERIC NOT NULL, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
              UNIQUE(employee_id,document_name), FOREIGN KEY(employee_id) REFERENCES employees(id))
            """;
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA journal_mode=WAL");
            s.execute(employees); s.execute(payrolls);
            s.execute(settings); s.execute(documents); seed(c);
        } catch (SQLException e) { throw new IllegalStateException("Không thể khởi tạo dữ liệu.", e); }
    }

    private void seed(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM employees")) {
            if (r.next() && r.getInt(1) > 0) return;
        }
        String sql = "INSERT INTO employees(code,name,department,position,base_salary,allowance,dependents) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            Object[][] data = {
                {"NV001","Nguyễn Minh Anh","Sản phẩm","Product Designer",22000000,2000000,1},
                {"NV002","Trần Quốc Bảo","Kỹ thuật","Backend Developer",28000000,1500000,0},
                {"NV003","Lê Thu Hà","Vận hành","Chuyên viên nhân sự",18000000,1200000,2}};
            for (Object[] row : data) { for (int i=0;i<row.length;i++) p.setObject(i+1,row[i]); p.addBatch(); }
            p.executeBatch();
        }
    }

    public List<Employee> findEmployees() {
        List<Employee> list = new ArrayList<>();
        try (Connection c=connect(); Statement s=c.createStatement();
             ResultSet r=s.executeQuery("SELECT * FROM employees ORDER BY active DESC,name")) {
            while (r.next()) list.add(new Employee(r.getLong("id"),r.getString("code"),r.getString("name"),
                    r.getString("department"),r.getString("position"),r.getBigDecimal("base_salary"),
                    r.getBigDecimal("allowance"),r.getInt("dependents"),r.getInt("active")==1));
            return list;
        } catch (SQLException e) { throw new IllegalStateException("Không thể đọc nhân viên.",e); }
    }

    public void saveEmployee(Employee e) {
        boolean insert=e.id()==0;
        String sql=insert
            ? "INSERT INTO employees(code,name,department,position,base_salary,allowance,dependents,active) VALUES(?,?,?,?,?,?,?,?)"
            : "UPDATE employees SET code=?,name=?,department=?,position=?,base_salary=?,allowance=?,dependents=?,active=? WHERE id=?";
        try (Connection c=connect(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,e.code()); p.setString(2,e.name()); p.setString(3,e.department());
            p.setString(4,e.position()); p.setBigDecimal(5,e.baseSalary()); p.setBigDecimal(6,e.allowance());
            p.setInt(7,e.dependents()); p.setInt(8,e.active()?1:0); if(!insert)p.setLong(9,e.id()); p.executeUpdate();
        } catch (SQLException x) {
            if (x.getMessage()!=null && x.getMessage().contains("UNIQUE"))
                throw new IllegalArgumentException("Mã nhân viên đã tồn tại.");
            throw new IllegalStateException("Không thể lưu nhân viên.",x);
        }
    }

    public void deleteEmployee(long employeeId) {
        try(Connection c=connect()){
            c.setAutoCommit(false);
            try(PreparedStatement documents=c.prepareStatement("DELETE FROM salary_documents WHERE employee_id=?");
                PreparedStatement payrolls=c.prepareStatement("DELETE FROM payrolls WHERE employee_id=?");
                PreparedStatement employee=c.prepareStatement("DELETE FROM employees WHERE id=?")){
                documents.setLong(1,employeeId);documents.executeUpdate();
                payrolls.setLong(1,employeeId);payrolls.executeUpdate();
                employee.setLong(1,employeeId);
                if(employee.executeUpdate()==0)throw new SQLException("Nhân viên không còn tồn tại.");
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new IllegalStateException("Không thể xóa nhân viên.",e);}
    }

    public void savePayroll(long employeeId,String month,BigDecimal standard,BigDecimal worked,
            BigDecimal overtime,BigDecimal bonus,PayrollResult r) {
        String sql="""
            INSERT INTO payrolls(employee_id,payroll_month,standard_days,worked_days,overtime_hours,
            bonus,other_deduction,gross_income,insurance,personal_income_tax,net_salary)
            VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(employee_id,payroll_month) DO UPDATE SET
            standard_days=excluded.standard_days,worked_days=excluded.worked_days,
            overtime_hours=excluded.overtime_hours,bonus=excluded.bonus,other_deduction=excluded.other_deduction,
            gross_income=excluded.gross_income,insurance=excluded.insurance,
            personal_income_tax=excluded.personal_income_tax,net_salary=excluded.net_salary,created_at=CURRENT_TIMESTAMP
            """;
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(sql)){
            p.setLong(1,employeeId);p.setString(2,month);p.setBigDecimal(3,standard);p.setBigDecimal(4,worked);
            p.setBigDecimal(5,overtime);p.setBigDecimal(6,bonus);p.setBigDecimal(7,r.otherDeduction());
            p.setBigDecimal(8,r.grossIncome());p.setBigDecimal(9,r.insurance());
            p.setBigDecimal(10,r.personalIncomeTax());p.setBigDecimal(11,r.netSalary());p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Không thể lưu bảng lương.",e);}
    }

    public List<PayrollRow> findPayrolls(String month) {
        List<PayrollRow> list=new ArrayList<>();
        String sql="""
            SELECT p.id,e.code,e.name,e.department,p.payroll_month,p.gross_income,p.insurance,
            p.personal_income_tax,p.other_deduction,p.net_salary FROM payrolls p JOIN employees e
            ON e.id=p.employee_id WHERE p.payroll_month=? ORDER BY e.name
            """;
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,month);try(ResultSet r=p.executeQuery()){while(r.next())list.add(new PayrollRow(
                r.getLong("id"),r.getString("code"),r.getString("name"),r.getString("department"),
                r.getString("payroll_month"),r.getBigDecimal("gross_income"),r.getBigDecimal("insurance"),
                r.getBigDecimal("personal_income_tax"),r.getBigDecimal("other_deduction"),r.getBigDecimal("net_salary")));}
            return list;
        }catch(SQLException e){throw new IllegalStateException("Không thể đọc bảng lương.",e);}
    }

    public record PayrollRow(long id,String code,String name,String department,String month,
            BigDecimal gross,BigDecimal insurance,BigDecimal tax,BigDecimal otherDeduction,BigDecimal net){}

    public String loadSetting(String key) {
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(
                "SELECT setting_value FROM app_settings WHERE setting_key=?")){
            p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}
        }catch(SQLException e){throw new IllegalStateException("Không thể đọc thiết lập.",e);}
    }

    public void saveSetting(String key,String value) {
        String sql="INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?) "
                +"ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value";
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,key);p.setString(2,value);p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Không thể lưu thiết lập.",e);}
    }

    public void saveDocument(long employeeId,String name,String from,String to,String payload,BigDecimal net) {
        String sql="""
            INSERT INTO salary_documents(employee_id,document_name,date_from,date_to,payload,net_salary)
            VALUES(?,?,?,?,?,?) ON CONFLICT(employee_id,document_name) DO UPDATE SET
            date_from=excluded.date_from,date_to=excluded.date_to,payload=excluded.payload,
            net_salary=excluded.net_salary,updated_at=CURRENT_TIMESTAMP
            """;
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(sql)){
            p.setLong(1,employeeId);p.setString(2,name);p.setString(3,from);p.setString(4,to);
            p.setString(5,payload);p.setBigDecimal(6,net);p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Không thể lưu hồ sơ lương.",e);}
    }

    public List<DocumentRow> findDocuments() {
        List<DocumentRow> list=new ArrayList<>();String sql="""
            SELECT d.id,d.employee_id,e.name,d.document_name,d.date_from,d.date_to,d.net_salary,d.updated_at
            FROM salary_documents d JOIN employees e ON e.id=d.employee_id ORDER BY d.updated_at DESC
            """;
        try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){
            while(r.next())list.add(new DocumentRow(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                    r.getString(5),r.getString(6),r.getBigDecimal(7),r.getString(8)));return list;
        }catch(SQLException e){throw new IllegalStateException("Không thể đọc hồ sơ lương.",e);}
    }

    public String loadDocument(long id) {
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(
                "SELECT payload FROM salary_documents WHERE id=?")){
            p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}
        }catch(SQLException e){throw new IllegalStateException("Không thể mở hồ sơ lương.",e);}
    }

    public void deleteDocument(long id) {
        try(Connection c=connect();PreparedStatement p=c.prepareStatement(
                "DELETE FROM salary_documents WHERE id=?")){
            p.setLong(1,id);
            if(p.executeUpdate()==0)throw new SQLException("Hồ sơ không còn tồn tại.");
        }catch(SQLException e){throw new IllegalStateException("Không thể xóa hồ sơ.",e);}
    }

    public record DocumentRow(long id,long employeeId,String employeeName,String name,
            String from,String to,BigDecimal net,String updatedAt){}
}
