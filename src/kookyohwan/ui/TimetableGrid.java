package kookyohwan.ui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.Day;
import kookyohwan.model.TimeSlot;
import kookyohwan.model.Timetable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** 메뉴 2: 시간표를 요일 x 교시 격자로 출력 (시간표 앱처럼 연속 교시가 한 덩어리로 보이게 테두리를 병합). */
public final class TimetableGrid {
    private static final int CELL = 14;             // 칸 폭 (한글 7자). 교시 열 4칸 + 세로선 6개 -> 월~금 한 줄 80칸
    private static final int MAX_PERIOD = 14;       // 실제 시간표의 마지막 교시(야간 포함)
    private static final int BASE_PERIOD = 9;       // 늦은 수업이 없으면 9교시까지만 그린다 (화면이 길어지지 않게)
    private static final boolean USE_COLOR = false; // Windows 기본 cmd 호환을 위해 기본 끔(16절 Q14)
    private static final String[] BG = {"\u001B[44m", "\u001B[43m", "\u001B[42m",
            "\u001B[45m", "\u001B[46m", "\u001B[41m"};
    private static final String RESET = "\u001B[0m";

    private TimetableGrid() {
    }

    public static void print(Timetable table) {
        // 1) 격자 채우기: grid[요일][교시] = 과목 (EnumMap이라 열 순서가 월->일, 배열 첨자가 곧 교시)
        EnumMap<Day, Course[]> grid = new EnumMap<>(Day.class);
        for (Day d : Day.values()) {
            grid.put(d, new Course[MAX_PERIOD + 2]); // [0]과 맨 끝은 비워 둠 (위·아래 테두리 계산용)
        }
        List<Course> order = table.getCourses(); // 색상 번호용
        int last = BASE_PERIOD;
        boolean weekend = false;
        for (Course c : order) {
            for (TimeSlot t : c.getTimes()) {
                for (int p = t.getStart(); p <= t.getEnd() && p <= MAX_PERIOD; p++) {
                    grid.get(t.getDay())[p] = c;
                    last = Math.max(last, p);
                }
                weekend |= t.getDay() == Day.SAT || t.getDay() == Day.SUN;
            }
        }
        // 토·일 열은 주말 수업이 있을 때만 (평소에는 월~금 80칸을 유지)
        List<Day> days = new ArrayList<>();
        for (Day d : Day.values()) {
            if (weekend || (d != Day.SAT && d != Day.SUN)) {
                days.add(d);
            }
        }

        // 2) 머리글과 위쪽 테두리
        StringBuilder head = new StringBuilder("    |");
        for (Day d : days) {
            head.append(TextUtil.center(d.getLabel(), CELL)).append('|');
        }
        System.out.println(head);
        System.out.println(border(grid, days, 0, last));

        // 3) 교시마다 한 줄: 블록의 첫 교시엔 과목명, 둘째 교시엔 학수번호, 나머지는 빈칸
        for (int p = 1; p <= last; p++) {
            StringBuilder line = new StringBuilder(String.format(" %-3d|", p));
            for (Day d : days) {
                Course[] col = grid.get(d);
                Course c = col[p];
                String text = "";
                if (c != null) {
                    int start = p;
                    while (start > 1 && col[start - 1] == c) {
                        start--; // 이 블록의 첫 교시
                    }
                    if (p == start) {
                        text = TextUtil.cut(c.getName(), CELL);
                    } else if (p == start + 1) {
                        text = c.getCode();
                    }
                }
                String cell = TextUtil.padRight(text, CELL);
                if (USE_COLOR && c != null) {
                    cell = BG[order.indexOf(c) % BG.length] + cell + RESET;
                }
                line.append(cell).append('|');
            }
            System.out.println(line);
            String b = border(grid, days, p, last); // 과목이 바뀌는 칸에만 가로선
            if (b != null) {
                System.out.println(b);
            }
        }

        // 4) 교환 요청을 낼 수 있는 과목(교양)이 바로 보이게 한 줄 요약. 교직·군사는 있을 때만
        StringBuilder summary = new StringBuilder(" 전공: " + names(order, CourseType.MAJOR)
                + "   교양: " + names(order, CourseType.GENERAL));
        for (CourseType type : new CourseType[]{CourseType.TEACHING, CourseType.MILITARY}) {
            String found = names(order, type);
            if (!found.equals("없음")) {
                summary.append("   ").append(type.getLabel()).append(": ").append(found);
            }
        }
        System.out.println(summary);
    }

    /**
     * p교시와 p+1교시 사이의 선. 위아래 과목이 다른 칸만 ----, 바뀌는 칸이 하나도 없으면 null.
     * p == 0 (위쪽)과 p == last (아래쪽)는 전체 테두리.
     */
    private static String border(EnumMap<Day, Course[]> grid, List<Day> days, int p, int last) {
        boolean[] dash = new boolean[days.size()];
        boolean any = false;
        for (int i = 0; i < days.size(); i++) {
            Course[] col = grid.get(days.get(i));
            dash[i] = (p == 0 || p == last) || col[p] != col[p + 1];
            any |= dash[i];
        }
        if (!any) {
            return null;
        }
        StringBuilder sb = new StringBuilder("    ");
        for (int i = 0; i < days.size(); i++) {
            boolean corner = (i == 0) ? dash[0] : (dash[i - 1] || dash[i]);
            sb.append(corner ? '+' : '|');
            sb.append((dash[i] ? "-" : " ").repeat(CELL));
        }
        sb.append(dash[days.size() - 1] ? '+' : '|');
        return sb.toString();
    }

    private static String names(List<Course> courses, CourseType type) {
        List<String> result = new ArrayList<>();
        for (Course c : courses) {
            if (c.getType() == type) {
                result.add(c.getName());
            }
        }
        return result.isEmpty() ? "없음" : String.join(", ", result);
    }
}
