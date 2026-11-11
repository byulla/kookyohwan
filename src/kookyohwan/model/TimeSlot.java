package kookyohwan.model;

/** 요일 + 교시 범위(끝 교시 포함). 겹침 판정의 최소 단위. */
public class TimeSlot {
    private final Day day;
    private final int start;
    private final int end;

    public TimeSlot(Day day, int start, int end) {
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

    /** 같은 요일이고 교시 구간이 한 칸이라도 포개지면 겹침. */
    public boolean overlaps(TimeSlot other) {
        return day == other.day && start <= other.end && other.start <= end;
    }

    /** "월3-4" -> TimeSlot(MON, 3, 4). "월3"처럼 한 교시만 쓰면 3-3으로 본다. */
    public static TimeSlot parse(String text) {
        String t = text.trim();
        if (t.length() < 2) {
            throw new IllegalArgumentException("시간 형식 오류: " + text);
        }
        Day day = Day.fromLabel(t.substring(0, 1));
        String[] range = t.substring(1).split("-");
        int start = Integer.parseInt(range[0].trim());
        int end = range.length > 1 ? Integer.parseInt(range[1].trim()) : start;
        return new TimeSlot(day, start, end);
    }

    /** "월 3-4" (화면용. 파일 형식 "월3-4"와는 공백 하나 차이). 한 교시짜리는 "월 5". */
    @Override
    public String toString() {
        return day.getLabel() + " " + (start == end ? String.valueOf(start) : start + "-" + end);
    }
}
