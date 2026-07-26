# Lương Việt — SalaryManager

Ứng dụng desktop quản lý nhân viên, lịch chấm công và tính lương theo ca bằng
Java 17, JavaFX và SQLite. Giao diện được tối ưu cho màn hình nhỏ, sử dụng
Material Design Icons và tự chuyển bố cục khi cửa sổ không đủ rộng.

## 1. Yêu cầu hệ thống

- Windows 10/11.
- JDK 17 trở lên.
- Maven 3.8 trở lên.
- NetBeans 17 nếu chạy từ IDE.

## 2. Mở ứng dụng

### Chạy nhanh

Nhấp đúp vào:

```text
MO_APP.bat
```

### Chạy bằng NetBeans

1. Mở thư mục dự án `D:\SalaryManager`.
2. Chờ Maven tải đầy đủ thư viện.
3. Chọn **Run Project**.

### Chạy bằng Maven

```powershell
cd D:\SalaryManager
mvn javafx:run
```

Lớp khởi động chính là `app.MainApp`.

## 3. Các màn hình chính

### Tính lương

Cho phép chọn nhân viên và khoảng ngày của kỳ lương.

#### Thu nhập cố định

- Lương cơ bản.
- Đơn giá ca ngày.
- Đơn giá ca đêm.
- Phụ cấp.
- Chuyên cần.
- Đời sống.
- Thâm niên theo năm.
- Xăng xe.
- Nhà ở.
- Điện thoại.
- Hỗ trợ khác.

Nếu bỏ trống đơn giá ca ngày:

```text
Đơn giá ca ngày = Lương cơ bản / Số giờ chuẩn tháng
```

Số giờ chuẩn mặc định là `208`.

Nếu bỏ trống đơn giá ca đêm, ứng dụng dùng đơn giá ca ngày. Người dùng có thể
nhập một đơn giá riêng cho ca đêm.

#### Thu nhập phát sinh

- Thưởng.
- Thưởng năng suất.
- Thưởng KPI.
- Thưởng lễ.
- Thưởng khác.

#### Khoản trừ

- Bảo hiểm.
- Thuế thu nhập cá nhân.
- Tạm ứng.
- Phạt.
- Trừ khác.

Bảo hiểm và thuế được tính tự động. Tạm ứng, phạt và trừ khác do người dùng
nhập.

### Lịch chấm công

Lịch hiển thị đầy đủ tất cả các tháng trong khoảng đã chọn. Ví dụ, nếu chọn từ
`25/07` đến `25/08`, màn hình sẽ hiển thị cả phần cuối tháng 7 và phần đầu
tháng 8.

Nhấp vào một ngày để chọn trạng thái:

- `L`: Làm bình thường.
- `CN`: Làm ngày nghỉ/Chủ nhật.
- `P`: Nghỉ phép có lương.
- `NL`: Nghỉ không lương.
- `LỄ`: Nghỉ lễ.
- `1/2`: Nghỉ nửa ngày.
- `KOT`: Làm nhưng không tăng ca.
- `N`: Ngày nghỉ.

Bảng chi tiết cho phép chọn loại ca:

- Ca ngày.
- Ca đêm.
- Chủ nhật ngày.
- Chủ nhật đêm.

Giờ hành chính và giờ OT có thể chỉnh trực tiếp cho từng ngày.

### Nhân viên

Hỗ trợ:

- Thêm nhân viên.
- Chỉnh sửa nhân viên.
- Quản lý phòng ban, chức danh, lương tham chiếu và người phụ thuộc.
- Đánh dấu đang làm việc hoặc đã nghỉ.
- Xóa nhân viên.

Khi xóa nhân viên, ứng dụng yêu cầu xác nhận và đồng thời xóa các hồ sơ lương
liên quan. Thao tác này không thể hoàn tác.

### Thiết lập

#### Ca ngày

Có thể thay đổi:

- Giờ bắt đầu ca.
- Giờ kết thúc hành chính.
- Giờ kết thúc OT chính.
- Giờ kết thúc ca dài.
- Số giờ hành chính và hệ số.
- Số giờ OT chính và hệ số.
- Hệ số giờ vượt.

#### Ca đêm

Ca đêm có cấu hình hoàn toàn độc lập:

- Giờ bắt đầu ca đêm.
- Các mốc chuyển dải giờ.
- Giờ kết thúc ca.
- Số giờ và hệ số của từng dải.
- Hệ số giờ đặc biệt.
- Hệ số giờ vượt.

#### Chủ nhật và ngày lễ

- Số giờ và hệ số Chủ nhật ngày.
- Các dải giờ và hệ số Chủ nhật đêm.
- Hệ số giờ vượt.
- Hệ số ngày lễ.
- Phụ cấp mỗi ca.

Nếu phụ cấp ca bằng `0`, ứng dụng tự tính:

```text
Phụ cấp ca = 25% × đơn giá của ca tương ứng
```

#### Lịch làm việc

- Giờ bắt đầu và kết thúc nghỉ trưa.
- Giờ hành chính mặc định mỗi ngày.
- Giờ OT mặc định mỗi ngày.
- Số giờ chuẩn trong tháng.
- Số công cố định hoặc tự tính.
- Chọn ngày làm từ thứ 2 đến Chủ nhật.
- Nhập thêm ngày lễ và lịch Tết.

Tất cả ô thời gian sử dụng danh sách thả xuống:

- Giờ: `00–23`.
- Phút: `00–59`.

Không thể nhập giờ thủ công hoặc dùng nút tăng/giảm.

### Hồ sơ đã lưu

- Lưu bảng lương với tên như `Lương tháng 6`, `Lương tháng 7`.
- Mở lại hồ sơ bất cứ lúc nào.
- Xóa hồ sơ sau khi xác nhận.

## 4. Công thức ca mặc định

Các hệ số đều có thể thay đổi trong màn hình **Thiết lập**.

### Ca ngày

| Dải giờ | Số giờ mặc định | Hệ số |
|---|---:|---:|
| Giờ hành chính | 8 | 100% |
| OT chính | 3 | 150% |
| Giờ vượt sau 11 giờ | Tùy thực tế | 100% |

### Ca đêm

| Dải giờ | Số giờ mặc định | Hệ số |
|---|---:|---:|
| Dải 1 | 2 | 120% |
| Dải 2 | 6 | 130% |
| Giờ đặc biệt | 1 | 200% |
| Giờ còn lại | Tùy thực tế | 150% |

### Chủ nhật ngày

| Dải giờ | Số giờ mặc định | Hệ số |
|---|---:|---:|
| 11 giờ đầu | 11 | 200% |
| Giờ vượt | Tùy thực tế | 150% |

Toàn bộ giờ hành chính và OT của Chủ nhật đều được đưa vào công thức Chủ nhật,
không cộng lại ở mức 100%.

### Chủ nhật đêm

| Dải giờ | Số giờ mặc định | Hệ số |
|---|---:|---:|
| Dải 1 | 2 | 200% |
| Dải 2 | 7 | 250% |
| Giờ còn lại | Tùy thực tế | 200% |

## 5. Bảo hiểm và thuế

Mặc định:

- Tổng bảo hiểm người lao động: `10,5% × lương cơ bản`, gồm:
  - BHXH: `8%`.
  - BHYT: `1,5%`.
  - BHTN: `1%`.
- Giảm trừ bản thân kỳ tính thuế 2026: `15.500.000 đồng/tháng`.
- Giảm trừ mỗi người phụ thuộc: `6.200.000 đồng/tháng`.
- Thuế TNCN dùng biểu lũy tiến 5 bậc của kỳ tính thuế 2026.

Thu nhập tính thuế được xác định theo công thức:

```text
Thu nhập tính thuế
= Tổng thu nhập
- Bảo hiểm bắt buộc
- 15.500.000
- (6.200.000 × số người phụ thuộc)
```

Biểu thuế lũy tiến:

| Bậc | Phần thu nhập tính thuế/tháng | Thuế suất |
|---:|---|---:|
| 1 | Đến 10.000.000 | 5% |
| 2 | Trên 10.000.000 đến 30.000.000 | 10% |
| 3 | Trên 30.000.000 đến 60.000.000 | 20% |
| 4 | Trên 60.000.000 đến 100.000.000 | 30% |
| 5 | Trên 100.000.000 | 35% |

Thuế được tính theo từng phần, không lấy toàn bộ thu nhập nhân với mức thuế cao
nhất. Mục **Chi tiết cách tính** trong ứng dụng hiển thị bậc thuế thực tế đã áp
dụng.

Các kết quả trong ứng dụng phục vụ lập bảng lương nội bộ. Trước khi kê khai
chính thức, doanh nghiệp cần đối chiếu hợp đồng lao động, tiền lương đóng bảo
hiểm và quy định pháp luật đang áp dụng.

## 6. Lưu dữ liệu

Dữ liệu được lưu tại:

```text
database\salary.db
```

Ứng dụng sử dụng SQLite ở chế độ WAL để giảm thời gian chờ khi ghi dữ liệu.

### Lưu nháp tự động

Sau khi người dùng ngừng nhập khoảng 550 mili giây, ứng dụng lưu nháp ở luồng
nền. Khi đóng ứng dụng, dữ liệu mới nhất được ghi lại ngay lập tức.

Khi mở lại, các nội dung sau được phục hồi:

- Nhân viên đang chọn.
- Kỳ lương.
- Lương cơ bản.
- Đơn giá ca ngày và ca đêm.
- Phụ cấp, thưởng và khoản trừ.
- Toàn bộ lịch chấm công.
- Loại ca và số giờ từng ngày.

Nếu phát hiện `salary.db` không phải SQLite hợp lệ, ứng dụng giữ lại tệp đó dưới
dạng:

```text
salary.db.invalid-YYYYMMDD-HHMMSS.bak
```

sau đó tạo cơ sở dữ liệu mới.

## 7. Định dạng dữ liệu

Các ô tiền sử dụng dấu chấm phân cách hàng nghìn:

```text
6.400.000
400.000
30.769
```

Khi ô đang được chỉnh sửa, dấu phân cách có thể tạm ẩn để nhập thuận tiện. Khi
rời khỏi ô, ứng dụng tự định dạng lại.

## 8. Thống kê và chi tiết công thức

Ứng dụng hiển thị:

- Ngày thường.
- Số ngày nghỉ làm.
- Số ngày nghỉ.
- Số ngày không OT.
- Tổng giờ hành chính.
- Tổng giờ OT.
- Tổng giờ.
- Tổng thu.
- Tổng trừ.
- Thực nhận.

Mục **Chi tiết cách tính** cho biết:

- Đơn giá ca ngày và ca đêm.
- Tổng giờ và số tiền từng loại ca.
- Phụ cấp ca.
- Các phụ cấp/thưởng.
- Bảo hiểm và thuế.
- Tổng thu, tổng trừ và thực nhận.

## 9. Xuất dữ liệu

Tại màn hình Tính lương:

- **Excel**: xuất bảng lương và chi tiết chấm công `.xlsx`.
- **PDF**: xuất báo cáo `.pdf`.
- **In**: mở hộp thoại máy in của Windows.

## 10. Giao diện và hiệu năng

- Cửa sổ mở ở trạng thái phóng to.
- Kích thước tối thiểu: `960 × 640`.
- Các màn hình tự chuyển sang bố cục dọc khi thiếu chiều ngang.
- Nhãn dài tự xuống dòng và có tooltip.
- Các trang dài có thanh cuộn.
- Lưu database chạy nền, không chặn giao diện.
- Đổi trạng thái chấm công chỉ cập nhật ô vừa thay đổi.

## 11. Công nghệ

- Java 17.
- JavaFX 21.
- Maven.
- SQLite.
- Jackson.
- Apache POI để xuất Excel.
- Apache PDFBox để xuất PDF.
- Ikonli Material Design Icons.
- Font giao diện Segoe UI.

## 12. Biên dịch

```powershell
mvn clean package
```

Tệp JAR được tạo tại:

```text
target\SalaryManager-1.0.0.jar
```

Do JavaFX sử dụng các thư viện riêng, nên khởi chạy bằng `MO_APP.bat`,
`mvn javafx:run` hoặc nút **Run Project** của NetBeans thay vì nhấp trực tiếp
vào tệp JAR.
