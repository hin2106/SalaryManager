package app;

import javafx.embed.swing.SwingFXUtils;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.image.WritableImage;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public final class ReportExporter {
    private ReportExporter() {}

    public static void excel(File file, Employee employee, SalarySummary summary,
            List<AttendanceDay> days) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Bảng lương");
            CellStyle heading=wb.createCellStyle();Font bold=wb.createFont();bold.setBold(true);heading.setFont(bold);
            int row=0;
            Row title=sheet.createRow(row++);title.createCell(0).setCellValue("BẢNG LƯƠNG - "+employee.name());title.getCell(0).setCellStyle(heading);
            row++; row=moneyRow(sheet,row,"Tổng thu",summary.grossIncome().doubleValue(),heading);
            row=moneyRow(sheet,row,"Bảo hiểm",summary.insurance().doubleValue(),heading);
            row=moneyRow(sheet,row,"Thuế TNCN",summary.personalIncomeTax().doubleValue(),heading);
            row=moneyRow(sheet,row,"Tổng trừ",summary.totalDeductions().doubleValue(),heading);
            row=moneyRow(sheet,row,"Thực nhận",summary.netSalary().doubleValue(),heading);row+=2;
            String[] headers={"Ngày","Thứ","Trạng thái","Giờ HC","Giờ OT","Ghi chú"};
            Row h=sheet.createRow(row++);for(int i=0;i<headers.length;i++){h.createCell(i).setCellValue(headers[i]);h.getCell(i).setCellStyle(heading);}
            for(AttendanceDay d:days){Row r=sheet.createRow(row++);r.createCell(0).setCellValue(d.date);
                r.createCell(1).setCellValue(d.localDate().getDayOfWeek().toString());
                r.createCell(2).setCellValue(d.status.label());r.createCell(3).setCellValue(d.regularHours);
                r.createCell(4).setCellValue(d.overtimeHours);r.createCell(5).setCellValue(d.note);}
            for(int i=0;i<6;i++)sheet.autoSizeColumn(i);
            try(FileOutputStream out=new FileOutputStream(file)){wb.write(out);}
        }
    }
    private static int moneyRow(Sheet s,int index,String label,double value,CellStyle style){
        Row r=s.createRow(index);r.createCell(0).setCellValue(label);r.getCell(0).setCellStyle(style);
        r.createCell(1).setCellValue(value);return index+1;
    }

    public static void pdf(File file, Node reportNode) throws Exception {
        double width=Math.max(900,reportNode.getBoundsInLocal().getWidth());
        double height=Math.max(700,reportNode.getBoundsInLocal().getHeight());
        WritableImage image=new WritableImage((int)Math.ceil(width),(int)Math.ceil(height));
        reportNode.snapshot(null,image);
        try(PDDocument document=new PDDocument()){
            PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);
            PDImageXObject pdfImage=LosslessFactory.createFromImage(document,SwingFXUtils.fromFXImage(image,null));
            float margin=28,available=page.getMediaBox().getWidth()-margin*2;
            float drawnHeight=(float)(pdfImage.getHeight()*available/pdfImage.getWidth());
            try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                stream.drawImage(pdfImage,margin,page.getMediaBox().getHeight()-margin-drawnHeight,available,drawnHeight);
            }
            document.save(file);
        }
    }

    public static boolean print(Window owner, Node node) {
        PrinterJob job=PrinterJob.createPrinterJob();
        if(job==null||!job.showPrintDialog(owner))return false;
        boolean ok=job.printPage(node);if(ok)job.endJob();return ok;
    }
}
