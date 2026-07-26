package app;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;

public class SalaryApplication extends Application {
    private final Database db=new Database();
    private final ObjectMapper json=new ObjectMapper();
    private final NumberFormat money=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
    private final ObservableList<AttendanceDay> attendance=FXCollections.observableArrayList();
    private final StackPane content=new StackPane();
    private final Label title=new Label(),subtitle=new Label();
    private final Map<String,Label> metrics=new LinkedHashMap<>();
    private final PauseTransition draftDelay=new PauseTransition(Duration.millis(550));
    private final ExecutorService storageExecutor=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"salary-storage");thread.setDaemon(true);return thread;
    });
    private final List<ChangeListener<Number>> widthListeners=new ArrayList<>();
    private TableView<AttendanceDay> activeAttendanceTable;
    private WorkSettings settings;
    private SalaryInputs inputs=new SalaryInputs();
    private Employee employee;
    private SalarySummary summary;
    private LocalDate from=YearMonth.now().atDay(1),to=YearMonth.now().atEndOfMonth();
    private Button salaryNav,calendarNav,staffNav,settingsNav,recordsNav;
    private Node printableReport;

    @Override public void start(Stage stage){
        settings=loadSettings();
        draftDelay.setOnFinished(e->persistDraftAsync());
        List<Employee> staff=db.findEmployees();if(!staff.isEmpty())employee=staff.get(0);
        if(!loadDraft())attendance.setAll(SalaryEngine.generate(from,to,settings));
        BorderPane root=new BorderPane();root.setLeft(sidebar());
        VBox header=new VBox(4,title,subtitle);header.getStyleClass().add("header");
        title.getStyleClass().add("title");subtitle.getStyleClass().add("muted");
        root.setTop(header);root.setCenter(content);
        Scene scene=new Scene(root,1080,720);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app/style.css")).toExternalForm());
        stage.setTitle("Saraly Manager · Quản lý lương");stage.setMinWidth(960);stage.setMinHeight(640);
        stage.setScene(scene);stage.setMaximized(true);stage.setOnCloseRequest(e->flushAndClose());showSalary();stage.show();
    }

    private VBox sidebar(){
        Label mark=new Label("LV"),brand=new Label("Saraly Manager");mark.getStyleClass().add("mark");brand.getStyleClass().add("brand");
        HBox logo=new HBox(10,mark,brand);logo.setAlignment(Pos.CENTER_LEFT);
        salaryNav=nav("Tính lương","mdi2c-calculator",this::showSalary);
        calendarNav=nav("Lịch chấm công","mdi2c-calendar-month",this::showCalendar);
        staffNav=nav("Nhân viên","mdi2a-account-group",this::showEmployees);
        settingsNav=nav("Thiết lập","mdi2c-cog",this::showSettings);
        recordsNav=nav("Hồ sơ đã lưu","mdi2f-folder",this::showRecords);
        Region grow=new Region();VBox.setVgrow(grow,Priority.ALWAYS);
        Label note=new Label("Dữ liệu lưu trên máy\nTự động tính theo thay đổi");note.getStyleClass().add("side-note");
        VBox box=new VBox(14,logo,new Separator(),salaryNav,calendarNav,staffNav,settingsNav,recordsNav,grow,note);
        box.getStyleClass().add("sidebar");box.setPrefWidth(220);return box;
    }
    private Button nav(String text,String icon,Runnable action){
        FontIcon glyph=new FontIcon(icon);glyph.setIconSize(18);Button b=new Button(text,glyph);
        b.getStyleClass().add("nav");b.setMaxWidth(Double.MAX_VALUE);b.setOnAction(e->action.run());return b;
    }
    private void page(Button active,String heading,String sub){
        for(ChangeListener<Number> listener:widthListeners)content.widthProperty().removeListener(listener);
        widthListeners.clear();
        for(Button b:List.of(salaryNav,calendarNav,staffNav,settingsNav,recordsNav))b.getStyleClass().remove("active");
        active.getStyleClass().add("active");title.setText(heading);subtitle.setText(sub);content.getChildren().clear();
    }

    private void showSalary(){
        page(salaryNav,"Tính lương","Thu nhập, khấu trừ và kết quả được cập nhật tức thời");
        ComboBox<Employee> staff=new ComboBox<>(FXCollections.observableArrayList(db.findEmployees().stream().filter(Employee::active).toList()));
        staff.setMaxWidth(Double.MAX_VALUE);if(employee!=null)staff.getSelectionModel().select(
                staff.getItems().stream().filter(e->e.id()==employee.id()).findFirst().orElse(null));
        staff.valueProperty().addListener((o,a,b)->{employee=b;recalculate();saveDraft();});
        DatePicker fromPicker=new DatePicker(from),toPicker=new DatePicker(to);
        fromPicker.valueProperty().addListener((o,a,b)->{if(b!=null){from=b;regenerate();showSalary();}});
        toPicker.valueProperty().addListener((o,a,b)->{if(b!=null){to=b;regenerate();showSalary();}});
        GridPane context=formGrid();addRow(context,0,"Nhân viên",staff);addRow(context,1,"Từ ngày",fromPicker);addRow(context,2,"Đến ngày",toPicker);
        Button calendar=action("Chỉnh lịch chấm công","secondary","mdi2c-calendar-edit",this::showCalendar);
        VBox contextCard=card("Kỳ lương",context,calendar);

        VBox fixed=moneyGroup("Thu nhập cố định",true,inputs.fixedIncome);
        VBox variable=moneyGroup("Thu nhập phát sinh",false,inputs.variableIncome);
        VBox deductions=moneyGroup("Khoản trừ",false,inputs.deductions);
        ScrollPane inputScroll=new ScrollPane(new VBox(14,contextCard,fixed,variable,deductions));
        inputScroll.setFitToWidth(true);inputScroll.getStyleClass().add("clean-scroll");inputScroll.setPrefWidth(470);

        GridPane stats=new GridPane();stats.setHgap(10);stats.setVgap(10);
        String[] names={"Ngày thường","Ngày nghỉ làm","Ngày nghỉ","Ngày không OT","Giờ hành chính","Giờ OT","Tổng giờ"};
        for(int i=0;i<names.length;i++){Label value=label("0","stat-value");metrics.put(names[i],value);
            VBox tile=new VBox(5,label(names[i],"stat-label"),value);tile.getStyleClass().add("stat-tile");
            stats.add(tile,i%4,i/4);GridPane.setHgrow(tile,Priority.ALWAYS);}
        Label gross=label("0 ₫","result-amount"),deduct=label("0 ₫","result-amount"),net=label("0 ₫","net");
        metrics.put("Tổng thu",gross);metrics.put("Tổng trừ",deduct);metrics.put("Thực nhận",net);
        HBox totals=new HBox(12,resultTile("TỔNG THU",gross),resultTile("TỔNG TRỪ",deduct),resultTile("THỰC NHẬN",net));
        TextArea formula=new TextArea();formula.setEditable(false);formula.setWrapText(true);formula.getStyleClass().add("formula");
        metrics.put("formula",formulaLabel(formula));
        TitledPane details=new TitledPane("Chi tiết cách tính",formula);details.setExpanded(true);
        Button save=action("Lưu hồ sơ","primary","mdi2c-content-save",this::saveDocument);
        Button excel=action("Excel","secondary","mdi2m-microsoft-excel",this::exportExcel);
        Button pdf=action("PDF","secondary","mdi2f-file-pdf-box",()->exportPdf(printableReport));
        Button print=action("In","secondary","mdi2p-printer",()->ReportExporter.print(content.getScene().getWindow(),printableReport));
        HBox actions=new HBox(9,save,excel,pdf,print);actions.setAlignment(Pos.CENTER_RIGHT);
        VBox report=new VBox(14,label("Thống kê thời gian","section"),stats,totals,details,actions);report.getStyleClass().add("panel");
        printableReport=report;
        ScrollPane reportScroll=new ScrollPane(report);reportScroll.setFitToWidth(true);reportScroll.getStyleClass().add("clean-scroll");
        SplitPane split=new SplitPane(inputScroll,reportScroll);split.setDividerPositions(.39);
        watchWidth(width->{
            boolean narrow=width<940;
            split.setOrientation(narrow?Orientation.VERTICAL:Orientation.HORIZONTAL);
            split.setDividerPositions(narrow?.48:.39);
        });
        VBox view=new VBox(split);view.getStyleClass().add("page");VBox.setVgrow(split,Priority.ALWAYS);content.getChildren().add(view);
        recalculate();formula.setText(summary==null?"":summary.formula());
    }
    private Label formulaLabel(TextArea area){Label marker=new Label();marker.setUserData(area);return marker;}
    private VBox moneyGroup(String heading,boolean base,Map<String,BigDecimal> values){
        GridPane grid=formGrid();int row=0;
        if(base){TextField basic=moneyField(inputs.baseSalary,v->inputs.baseSalary=v);
            TextField dayHourly=moneyField(inputs.dayHourlyRate,v->inputs.dayHourlyRate=v);
            TextField nightHourly=moneyField(inputs.nightHourlyRate,v->inputs.nightHourlyRate=v);
            addRow(grid,row++,"Lương cơ bản",basic);
            addRow(grid,row++,"Đơn giá ca ngày",dayHourly);
            addRow(grid,row++,"Đơn giá ca đêm",nightHourly);}
        for(String key:new ArrayList<>(values.keySet())){
            TextField field=moneyField(values.get(key),v->values.put(key,v));addRow(grid,row++,key,field);
        }
        return card(heading,grid);
    }
    private TextField moneyField(BigDecimal initial,java.util.function.Consumer<BigDecimal> setter){
        TextField f=new TextField(initial==null||initial.signum()==0?"":formatInput(initial));f.setPromptText("0");
        f.focusedProperty().addListener((o,was,is)->{try{
            if(is)f.setText(f.getText().replace(".",""));
            else if(!f.getText().isBlank())f.setText(formatInput(parse(f.getText())));
        }catch(Exception ignored){}});
        f.textProperty().addListener((o,a,b)->{try{setter.accept(parse(b));f.getStyleClass().remove("invalid");recalculate();saveDraft();}
            catch(Exception e){if(!f.getStyleClass().contains("invalid"))f.getStyleClass().add("invalid");}});return f;
    }

    private void showCalendar(){
        page(calendarNav,"Lịch chấm công","Nhấp một ngày để chọn trạng thái; chỉnh giờ trực tiếp trong bảng");
        VBox calendars=new VBox(16);
        for(YearMonth current=YearMonth.from(from),last=YearMonth.from(to);
                !current.isAfter(last);current=current.plusMonths(1)){
            calendars.getChildren().add(monthCalendar(current));
        }
        ScrollPane calendarScroll=new ScrollPane(calendars);
        calendarScroll.setFitToWidth(true);calendarScroll.getStyleClass().add("clean-scroll");
        TableView<AttendanceDay> table=attendanceTable();VBox.setVgrow(table,Priority.ALWAYS);
        VBox left=new VBox(12,calendarScroll,legend());VBox.setVgrow(calendarScroll,Priority.ALWAYS);
        left.getStyleClass().add("panel");left.setPrefWidth(610);
        VBox right=new VBox(12,label("Bảng chi tiết","section"),table);right.getStyleClass().add("panel");
        SplitPane body=new SplitPane(left,right);body.setDividerPositions(.5);
        watchWidth(width->{
            boolean narrow=width<980;
            body.setOrientation(narrow?Orientation.VERTICAL:Orientation.HORIZONTAL);
            body.setDividerPositions(.5);
        });
        Button back=action("Quay lại tính lương","primary","mdi2c-calculator",this::showSalary);
        VBox view=new VBox(14,body,back);view.getStyleClass().add("page");VBox.setVgrow(body,Priority.ALWAYS);content.getChildren().add(view);
    }
    private VBox monthCalendar(YearMonth display){
        GridPane calendar=new GridPane();calendar.setHgap(7);calendar.setVgap(7);
        String[] weekdays={"Thứ 2","Thứ 3","Thứ 4","Thứ 5","Thứ 6","Thứ 7","CN"};
        for(int i=0;i<7;i++){
            ColumnConstraints column=new ColumnConstraints();column.setPercentWidth(100.0/7);
            column.setHgrow(Priority.ALWAYS);calendar.getColumnConstraints().add(column);
            Label heading=label(weekdays[i],"calendar-heading");heading.setMaxWidth(Double.MAX_VALUE);
            calendar.add(heading,i,0);
        }
        int offset=display.atDay(1).getDayOfWeek().getValue()-1;
        for(AttendanceDay day:attendance){
            LocalDate date=day.localDate();if(!YearMonth.from(date).equals(display))continue;
            Button cell=new Button(date.getDayOfMonth()+"\n"+day.status.shortLabel());
            cell.getStyleClass().addAll("day-cell","status-"+day.status.name().toLowerCase());
            cell.setMaxSize(Double.MAX_VALUE,72);cell.setMinHeight(58);
            cell.setOnAction(e->statusMenu(cell,day));
            int index=offset+date.getDayOfMonth()-1;calendar.add(cell,index%7,index/7+1);
        }
        Label monthTitle=label(display.format(DateTimeFormatter.ofPattern("'Tháng' M yyyy")),"section");
        VBox monthBox=new VBox(9,monthTitle,calendar);monthBox.getStyleClass().add("month-calendar");
        return monthBox;
    }
    private void statusMenu(Button owner,AttendanceDay day){
        ContextMenu menu=new ContextMenu();
        for(AttendanceStatus s:AttendanceStatus.values()){MenuItem item=new MenuItem(s.label());
            item.setOnAction(e->{
                SalaryEngine.applyStatus(day,s,settings);
                owner.setText(day.localDate().getDayOfMonth()+"\n"+day.status.shortLabel());
                owner.getStyleClass().removeIf(style->style.startsWith("status-"));
                owner.getStyleClass().add("status-"+day.status.name().toLowerCase());
                if(activeAttendanceTable!=null)activeAttendanceTable.refresh();
                recalculate();saveDraft();
            });menu.getItems().add(item);}
        menu.show(owner,Side.BOTTOM,0,0);
    }
    private HBox legend(){HBox box=new HBox(8);for(AttendanceStatus s:AttendanceStatus.values()){
        Label l=new Label(s.shortLabel()+"  "+s.label());l.getStyleClass().addAll("legend","status-"+s.name().toLowerCase());box.getChildren().add(l);}
        return box;}
    private TableView<AttendanceDay> attendanceTable(){
        TableView<AttendanceDay> t=new TableView<>(attendance);t.setEditable(true);
        activeAttendanceTable=t;
        t.getColumns().add(acol("Ngày",78,d->d.localDate().format(DateTimeFormatter.ofPattern("dd/MM"))));
        t.getColumns().add(acol("Thứ",75,d->dayName(d.localDate())));
        TableColumn<AttendanceDay,AttendanceStatus> status=new TableColumn<>("Trạng thái");status.setPrefWidth(145);
        status.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(x.getValue().status));
        status.setCellFactory(ComboBoxTableCell.forTableColumn(AttendanceStatus.values()));
        status.setOnEditCommit(e->{SalaryEngine.applyStatus(e.getRowValue(),e.getNewValue(),settings);t.refresh();recalculate();saveDraft();});
        t.getColumns().add(status);
        TableColumn<AttendanceDay,ShiftType> shift=new TableColumn<>("Ca làm");shift.setPrefWidth(125);
        shift.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(x.getValue().shiftType==null?ShiftType.DAY:x.getValue().shiftType));
        shift.setCellFactory(ComboBoxTableCell.forTableColumn(ShiftType.values()));
        shift.setOnEditCommit(e->{e.getRowValue().shiftType=e.getNewValue();t.refresh();recalculate();saveDraft();});
        t.getColumns().add(shift);
        t.getColumns().add(numberColumn("HC",65,d->d.regularHours,(d,v)->d.regularHours=v));
        t.getColumns().add(numberColumn("OT",65,d->d.overtimeHours,(d,v)->d.overtimeHours=v));
        t.getColumns().add(acol("Ghi chú",170,d->d.note));return t;
    }
    private TableColumn<AttendanceDay,Double> numberColumn(String name,double width,
            java.util.function.Function<AttendanceDay,Double> get,java.util.function.BiConsumer<AttendanceDay,Double> set){
        TableColumn<AttendanceDay,Double> c=new TableColumn<>(name);c.setPrefWidth(width);
        c.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(get.apply(x.getValue())));
        c.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        c.setOnEditCommit(e->{set.accept(e.getRowValue(),Math.max(0,e.getNewValue()));recalculate();saveDraft();});return c;
    }
    private <T> TableColumn<AttendanceDay,T> acol(String n,double w,java.util.function.Function<AttendanceDay,T> fn){
        TableColumn<AttendanceDay,T> c=new TableColumn<>(n);c.setPrefWidth(w);c.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(fn.apply(x.getValue())));return c;}

    private void showSettings(){
        page(settingsNav,"Thiết lập ca và lịch làm","Không cố định: cấu hình theo chính sách của từng công ty");
        TimeRoll dayStart=new TimeRoll(settings.dayStart),dayEnd=new TimeRoll(settings.dayRegularEnd),
                dayOtEnd=new TimeRoll(settings.dayOtEnd),dayExtendedEnd=new TimeRoll(settings.dayExtendedEnd);
        TextField dayHours=tf(""+settings.dayRegularBlockHours),dayRate=tf(""+settings.dayRegularRate),
                dayOtHours=tf(""+settings.dayOtBlockHours),dayOtRate=tf(""+settings.dayOtRate),
                dayExtraRate=tf(""+settings.dayExtraRate);
        GridPane day=formGrid();addRow(day,0,"Bắt đầu ca ngày",dayStart);addRow(day,1,"Kết thúc giờ HC",dayEnd);
        addRow(day,2,"Kết thúc OT chính",dayOtEnd);addRow(day,3,"Kết thúc ca dài",dayExtendedEnd);
        addRow(day,4,"Số giờ HC",dayHours);addRow(day,5,"Hệ số giờ HC",dayRate);
        addRow(day,6,"Số giờ OT chính",dayOtHours);addRow(day,7,"Hệ số OT chính",dayOtRate);
        addRow(day,8,"Hệ số giờ vượt",dayExtraRate);

        TimeRoll nightStart=new TimeRoll(settings.nightStart),nightBlock2Start=new TimeRoll(settings.nightBlock2Start),
                nightPremiumStart=new TimeRoll(settings.nightPremiumStart),nightRegularEnd=new TimeRoll(settings.nightRegularEnd),
                nightEnd=new TimeRoll(settings.nightEnd);
        TextField nightHours1=tf(""+settings.nightBlock1Hours),nightRate1=tf(""+settings.nightBlock1Rate),
                nightHours2=tf(""+settings.nightBlock2Hours),nightRate2=tf(""+settings.nightBlock2Rate),
                nightPremiumHours=tf(""+settings.nightPremiumHours),nightPremiumRate=tf(""+settings.nightPremiumRate),
                nightExtraRate=tf(""+settings.nightExtraRate);
        GridPane night=formGrid();addRow(night,0,"Bắt đầu ca đêm",nightStart);addRow(night,1,"Mốc dải 2",nightBlock2Start);
        addRow(night,2,"Mốc giờ đặc biệt",nightPremiumStart);addRow(night,3,"Kết thúc giờ chính",nightRegularEnd);
        addRow(night,4,"Kết thúc ca đêm",nightEnd);addRow(night,5,"Số giờ dải 1",nightHours1);
        addRow(night,6,"Hệ số dải 1",nightRate1);addRow(night,7,"Số giờ dải 2",nightHours2);
        addRow(night,8,"Hệ số dải 2",nightRate2);addRow(night,9,"Số giờ đặc biệt",nightPremiumHours);
        addRow(night,10,"Hệ số giờ đặc biệt",nightPremiumRate);addRow(night,11,"Hệ số giờ vượt",nightExtraRate);

        TextField sundayDayHours=tf(""+settings.sundayDayBaseHours),sundayDayRate=tf(""+settings.sundayDayRate),
                sundayDayExtra=tf(""+settings.sundayDayExtraRate),sundayNightHours1=tf(""+settings.sundayNightBlock1Hours),
                sundayNightRate1=tf(""+settings.sundayNightBlock1Rate),sundayNightHours2=tf(""+settings.sundayNightBlock2Hours),
                sundayNightRate2=tf(""+settings.sundayNightBlock2Rate),sundayNightExtra=tf(""+settings.sundayNightExtraRate),
                holidayRate=tf(""+settings.holidayOtRate);
        TextField shiftAllowance=moneyEntry(BigDecimal.valueOf(settings.shiftAllowance));
        GridPane sunday=formGrid();addRow(sunday,0,"CN ngày: số giờ chính",sundayDayHours);
        addRow(sunday,1,"CN ngày: hệ số chính",sundayDayRate);addRow(sunday,2,"CN ngày: hệ số vượt",sundayDayExtra);
        addRow(sunday,3,"CN đêm: số giờ dải 1",sundayNightHours1);addRow(sunday,4,"CN đêm: hệ số dải 1",sundayNightRate1);
        addRow(sunday,5,"CN đêm: số giờ dải 2",sundayNightHours2);addRow(sunday,6,"CN đêm: hệ số dải 2",sundayNightRate2);
        addRow(sunday,7,"CN đêm: hệ số vượt",sundayNightExtra);addRow(sunday,8,"Hệ số ngày lễ",holidayRate);
        addRow(sunday,9,"Phụ cấp/ca (0 = 25% đơn giá)",shiftAllowance);

        TimeRoll lunchStart=new TimeRoll(settings.lunchStart),lunchEnd=new TimeRoll(settings.lunchEnd);
        TextField regular=tf(""+settings.regularHoursPerDay),defaultOt=tf(""+settings.defaultOtHours),
                fixedDays=tf(settings.fixedWorkDays==0?"":""+settings.fixedWorkDays),
                standardHours=tf(""+settings.standardMonthlyHours);
        FlowPane workDays=new FlowPane(10,10);List<CheckBox> checks=new ArrayList<>();
        String[] labels={"Thứ 2","Thứ 3","Thứ 4","Thứ 5","Thứ 6","Thứ 7","Chủ nhật"};
        for(int i=1;i<=7;i++){CheckBox c=new CheckBox(labels[i-1]);c.setSelected(settings.workDays.contains(i));c.setUserData(i);checks.add(c);workDays.getChildren().add(c);}
        TextArea holidays=new TextArea(String.join("\n",settings.extraHolidays));holidays.setPromptText("Mỗi dòng một ngày, ví dụ 2026-02-17 (Tết)");
        GridPane schedule=formGrid();addRow(schedule,0,"Bắt đầu nghỉ trưa",lunchStart);addRow(schedule,1,"Kết thúc nghỉ trưa",lunchEnd);
        addRow(schedule,2,"Giờ HC mặc định/ngày",regular);addRow(schedule,3,"OT mặc định/ngày",defaultOt);
        addRow(schedule,4,"Giờ chuẩn/tháng",standardHours);addRow(schedule,5,"Số công cố định (0 = tự tính)",fixedDays);
        schedule.add(new Label("Ngày làm việc"),0,6);schedule.add(workDays,1,6);
        VBox scheduleCard=card("Lịch làm việc",schedule,label("Ngày lễ bổ sung / ngày Tết","field-label"),holidays);
        Button save=action("Lưu thiết lập","primary","mdi2c-content-save",()->{
            try{settings.dayStart=dayStart.value();settings.dayRegularEnd=dayEnd.value();settings.dayOtEnd=dayOtEnd.value();settings.dayExtendedEnd=dayExtendedEnd.value();
                settings.dayRegularBlockHours=decimal(dayHours.getText());settings.dayRegularRate=decimal(dayRate.getText());
                settings.dayOtBlockHours=decimal(dayOtHours.getText());settings.dayOtRate=decimal(dayOtRate.getText());settings.dayExtraRate=decimal(dayExtraRate.getText());
                settings.nightStart=nightStart.value();settings.nightBlock2Start=nightBlock2Start.value();settings.nightPremiumStart=nightPremiumStart.value();
                settings.nightRegularEnd=nightRegularEnd.value();settings.nightEnd=nightEnd.value();
                settings.nightBlock1Hours=decimal(nightHours1.getText());settings.nightBlock1Rate=decimal(nightRate1.getText());
                settings.nightBlock2Hours=decimal(nightHours2.getText());settings.nightBlock2Rate=decimal(nightRate2.getText());
                settings.nightPremiumHours=decimal(nightPremiumHours.getText());settings.nightPremiumRate=decimal(nightPremiumRate.getText());
                settings.nightExtraRate=decimal(nightExtraRate.getText());
                settings.sundayDayBaseHours=decimal(sundayDayHours.getText());settings.sundayDayRate=decimal(sundayDayRate.getText());
                settings.sundayDayExtraRate=decimal(sundayDayExtra.getText());settings.sundayNightBlock1Hours=decimal(sundayNightHours1.getText());
                settings.sundayNightBlock1Rate=decimal(sundayNightRate1.getText());settings.sundayNightBlock2Hours=decimal(sundayNightHours2.getText());
                settings.sundayNightBlock2Rate=decimal(sundayNightRate2.getText());settings.sundayNightExtraRate=decimal(sundayNightExtra.getText());
                settings.lunchStart=lunchStart.value();settings.lunchEnd=lunchEnd.value();
                settings.regularHoursPerDay=decimal(regular.getText());settings.defaultOtHours=decimal(defaultOt.getText());
                settings.holidayOtRate=decimal(holidayRate.getText());
                settings.standardMonthlyHours=decimal(standardHours.getText());settings.shiftAllowance=parse(shiftAllowance.getText()).doubleValue();
                settings.fixedWorkDays=fixedDays.getText().isBlank()?0:Integer.parseInt(fixedDays.getText());settings.workDays.clear();
                checks.stream().filter(CheckBox::isSelected).forEach(c->settings.workDays.add((Integer)c.getUserData()));
                settings.extraHolidays.clear();holidays.getText().lines().map(String::trim).filter(x->!x.isBlank()).forEach(x->{LocalDate.parse(x);settings.extraHolidays.add(x);});
                db.saveSetting("work_settings",json.writeValueAsString(settings));regenerate();saveDraft();info("Đã lưu thiết lập ca và lịch làm.");
            }catch(Exception e){error("Thiết lập chưa hợp lệ: "+e.getMessage());}});
        VBox dayCard=card("Thiết lập ca ngày",day),nightCard=card("Thiết lập ca đêm",night),
                sundayCard=card("Chủ nhật và ngày lễ",sunday);
        for(VBox card:List.of(dayCard,nightCard,sundayCard,scheduleCard)){card.setPrefWidth(385);card.setMinWidth(345);}
        FlowPane columns=new FlowPane(16,16,dayCard,nightCard,sundayCard,scheduleCard);
        watchWidth(width->columns.setPrefWrapLength(Math.max(360,width-70)));
        VBox view=new VBox(16,columns,save);view.getStyleClass().add("page");
        ScrollPane scroll=new ScrollPane(view);scroll.setFitToWidth(true);scroll.getStyleClass().add("clean-scroll");
        content.getChildren().add(scroll);
    }

    private void showEmployees(){
        page(staffNav,"Nhân viên","Quản lý hồ sơ, mức lương tham chiếu và người phụ thuộc");
        TableView<Employee> table=new TableView<>(FXCollections.observableArrayList(db.findEmployees()));
        table.getColumns().add(ecol("Mã",85,Employee::code));table.getColumns().add(ecol("Họ và tên",190,Employee::name));
        table.getColumns().add(ecol("Phòng ban",135,Employee::department));table.getColumns().add(ecol("Chức danh",175,Employee::position));
        table.getColumns().add(ecol("Lương tham chiếu",155,e->money.format(e.baseSalary())));
        table.getColumns().add(ecol("Người phụ thuộc",120,e->e.dependents()));table.getColumns().add(ecol("Trạng thái",105,e->e.active()?"Đang làm":"Đã nghỉ"));
        Button add=action("Thêm nhân viên","primary","mdi2a-account-plus",()->employeeDialog(null,table));
        Button edit=action("Chỉnh sửa","secondary","mdi2a-account-edit",()->employeeDialog(table.getSelectionModel().getSelectedItem(),table));
        Button delete=action("Xóa nhân viên","danger","mdi2d-delete-outline",()->{
            Employee selected=table.getSelectionModel().getSelectedItem();
            if(selected==null||!confirm("Xóa nhân viên",
                    "Xóa “"+selected.name()+"” và toàn bộ hồ sơ lương liên quan?\nThao tác này không thể hoàn tác."))return;
            try{db.deleteEmployee(selected.id());
                if(employee!=null&&employee.id()==selected.id()){
                    employee=db.findEmployees().stream().filter(Employee::active).findFirst().orElse(null);
                    inputs=new SalaryInputs();regenerate();
                }
                table.setItems(FXCollections.observableArrayList(db.findEmployees()));
                info("Đã xóa nhân viên và dữ liệu liên quan.");
            }catch(Exception e){error(e.getMessage());}
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        HBox actions=new HBox(9,add,edit,delete);VBox panel=new VBox(14,actions,table);panel.getStyleClass().add("panel");VBox.setVgrow(table,Priority.ALWAYS);
        VBox view=new VBox(panel);view.getStyleClass().add("page");VBox.setVgrow(panel,Priority.ALWAYS);content.getChildren().add(view);
    }
    private <T> TableColumn<Employee,T> ecol(String n,double w,java.util.function.Function<Employee,T> fn){
        TableColumn<Employee,T> c=new TableColumn<>(n);c.setPrefWidth(w);c.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(fn.apply(x.getValue())));return c;}
    private void employeeDialog(Employee old,TableView<Employee> table){
        Dialog<Employee> dialog=new Dialog<>();dialog.setTitle(old==null?"Thêm nhân viên":"Chỉnh sửa nhân viên");
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Lưu",ButtonBar.ButtonData.OK_DONE),ButtonType.CANCEL);
        TextField code=tf(old==null?"":old.code()),name=tf(old==null?"":old.name()),department=tf(old==null?"":old.department()),
                position=tf(old==null?"":old.position()),base=moneyEntry(old==null?BigDecimal.ZERO:old.baseSalary()),
                allowance=moneyEntry(old==null?BigDecimal.ZERO:old.allowance());
        Spinner<Integer> dependents=new Spinner<>(0,20,old==null?0:old.dependents());CheckBox active=new CheckBox("Đang làm việc");active.setSelected(old==null||old.active());
        GridPane form=formGrid();addRow(form,0,"Mã nhân viên *",code);addRow(form,1,"Họ và tên *",name);addRow(form,2,"Phòng ban",department);
        addRow(form,3,"Chức danh",position);addRow(form,4,"Lương tham chiếu",base);addRow(form,5,"Phụ cấp mặc định",allowance);
        addRow(form,6,"Người phụ thuộc",dependents);form.add(active,1,7);dialog.getDialogPane().setContent(form);dialog.getDialogPane().setPrefWidth(480);
        dialog.setResultConverter(button->{if(button.getButtonData()!=ButtonBar.ButtonData.OK_DONE)return null;
            try{if(code.getText().isBlank()||name.getText().isBlank())throw new IllegalArgumentException("Vui lòng nhập mã và họ tên.");
                return new Employee(old==null?0:old.id(),code.getText().trim().toUpperCase(),name.getText().trim(),
                        department.getText().trim(),position.getText().trim(),parse(base.getText()),parse(allowance.getText()),
                        dependents.getValue(),active.isSelected());}catch(Exception e){error(e.getMessage());return null;}});
        dialog.showAndWait().ifPresent(value->{try{db.saveEmployee(value);table.setItems(FXCollections.observableArrayList(db.findEmployees()));info("Đã lưu nhân viên.");}
            catch(Exception e){error(e.getMessage());}});
    }

    private void showRecords(){
        page(recordsNav,"Hồ sơ đã lưu","Mở lại bảng lương tháng 6, tháng 7 hoặc bất kỳ kỳ nào");
        TableView<Database.DocumentRow> table=new TableView<>(FXCollections.observableArrayList(db.findDocuments()));
        table.getColumns().add(dcol("Tên hồ sơ",190,Database.DocumentRow::name));
        table.getColumns().add(dcol("Nhân viên",180,Database.DocumentRow::employeeName));
        table.getColumns().add(dcol("Từ ngày",120,Database.DocumentRow::from));table.getColumns().add(dcol("Đến ngày",120,Database.DocumentRow::to));
        table.getColumns().add(dcol("Thực nhận",160,r->money.format(r.net())));table.getColumns().add(dcol("Cập nhật",170,Database.DocumentRow::updatedAt));
        Button open=action("Mở hồ sơ","primary","mdi2f-folder-open",()->{Database.DocumentRow row=table.getSelectionModel().getSelectedItem();if(row!=null)loadDocument(row);});
        Button delete=action("Xóa hồ sơ","danger","mdi2d-delete-outline",()->{
            Database.DocumentRow row=table.getSelectionModel().getSelectedItem();
            if(row==null||!confirm("Xóa hồ sơ","Xóa vĩnh viễn hồ sơ “"+row.name()+"”?"))return;
            try{db.deleteDocument(row.id());table.getItems().remove(row);info("Đã xóa hồ sơ.");}
            catch(Exception e){error(e.getMessage());}
        });
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        HBox actions=new HBox(9,open,delete);
        VBox panel=new VBox(14,actions,table);panel.getStyleClass().add("panel");VBox.setVgrow(table,Priority.ALWAYS);
        VBox view=new VBox(panel);view.getStyleClass().add("page");VBox.setVgrow(panel,Priority.ALWAYS);content.getChildren().add(view);
    }
    private <T> TableColumn<Database.DocumentRow,T> dcol(String n,double w,java.util.function.Function<Database.DocumentRow,T> fn){
        TableColumn<Database.DocumentRow,T> c=new TableColumn<>(n);c.setPrefWidth(w);c.setCellValueFactory(x->new ReadOnlyObjectWrapper<>(fn.apply(x.getValue())));return c;}

    private void recalculate(){
        if(employee==null||attendance.isEmpty())return;
        try{summary=SalaryEngine.calculate(employee,inputs,settings,attendance);
            setMetric("Ngày thường",""+summary.normalDays());setMetric("Ngày nghỉ làm",""+summary.sundayDays());setMetric("Ngày nghỉ",""+summary.leaveDays());
            double displayedRegularHours=attendance.stream().mapToDouble(d->d.regularHours).sum();
            double displayedOtHours=attendance.stream().mapToDouble(d->d.overtimeHours).sum();
            setMetric("Ngày không OT",""+summary.noOtDays());
            setMetric("Giờ hành chính",num(displayedRegularHours));
            setMetric("Giờ OT",num(displayedOtHours));
            setMetric("Tổng giờ",num(displayedRegularHours+displayedOtHours));
            setMetric("Tổng thu",money.format(summary.grossIncome()));setMetric("Tổng trừ",money.format(summary.totalDeductions()));setMetric("Thực nhận",money.format(summary.netSalary()));
            Label marker=metrics.get("formula");if(marker!=null&&marker.getUserData() instanceof TextArea area)area.setText(summary.formula());
        }catch(Exception ignored){}
    }
    private void setMetric(String key,String value){Label label=metrics.get(key);if(label!=null)label.setText(value);}
    private void regenerate(){attendance.setAll(SalaryEngine.generate(from,to,settings));recalculate();saveDraft();}
    private WorkSettings loadSettings(){try{String value=db.loadSetting("work_settings");return value==null?new WorkSettings():json.readValue(value,WorkSettings.class);}catch(Exception e){return new WorkSettings();}}

    private boolean loadDraft(){
        try{
            String value=db.loadSetting("salary_draft");if(value==null)return false;
            SalaryDocument doc=json.readValue(value,SalaryDocument.class);
            employee=db.findEmployees().stream().filter(e->e.id()==doc.employeeId).findFirst().orElse(employee);
            inputs=doc.inputs==null?new SalaryInputs():doc.inputs;normalizeInputs();
            from=LocalDate.parse(doc.from);to=LocalDate.parse(doc.to);
            attendance.setAll(doc.attendance==null?List.of():doc.attendance);return !attendance.isEmpty();
        }catch(Exception e){return false;}
    }
    private void normalizeInputs(){
        SalaryInputs defaults=new SalaryInputs();
        defaults.fixedIncome.forEach(inputs.fixedIncome::putIfAbsent);
        defaults.variableIncome.forEach(inputs.variableIncome::putIfAbsent);
        defaults.deductions.forEach(inputs.deductions::putIfAbsent);
    }
    private void saveDraft(){
        if(employee!=null&&settings!=null)draftDelay.playFromStart();
    }
    private String draftPayload(){
        try{SalaryDocument doc=new SalaryDocument();doc.employeeId=employee.id();doc.employeeName=employee.name();
            doc.documentName="Tự động lưu";doc.from=from.toString();doc.to=to.toString();
            doc.inputs=inputs;doc.attendance=new ArrayList<>(attendance);
            return json.writeValueAsString(doc);
        }catch(Exception ignored){return null;}
    }
    private void persistDraftAsync(){
        String payload=draftPayload();if(payload==null)return;
        storageExecutor.submit(()->db.saveSetting("salary_draft",payload));
    }
    private void flushAndClose(){
        draftDelay.stop();
        storageExecutor.shutdown();
        try{storageExecutor.awaitTermination(1200,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        String payload=draftPayload();if(payload!=null)db.saveSetting("salary_draft",payload);
    }
    private void watchWidth(DoubleConsumer consumer){
        ChangeListener<Number> listener=(observable,oldValue,newValue)->consumer.accept(newValue.doubleValue());
        widthListeners.add(listener);content.widthProperty().addListener(listener);
        consumer.accept(content.getWidth());
    }

    private void saveDocument(){
        if(summary==null||employee==null)return;TextInputDialog d=new TextInputDialog("Lương tháng "+from.format(DateTimeFormatter.ofPattern("MM/yyyy")));
        d.setTitle("Lưu hồ sơ");d.setHeaderText("Đặt tên để mở lại bất cứ lúc nào");
        d.showAndWait().filter(x->!x.isBlank()).ifPresent(name->{try{SalaryDocument doc=new SalaryDocument();doc.employeeId=employee.id();doc.employeeName=employee.name();
            doc.documentName=name;doc.from=from.toString();doc.to=to.toString();doc.inputs=inputs;doc.attendance=new ArrayList<>(attendance);
            db.saveDocument(employee.id(),name,doc.from,doc.to,json.writeValueAsString(doc),summary.netSalary());info("Đã lưu hồ sơ “"+name+"”.");
        }catch(Exception e){error(e.getMessage());}});
    }
    private void loadDocument(Database.DocumentRow row){try{SalaryDocument doc=json.readValue(db.loadDocument(row.id()),SalaryDocument.class);
        employee=db.findEmployees().stream().filter(e->e.id()==doc.employeeId).findFirst().orElse(employee);inputs=doc.inputs;
        from=LocalDate.parse(doc.from);to=LocalDate.parse(doc.to);attendance.setAll(doc.attendance);showSalary();
    }catch(Exception e){error("Không thể mở hồ sơ: "+e.getMessage());}}
    private void exportExcel(){if(summary==null)return;FileChooser f=chooser("Xuất Excel","bang-luong.xlsx","Excel","*.xlsx");
        File file=f.showSaveDialog(content.getScene().getWindow());if(file!=null)try{ReportExporter.excel(file,employee,summary,attendance);info("Đã xuất Excel.");}catch(Exception e){error(e.getMessage());}}
    private void exportPdf(Node node){if(summary==null||node==null)return;FileChooser f=chooser("Xuất PDF","bang-luong.pdf","PDF","*.pdf");
        File file=f.showSaveDialog(content.getScene().getWindow());if(file!=null)try{ReportExporter.pdf(file,node);info("Đã xuất PDF.");}catch(Exception e){error(e.getMessage());}}
    private FileChooser chooser(String title,String name,String label,String pattern){FileChooser f=new FileChooser();f.setTitle(title);f.setInitialFileName(name);f.getExtensionFilters().add(new FileChooser.ExtensionFilter(label,pattern));return f;}

    private VBox card(String heading,Node... nodes){VBox box=new VBox(13,label(heading,"section"));box.getChildren().addAll(nodes);box.getStyleClass().add("panel");return box;}
    private VBox resultTile(String heading,Label value){VBox box=new VBox(6,label(heading,"stat-label"),value);box.getStyleClass().add("result-tile");HBox.setHgrow(box,Priority.ALWAYS);return box;}
    private GridPane formGrid(){
        GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);
        ColumnConstraints labels=new ColumnConstraints(115,145,175);
        ColumnConstraints fields=new ColumnConstraints(145,220,Double.MAX_VALUE);fields.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(labels,fields);return g;
    }
    private void addRow(GridPane g,int row,String label,Node field){
        Label l=new Label(label);l.getStyleClass().add("field-label");l.setWrapText(true);
        l.setMaxWidth(175);l.setTooltip(new Tooltip(label));
        g.add(l,0,row);g.add(field,1,row);GridPane.setHgrow(field,Priority.ALWAYS);
    }
    private Button action(String text,String style,String icon,Runnable action){
        Button b=new Button(text,new FontIcon(icon));b.getStyleClass().add(style);
        b.setTooltip(new Tooltip(text));b.setOnAction(e->action.run());return b;
    }
    private TextField tf(String value){TextField f=new TextField(value);f.setMaxWidth(Double.MAX_VALUE);return f;}
    private TextField moneyEntry(BigDecimal value){
        TextField f=new TextField(value==null||value.signum()==0?"":formatInput(value));f.setPromptText("0");
        f.focusedProperty().addListener((o,was,is)->{try{
            if(is)f.setText(f.getText().replace(".",""));
            else if(!f.getText().isBlank())f.setText(formatInput(parse(f.getText())));
        }catch(Exception ignored){}});return f;
    }
    private Label label(String text,String style){
        Label l=new Label(text);l.getStyleClass().add(style);l.setWrapText(true);
        l.setTooltip(new Tooltip(text));return l;
    }
    private BigDecimal parse(String value){if(value==null||value.isBlank())return BigDecimal.ZERO;return new BigDecimal(value.trim().replace(".","").replace(",",""));}
    private String formatInput(BigDecimal value){return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value);}
    private double decimal(String value){return Double.parseDouble(value.trim().replace(',','.'));}
    private String num(double value){return value==Math.rint(value)?String.format("%.0f",value):String.format("%.1f",value);}
    private String dayName(LocalDate d){return d.getDayOfWeek()==DayOfWeek.SUNDAY?"CN":"Thứ "+(d.getDayOfWeek().getValue()+1);}
    private void error(String text){Alert a=new Alert(Alert.AlertType.ERROR,text,ButtonType.OK);a.setHeaderText("Chưa thể thực hiện");a.showAndWait();}
    private void info(String text){Alert a=new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK);a.setHeaderText(null);a.showAndWait();}
    private boolean confirm(String title,String text){
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,text,ButtonType.CANCEL,ButtonType.OK);
        alert.setTitle(title);alert.setHeaderText(title);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static final class TimeRoll extends HBox {
        private final ComboBox<String> hour;
        private final ComboBox<String> minute;
        TimeRoll(String time){
            LocalTime parsed;
            try{parsed=LocalTime.parse(time);}catch(Exception e){parsed=LocalTime.MIDNIGHT;}
            hour=new ComboBox<>();minute=new ComboBox<>();
            for(int value=0;value<24;value++)hour.getItems().add(String.format("%02d",value));
            for(int value=0;value<60;value++)minute.getItems().add(String.format("%02d",value));
            hour.setValue(String.format("%02d",parsed.getHour()));
            minute.setValue(String.format("%02d",parsed.getMinute()));
            hour.setEditable(false);minute.setEditable(false);
            hour.setVisibleRowCount(12);minute.setVisibleRowCount(12);
            hour.setPrefWidth(82);minute.setPrefWidth(82);
            Label separator=new Label(":");separator.getStyleClass().add("time-separator");
            setSpacing(6);setAlignment(Pos.CENTER_LEFT);getChildren().addAll(hour,separator,minute);
        }
        String value(){return hour.getValue()+":"+minute.getValue();}
    }
}
