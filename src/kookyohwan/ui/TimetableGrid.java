package kookyohwan.ui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.Day;
import kookyohwan.model.TimeSlot;
import kookyohwan.model.Timetable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class TimetableGrid {
    private static final int CELL = 14;
    private static final int MAX_PERIOD = 14;
    private static final int BASE_PERIOD = 9;
    private static final boolean USE_COLOR = false;
    private static final String[] BG = {"\u001B[44m", "\u001B[43m", "\u001B[42m",
            "\u001B[45m", "\u001B[46m", "\u001B[41m"};
    private static final String RESET = "\u001B[0m";

    private TimetableGrid() {
    }

    public static void print(Timetable table) {
        EnumMap<Day, Course[]> grid = new EnumMap<>(Day.class);
        for (Day d : Day.values()) {
            grid.put(d, new Course[MAX_PERIOD + 2]);
        }
        List<Course> order = table.getCourses();
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

        List<Day> days = new ArrayList<>();
        for (Day d : Day.values()) {
            if (weekend || (d != Day.SAT && d != Day.SUN)) {
                days.add(d);
            }
        }

        StringBuilder head = new StringBuilder("    |");
        for (Day d : days) {
            head.append(TextUtil.center(d.getLabel(), CELL)).append('|');
        }
        System.out.println(head);
        System.out.println(border(grid, days, 0, last));

        for (int p = 1; p <= last; p++) {
            StringBuilder line = new StringBuilder(String.format(" %-3d|", p));
            for (Day d : days) {
                Course[] col = grid.get(d);
                Course c = col[p];
                String text = "";
                if (c != null) {
                    int start = p;
                    while (start > 1 && col[start - 1] == c) {
                        start--;
                    }
                    if (p == start) {
                        text = TextUtil.cut(c.getName(), CELL);
                    } else if (p == start + 1) {
                        text = TextUtil.cut(c.getCode(), CELL);
                    }
                }
                String cell = TextUtil.padRight(text, CELL);
                if (USE_COLOR && c != null) {
                    cell = BG[order.indexOf(c) % BG.length] + cell + RESET;
                }
                line.append(cell).append('|');
            }
            System.out.println(line);
            String b = border(grid, days, p, last);
            if (b != null) {
                System.out.println(b);
            }
        }

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
