package kookyohwan.model;

/** 요일 + 교시 범위(끝 교시 포함). 겹침 판정의 최소 단위. */
public class TimeSlot {
    /** 요일 한 글자 + 교시(1~2자리) + 선택적으로 "-끝 교시" */
    private static final java.util.regex.Pattern FORMAT =
            java.util.regex.Pattern.compile("(.)([0-9]{1,2})(?:-([0-9]{1,2}))?");

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

    /**
     * "월3-4" -> TimeSlot(MON, 3, 4). "월3"처럼 한 교시만 쓰면 3-3으로 본다.
     * 형식이 조금이라도 다르면("월-", "월3-", "월3-4-5", 공백 포함 등) IllegalArgumentException —
     * 다른 예외가 새어 나가면 파일을 읽다가 프로그램이 죽기 때문에 형식을 먼저 통째로 검사한다.
     */
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

    /** "월 3-4" (화면용. 파일 형식 "월3-4"와는 공백 하나 차이). 한 교시짜리는 "월 5". */
    @Override
    public String toString() {
        return day.getLabel() + " " + (start == end ? String.valueOf(start) : start + "-" + end);
    }
}
