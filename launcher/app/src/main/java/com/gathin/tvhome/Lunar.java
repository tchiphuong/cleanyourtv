package com.gathin.tvhome;

import android.content.res.Resources;

import java.util.Calendar;
import java.util.Locale;

/**
 * Lunisolar (Vietnamese / Chinese) date, e.g. "2/9 âm lịch · Bính Ngọ" or "Lunar 2/9 · Year of the Horse", without ICU.
 * The astronomy is Hồ Ngọc Đức's public algorithm (new moons and solar terms from Jean Meeus' formulas); the only
 * difference between the Vietnamese and the Chinese calendar is the time zone it is computed for (UTC+7 or UTC+8),
 * which now and then moves a new moon, and with it the first day of a month, by one day.
 */
final class Lunar {

    private Lunar() {
    }

    /** Hours east of UTC used for the calendar: lunar_utc_offset, or 7 for Vietnamese and 8 otherwise. */
    static int offsetFor(int configured, Locale locale) {
        if (configured >= 0) return configured;
        return "vi".equals(locale.getLanguage()) ? 7 : 8;
    }

    /** Today's lunar date, formatted with the locale's strings, with Vietnamese holidays if applicable. */
    static String today(Resources res, int utcOffset) {
        Calendar c = Calendar.getInstance();
        int solarDay = c.get(Calendar.DAY_OF_MONTH);
        int solarMonth = c.get(Calendar.MONTH) + 1;
        int solarYear = c.get(Calendar.YEAR);
        int[] l = fromSolar(solarDay, solarMonth, solarYear, utcOffset);
        String month = l[3] == 1 ? res.getString(R.string.lunar_month_leap, l[1]) : String.valueOf(l[1]);
        String[] stems = res.getStringArray(R.array.lunar_stems);
        String[] branches = res.getStringArray(R.array.lunar_branches);
        String year = res.getString(R.string.lunar_year, stems[Math.floorMod(l[2] + 6, 10)], branches[Math.floorMod(l[2] + 8, 12)]);
        String dateText = res.getString(R.string.lunar_date_format, l[0], month, year);
        if (utcOffset == 7) {
            String holiday = getVietnameseHoliday(solarDay, solarMonth, solarYear, l[0], l[1], l[3] == 1, utcOffset);
            if (holiday != null && !holiday.isEmpty()) {
                return holiday + " · " + dateText;
            }
        }
        return dateText;
    }

    // Cache đếm ngược lễ để tránh tính toán lại mỗi phút
    private static int cachedSolarDay = -1;
    private static int cachedSolarMonth = -1;
    private static int cachedSolarYear = -1;
    private static int cachedUtcOffset = -1;
    private static String cachedUpcoming = "";

    /**
     * Top {@code count} ngày lễ sắp tới kèm số ngày còn lại (ví dụ: "Sắp tới: Mùng Một (10 âm) (còn 13 ngày) · ...").
     */
    static String upcomingHolidays(int count, int utcOffset) {
        if (utcOffset != 7) return "";
        Calendar c = Calendar.getInstance();
        int sDay = c.get(Calendar.DAY_OF_MONTH);
        int sMonth = c.get(Calendar.MONTH) + 1;
        int sYear = c.get(Calendar.YEAR);
        if (sDay == cachedSolarDay && sMonth == cachedSolarMonth && sYear == cachedSolarYear && utcOffset == cachedUtcOffset) {
            return cachedUpcoming;
        }
        String res = upcomingHolidays(c, count, utcOffset);
        cachedSolarDay = sDay;
        cachedSolarMonth = sMonth;
        cachedSolarYear = sYear;
        cachedUtcOffset = utcOffset;
        cachedUpcoming = res;
        return res;
    }

    /** Thuật toán tìm top {@code count} ngày lễ sắp tới dựa trên Calendar mốc cho trước. */
    static String upcomingHolidays(Calendar today, int count, int utcOffset) {
        if (utcOffset != 7 || count <= 0) return "";
        Calendar c = (Calendar) today.clone();
        StringBuilder sb = new StringBuilder();
        int found = 0;
        String lastGroup = "";

        for (int offset = 1; offset <= 365 && found < count; offset++) {
            c.add(Calendar.DAY_OF_YEAR, 1);
            int sDay = c.get(Calendar.DAY_OF_MONTH);
            int sMonth = c.get(Calendar.MONTH) + 1;
            int sYear = c.get(Calendar.YEAR);
            int[] l = fromSolar(sDay, sMonth, sYear, utcOffset);

            String holiday = getVietnameseHoliday(sDay, sMonth, sYear, l[0], l[1], l[3] == 1, utcOffset);
            if (holiday == null || holiday.isEmpty()) {
                continue;
            }

            String displayName = holiday;
            String groupKey = holiday;

            if (holiday.startsWith("Tết Nguyên Đán") || holiday.equals("Mùng 4 Tết")) {
                displayName = "Tết Nguyên Đán";
                groupKey = "TetNguyenDan";
            } else if (holiday.startsWith("Lễ Giáng Sinh")) {
                displayName = "Giáng Sinh";
                groupKey = "GiangSinh";
            } else if (holiday.startsWith("Đêm Giao Thừa")) {
                displayName = "Đêm Giao Thừa";
                groupKey = "GiaoThua";
            } else if (holiday.equals("Mùng Một")) {
                displayName = "Mùng Một (" + l[1] + " âm)";
                groupKey = "MungMot_" + l[1] + "_" + l[2];
            } else if (holiday.equals("Ngày Rằm")) {
                displayName = "Ngày Rằm (" + l[1] + " âm)";
                groupKey = "NgayRam_" + l[1] + "_" + l[2];
            } else if (holiday.startsWith("Tết Thanh Minh")) {
                displayName = "Tết Thanh Minh";
                groupKey = "ThanhMinh";
            } else if (holiday.startsWith("Rằm tháng Giêng")) {
                displayName = "Rằm tháng Giêng";
                groupKey = "RamThangGieng";
            } else if (holiday.startsWith("Lễ Vu Lan")) {
                displayName = "Lễ Vu Lan";
                groupKey = "VuLan";
            } else if (holiday.startsWith("Tết Táo Quân")) {
                displayName = "Tết Táo Quân";
                groupKey = "TaoQuan";
            } else if (holiday.startsWith("Tết Trùng Thập")) {
                displayName = "Tết Trùng Thập";
                groupKey = "TrungThap";
            } else if (holiday.startsWith("Lễ Thất Tịch")) {
                displayName = "Lễ Thất Tịch";
                groupKey = "ThatTich";
            }

            // Bỏ qua nếu cùng một đợt lễ kéo dài nhiều ngày (ví dụ các ngày Tết)
            if (groupKey.equals(lastGroup)) {
                continue;
            }
            lastGroup = groupKey;

            if (found > 0) {
                sb.append("   ·   ");
            }
            sb.append(displayName).append(" (còn ").append(offset).append(" ngày)");
            found++;
        }

        if (found == 0) {
            return "";
        }
        return "Sắp tới: " + sb.toString();
    }

    /** Returns the Vietnamese festival/holiday name for the given solar and lunar dates, or empty string. */
    static String getVietnameseHoliday(int sDay, int sMonth, int sYear, int lDay, int lMonth, boolean isLeap, double tz) {
        // 1. Tiết Thanh Minh tính theo kinh độ thiên văn mặt trời đi qua 15° (tảo mộ, thay đổi từng năm)
        if (sMonth == 4 && (sDay >= 4 && sDay <= 6)) {
            long jdToday = jdFromDate(sDay, sMonth, sYear);
            double lStart = sunLongitudeDegrees(jdToday, tz);
            double lEnd = sunLongitudeDegrees(jdToday + 1, tz);
            if (lStart <= 15.0 && lEnd > 15.0) {
                return "Tết Thanh Minh (Tảo mộ)";
            }
        }

        // 2. Các ngày lễ Âm lịch truyền thống lớn
        if (!isLeap) {
            if (lMonth == 1) {
                if (lDay == 1) return "Tết Nguyên Đán (Mùng 1)";
                if (lDay == 2) return "Tết Nguyên Đán (Mùng 2)";
                if (lDay == 3) return "Tết Nguyên Đán (Mùng 3)";
                if (lDay == 4) return "Mùng 4 Tết";
                if (lDay == 9) return "Vía Ngọc Hoàng (Mùng 9)";
                if (lDay == 15) return "Rằm tháng Giêng (Tết Nguyên Tiêu)";
            } else if (lMonth == 3) {
                if (lDay == 3) return "Tết Hàn Thực";
                if (lDay == 10) return "Giỗ Tổ Hùng Vương";
            } else if (lMonth == 4) {
                if (lDay == 15) return "Lễ Phật Đản";
            } else if (lMonth == 5) {
                if (lDay == 5) return "Tết Đoan Ngọ";
            } else if (lMonth == 7) {
                if (lDay == 7) return "Lễ Thất Tịch (Ngưu Lang - Chức Nữ)";
                if (lDay == 15) return "Lễ Vu Lan (Báo hiếu)";
            } else if (lMonth == 8) {
                if (lDay == 15) return "Tết Trung Thu";
            } else if (lMonth == 9) {
                if (lDay == 9) return "Tết Trùng Cửu";
            } else if (lMonth == 10) {
                if (lDay == 10) return "Tết Trùng Thập (Cơm mới)";
                if (lDay == 15) return "Tết Hạ Nguyên";
            } else if (lMonth == 12) {
                if (lDay == 23) return "Tết Táo Quân (23 Chạp)";
                if (lDay == 29 || lDay == 30) return "Đêm Giao Thừa (Tất Niên)";
            }
        }

        // 3. Các ngày lễ Dương lịch lớn tại Việt Nam
        if (sMonth == 1 && sDay == 1) return "Tết Dương Lịch";
        if (sMonth == 2 && sDay == 14) return "Lễ Tình Nhân (Valentine)";
        if (sMonth == 2 && sDay == 27) return "Thầy Thuốc Việt Nam";
        if (sMonth == 3 && sDay == 8) return "Quốc Tế Phụ Nữ";
        if (sMonth == 4 && sDay == 30) return "Giải Phóng Miền Nam";
        if (sMonth == 5 && sDay == 1) return "Quốc Tế Lao Động";
        if (sMonth == 6 && sDay == 1) return "Quốc Tế Thiếu Nhi";
        if (sMonth == 9 && sDay == 2) return "Quốc Khánh Việt Nam";
        if (sMonth == 10 && sDay == 20) return "Phụ Nữ Việt Nam";
        if (sMonth == 11 && sDay == 20) return "Nhà Giáo Việt Nam";
        if (sMonth == 12 && sDay == 22) return "Ngày QĐND Việt Nam";
        if (sMonth == 12 && (sDay == 24 || sDay == 25)) return "Lễ Giáng Sinh";

        // 4. Nhắc Mùng Một & Ngày Rằm hàng tháng (khi không trùng ngày lễ lớn ở trên)
        if (lDay == 1) return "Mùng Một";
        if (lDay == 15) return "Ngày Rằm";

        return "";
    }

    /** Solar longitude in degrees (0 to 360). */
    static double sunLongitudeDegrees(long jdn, double tz) {
        double t = (jdn - 2451545.5 - tz / 24) / 36525, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t * t - 0.00000048 * t * t * t;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t * t;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t * t) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) % 360;
        return l < 0 ? l + 360 : l;
    }

    /** Gregorian date to {day, month, year, leap (1 = leap month)} of the lunar calendar at UTC+tz. */
    static int[] fromSolar(int dd, int mm, int yy, double tz) {
        long dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        long monthStart = newMoonDay(k + 1, tz);
        if (monthStart > dayNumber) monthStart = newMoonDay(k, tz);
        long a11 = lunarMonth11(yy, tz);
        long b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = lunarMonth11(yy - 1, tz);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1, tz);
        }
        int lunarDay = (int) (dayNumber - monthStart + 1);
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int leap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapDiff = leapMonthOffset(a11, tz);
            if (diff >= leapDiff) {
                lunarMonth = diff + 10;
                if (diff == leapDiff) leap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new int[]{lunarDay, lunarMonth, lunarYear, leap};
    }

    static long jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        long y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        long jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return jd;
    }

    /** Day number of the k-th new moon after 1900-01-01, in local time. */
    static long newMoonDay(int k, double tz) {
        double t = k / 1236.85, t2 = t * t, t3 = t2 * t, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        c1 -= 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 -= 0.0004 * Math.sin(dr * 3 * mpr);
        c1 += 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 -= 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 -= 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;
        return (long) Math.floor(jd1 + c1 - deltaT + 0.5 + tz / 24);
    }

    /** Which of the 12 thirty-degree sectors the sun's longitude is in at the start of the day. */
    static int sunLongitude(long jdn, double tz) {
        double t = (jdn - 2451545.5 - tz / 24) / 36525, t2 = t * t, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        l -= Math.PI * 2 * Math.floor(l / (Math.PI * 2));
        return (int) Math.floor(l / Math.PI * 6);
    }

    /** First day of the 11th lunar month (the one holding the winter solstice) of a Gregorian year. */
    static long lunarMonth11(int yy, double tz) {
        long off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        long nm = newMoonDay(k, tz);
        if (sunLongitude(nm, tz) >= 9) nm = newMoonDay(k - 1, tz);
        return nm;
    }

    /** Index of the leap month after month 11 in a 13-month year: the first month with no solar term. */
    static int leapMonthOffset(long a11, double tz) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int i = 1;
        int arc = sunLongitude(newMoonDay(k + i, tz), tz);
        int last;
        do {
            last = arc;
            i++;
            arc = sunLongitude(newMoonDay(k + i, tz), tz);
        } while (arc != last && i < 14);
        return i - 1;
    }
}
