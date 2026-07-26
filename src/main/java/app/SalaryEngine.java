package app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

public final class SalaryEngine {
    private static final BigDecimal SELF_DEDUCTION = new BigDecimal("15500000");
    private static final BigDecimal DEPENDENT_DEDUCTION = new BigDecimal("6200000");
    private SalaryEngine() {}

    public static List<AttendanceDay> generate(LocalDate from, LocalDate to, WorkSettings s) {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ.");
        List<AttendanceDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean holiday = s.isHoliday(date);
            boolean workDay = s.isWorkDay(date.getDayOfWeek());
            AttendanceStatus status = holiday ? AttendanceStatus.HOLIDAY
                    : workDay ? AttendanceStatus.WORK : AttendanceStatus.OFF;
            double regular = workDay ? s.regularHoursPerDay : 0;
            double overtime = workDay && !holiday ? s.defaultOtHours : 0;
            days.add(new AttendanceDay(date, status, regular, overtime, holiday ? "Ngày lễ" : ""));
        }
        return days;
    }

    public static void applyStatus(AttendanceDay day, AttendanceStatus status, WorkSettings s) {
        day.status = status;
        switch (status) {
            case WORK -> { day.regularHours=s.regularHoursPerDay; day.overtimeHours=s.defaultOtHours;
                day.shiftType=ShiftType.DAY; day.note=""; }
            case SUNDAY_WORK -> { day.regularHours=s.regularHoursPerDay; day.overtimeHours=s.defaultOtHours;
                day.shiftType=ShiftType.SUNDAY_DAY; day.note="Làm ngày nghỉ"; }
            case PAID_LEAVE -> { day.regularHours=s.regularHoursPerDay; day.overtimeHours=0; day.note="Nghỉ phép"; }
            case UNPAID_LEAVE, OFF -> { day.regularHours=0; day.overtimeHours=0; day.note=status.label(); }
            case HOLIDAY -> { day.regularHours=s.regularHoursPerDay; day.overtimeHours=0; day.note="Nghỉ lễ"; }
            case HALF_DAY -> { day.regularHours=s.regularHoursPerDay/2; day.overtimeHours=0; day.note="Nghỉ nửa ngày"; }
            case NO_OT -> { day.regularHours=s.regularHoursPerDay; day.overtimeHours=0; day.note="Không tăng ca"; }
        }
    }

    public static SalarySummary calculate(Employee employee, SalaryInputs inputs,
            WorkSettings settings, List<AttendanceDay> days) {
        BigDecimal standardHours = BigDecimal.valueOf(settings.standardMonthlyHours > 0
                ? settings.standardMonthlyHours : 208);
        BigDecimal fallbackHourly = positive(inputs.hourlyRate)
                ? inputs.hourlyRate : inputs.baseSalary.divide(standardHours, 4, RoundingMode.HALF_UP);
        BigDecimal dayHourly = positive(inputs.dayHourlyRate) ? inputs.dayHourlyRate : fallbackHourly;
        BigDecimal nightHourly = positive(inputs.nightHourlyRate) ? inputs.nightHourlyRate : dayHourly;
        double regularHours=0, weekdayOt=0, sundayOt=0, holidayOt=0;
        int normal=0,sunday=0,leave=0,noOt=0;
        BigDecimal basePay=BigDecimal.ZERO,premiumPay=BigDecimal.ZERO,shiftAllowance=BigDecimal.ZERO;
        Map<ShiftType,Double> shiftHours=new EnumMap<>(ShiftType.class);
        Map<ShiftType,BigDecimal> shiftPays=new EnumMap<>(ShiftType.class);
        for (AttendanceDay d:days) {
            double workedHours = d.regularHours + d.overtimeHours;
            boolean sundayDate=d.localDate().getDayOfWeek()==DayOfWeek.SUNDAY;
            ShiftType shift=d.shiftType==null?(sundayDate?ShiftType.SUNDAY_DAY:ShiftType.DAY):d.shiftType;
            if(sundayDate&&shift==ShiftType.DAY)shift=ShiftType.SUNDAY_DAY;
            if(sundayDate&&shift==ShiftType.NIGHT)shift=ShiftType.SUNDAY_NIGHT;

            boolean unpaid=d.status==AttendanceStatus.UNPAID_LEAVE||d.status==AttendanceStatus.OFF;
            boolean leaveDay=d.status==AttendanceStatus.PAID_LEAVE||d.status==AttendanceStatus.HALF_DAY
                    ||d.status==AttendanceStatus.HOLIDAY;
            if(workedHours>0&&!unpaid){
                BigDecimal shiftHourly=(shift==ShiftType.NIGHT||shift==ShiftType.SUNDAY_NIGHT)
                        ?nightHourly:dayHourly;
                BigDecimal dayBase=shiftHourly.multiply(BigDecimal.valueOf(workedHours));
                BigDecimal dayPay;
                if(leaveDay){
                    dayPay=dayBase;
                }else{
                    dayPay=shiftHourly.multiply(BigDecimal.valueOf(equivalentHours(settings,shift,workedHours)));
                    shiftHours.merge(shift,workedHours,Double::sum);
                    shiftPays.merge(shift,dayPay,BigDecimal::add);
                    if(shift==ShiftType.DAY||shift==ShiftType.NIGHT){
                        BigDecimal allowancePerShift=settings.shiftAllowance>0
                                ?BigDecimal.valueOf(settings.shiftAllowance)
                                :shiftHourly.multiply(new BigDecimal("0.25"));
                        shiftAllowance=shiftAllowance.add(allowancePerShift);
                    }
                }
                basePay=basePay.add(dayBase);
                premiumPay=premiumPay.add(dayPay.subtract(dayBase));
            }
            regularHours+=d.regularHours;
            if(shift==ShiftType.SUNDAY_DAY||shift==ShiftType.SUNDAY_NIGHT)sundayOt+=d.overtimeHours;
            else weekdayOt+=d.overtimeHours;
            if (d.status==AttendanceStatus.WORK || d.status==AttendanceStatus.NO_OT) normal++;
            if ((shift==ShiftType.SUNDAY_DAY||shift==ShiftType.SUNDAY_NIGHT)&&workedHours>0) sunday++;
            if (d.status==AttendanceStatus.PAID_LEAVE || d.status==AttendanceStatus.UNPAID_LEAVE
                    || d.status==AttendanceStatus.HALF_DAY || d.status==AttendanceStatus.OFF) leave++;
            if (d.status==AttendanceStatus.NO_OT) noOt++;
        }
        BigDecimal regularPay=basePay.setScale(0,RoundingMode.HALF_UP);
        BigDecimal otPay=premiumPay.setScale(0,RoundingMode.HALF_UP);
        BigDecimal otherIncome=sum(inputs.fixedIncome).add(sum(inputs.variableIncome)).add(shiftAllowance);
        BigDecimal gross=regularPay.add(otPay).add(otherIncome).setScale(0,RoundingMode.HALF_UP);
        BigDecimal insurance=inputs.baseSalary.multiply(new BigDecimal("0.105")).setScale(0,RoundingMode.HALF_UP);
        BigDecimal taxable=gross.subtract(insurance).subtract(SELF_DEDUCTION)
                .subtract(DEPENDENT_DEDUCTION.multiply(BigDecimal.valueOf(employee.dependents()))).max(BigDecimal.ZERO);
        BigDecimal tax=tax(taxable),manual=sum(inputs.deductions);
        BigDecimal total=insurance.add(tax).add(manual),net=gross.subtract(total);
        String formula = formula(dayHourly,nightHourly,shiftHours,shiftPays,shiftAllowance,inputs,
                regularPay,otPay,otherIncome,gross,insurance,taxable,tax,manual,total,net);
        return new SalarySummary(normal,sunday,leave,noOt,regularHours,weekdayOt,sundayOt,holidayOt,
                dayHourly,regularPay,otPay,otherIncome,gross,insurance,tax,manual,total,net,formula);
    }

    private static double equivalentHours(WorkSettings s,ShiftType shift,double hours){
        return switch(shift){
            case DAY -> segment(hours,0,s.dayRegularBlockHours,s.dayRegularRate)
                    +segment(hours,s.dayRegularBlockHours,s.dayRegularBlockHours+s.dayOtBlockHours,s.dayOtRate)
                    +Math.max(0,hours-s.dayRegularBlockHours-s.dayOtBlockHours)*s.dayExtraRate;
            case NIGHT -> segment(hours,0,s.nightBlock1Hours,s.nightBlock1Rate)
                    +segment(hours,s.nightBlock1Hours,s.nightBlock1Hours+s.nightBlock2Hours,s.nightBlock2Rate)
                    +segment(hours,s.nightBlock1Hours+s.nightBlock2Hours,
                        s.nightBlock1Hours+s.nightBlock2Hours+s.nightPremiumHours,s.nightPremiumRate)
                    +Math.max(0,hours-s.nightBlock1Hours-s.nightBlock2Hours-s.nightPremiumHours)*s.nightExtraRate;
            case SUNDAY_DAY -> segment(hours,0,s.sundayDayBaseHours,s.sundayDayRate)
                    +Math.max(0,hours-s.sundayDayBaseHours)*s.sundayDayExtraRate;
            case SUNDAY_NIGHT -> segment(hours,0,s.sundayNightBlock1Hours,s.sundayNightBlock1Rate)
                    +segment(hours,s.sundayNightBlock1Hours,
                        s.sundayNightBlock1Hours+s.sundayNightBlock2Hours,s.sundayNightBlock2Rate)
                    +Math.max(0,hours-s.sundayNightBlock1Hours-s.sundayNightBlock2Hours)*s.sundayNightExtraRate;
        };
    }
    private static double segment(double hours,double from,double to,double rate){
        return Math.max(0,Math.min(hours,to)-from)*rate;
    }

    private static BigDecimal sum(Map<String,BigDecimal> map){return map.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static boolean positive(BigDecimal value){return value!=null&&value.signum()>0;}
    private static BigDecimal tax(BigDecimal income){
        long[] limits={10_000_000,30_000_000,60_000_000,100_000_000};String[] rates={"0.05","0.10","0.20","0.30","0.35"};
        BigDecimal remaining=income,previous=BigDecimal.ZERO,result=BigDecimal.ZERO;
        for(int i=0;i<rates.length;i++){BigDecimal limit=i<limits.length?BigDecimal.valueOf(limits[i]):income;
            BigDecimal band=remaining.min(limit.subtract(previous)).max(BigDecimal.ZERO);
            result=result.add(band.multiply(new BigDecimal(rates[i])));remaining=remaining.subtract(band);previous=limit;if(remaining.signum()<=0)break;}
        return result.setScale(0,RoundingMode.HALF_UP);
    }
    private static String formula(BigDecimal dayHourly,BigDecimal nightHourly,Map<ShiftType,Double> shiftHours,
            Map<ShiftType,BigDecimal> shiftPays,BigDecimal allowance,SalaryInputs in,
            BigDecimal regularPay,BigDecimal premiumPay,BigDecimal other,BigDecimal gross,
            BigDecimal insurance,BigDecimal taxable,BigDecimal tax,BigDecimal manual,
            BigDecimal total,BigDecimal net){
        StringBuilder b=new StringBuilder("ĐƠN GIÁ\nCa ngày: ").append(money(dayHourly))
                .append(" / giờ\nCa đêm: ").append(money(nightHourly))
                .append(" / giờ\n\nTIỀN CÔNG THEO CA");
        for(ShiftType shift:ShiftType.values())if(shiftHours.containsKey(shift))
            b.append("\n").append(shift).append(": ").append(num(shiftHours.get(shift)))
                    .append(" giờ = ").append(money(shiftPays.get(shift)));
        b.append("\nPhụ cấp ca: ").append(money(allowance)).append('\n');
        in.fixedIncome.forEach((k,v)->{if(v!=null&&v.signum()!=0)b.append('\n').append(k).append(": ").append(money(v));});
        in.variableIncome.forEach((k,v)->{if(v!=null&&v.signum()!=0)b.append('\n').append(k).append(": ").append(money(v));});
        b.append("\n\nTỔNG THU: ").append(money(gross)).append("\n\nKHOẢN TRỪ")
          .append("\nBảo hiểm bắt buộc: ").append(money(insurance)).append(" (10,5% lương cơ bản)")
          .append("\n  • BHXH: 8%")
          .append("\n  • BHYT: 1,5%")
          .append("\n  • BHTN: 1%")
          .append("\n\nThu nhập tính thuế: ").append(money(taxable))
          .append("\nThuế TNCN: ").append(money(tax))
          .append(taxDetail(taxable))
          .append("\nTạm ứng / phạt / khác: ").append(money(manual)).append("\nTỔNG TRỪ: ").append(money(total))
          .append("\n\nTHỰC NHẬN: ").append(money(net));return b.toString();
    }
    private static String taxDetail(BigDecimal taxable){
        if(taxable.signum()<=0)return "\n  • Không phát sinh thuế";
        long[] limits={10_000_000,30_000_000,60_000_000,100_000_000};
        String[] labels={"5% đến 10 triệu","10% từ trên 10–30 triệu",
                "20% từ trên 30–60 triệu","30% từ trên 60–100 triệu","35% phần trên 100 triệu"};
        BigDecimal remaining=taxable,previous=BigDecimal.ZERO;
        StringBuilder detail=new StringBuilder();
        for(int i=0;i<labels.length&&remaining.signum()>0;i++){
            BigDecimal limit=i<limits.length?BigDecimal.valueOf(limits[i]):taxable;
            BigDecimal band=remaining.min(limit.subtract(previous)).max(BigDecimal.ZERO);
            if(band.signum()>0)detail.append("\n  • ").append(labels[i]).append(": ").append(money(band));
            remaining=remaining.subtract(band);previous=limit;
        }
        return detail.toString();
    }
    private static String money(BigDecimal v){return String.format(Locale.forLanguageTag("vi-VN"),"%,.0f ₫",v);}
    private static String num(double v){return v==Math.rint(v)?String.format("%.0f",v):String.format("%.1f",v);}
}
