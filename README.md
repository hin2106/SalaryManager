# Lương Việt

Ứng dụng desktop quản lý nhân viên, chấm công và tính lương bằng JavaFX.

## Mở ứng dụng

Nhấp đúp `MO_APP.bat`, hoặc chạy:

```powershell
mvn javafx:run
```

Yêu cầu Java 17+ và Maven. Dữ liệu được lưu tại `database/salary.db`.

## Chức năng

- Thu nhập cố định, thu nhập phát sinh và các khoản trừ tách riêng.
- Thiết lập giờ vào/ra, nghỉ trưa, ca tăng ca và lịch làm từng công ty.
- Hệ số OT ngày thường, ngày nghỉ và ngày lễ có thể thay đổi.
- Tạo lịch theo khoảng ngày, nhận diện ngày làm/ngày nghỉ/ngày lễ.
- Lịch chấm công tương tác và bảng chi tiết giờ hành chính/OT từng ngày.
- Nghỉ phép, nghỉ không lương, nghỉ lễ, nửa ngày và không tăng ca.
- Kết quả và công thức cập nhật ngay khi thay đổi dữ liệu.
- Lưu và mở lại hồ sơ lương theo tháng.
- Xuất Excel, PDF và in.

## Công thức mặc định

- Lương hành chính = tổng giờ hành chính × đơn giá giờ.
- Nếu chưa nhập đơn giá giờ, ứng dụng tự suy ra từ lương cơ bản.
- Tăng ca ngày thường 150%, ngày nghỉ 200%, ngày lễ 300%; có thể thay đổi.
- Người lao động đóng bảo hiểm 10,5% trên lương cơ bản.
- Giảm trừ gia cảnh kỳ tính thuế 2026: 15,5 triệu đồng cho bản thân và
  6,2 triệu đồng cho mỗi người phụ thuộc.
- Thuế TNCN theo biểu lũy tiến 5 bậc của kỳ tính thuế 2026.

Các con số trong ứng dụng hỗ trợ lập bảng lương nội bộ; cần đối chiếu hồ sơ
lao động và quy định áp dụng riêng của doanh nghiệp trước khi kê khai chính thức.
