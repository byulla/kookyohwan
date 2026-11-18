package kookyohwan.model;

public class TimeSlot {
    private static final java.util.regex.Pattern FORMAT =
            java.util.regex.Pattern.compile("(.)([0-9]{1,2})(?:-([0-9]{1,2}))?");

    private final Day day;
    private final int start;
    private final int end;

    public TimeSlot(Day day, int start, int end) {
        if (day == null) {
            throw new IllegalArgumentException("요일이 없습니다");
        }
        if (start < 1 || end < start) {
            throw new IllegalArgumentException("잘못된 교시 범위: " + start + "-" + end);
        }
        this.day = day;
        this.start = start;
        this.end = end;
    }

    public Day getDay() {
        return day;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean overlaps(TimeSlot other) {
        return day == other.day && start <= other.end && other.start <= end;
    }

    public static TimeSlot parse(String text) {
        java.util.regex.Matcher m = FORMAT.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("시간 형식 오류: '" + text + "' (예: 월3-4)");
        }
        Day day = Day.fromLabel(m.group(1));
        int start = Integer.parseInt(m.group(2));
        int end = m.group(3) == null ? start : Integer.parseInt(m.group(3));
        return new TimeSlot(day, start, end);
    }

    @Override
    public String toString() {
        return day.getLabel() + " " + (start == end ? String.valueOf(start) : start + "-" + end);
    }
}
