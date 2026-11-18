package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.Day;
import kookyohwan.model.TimeSlot;
import kookyohwan.model.Timetable;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class TimetableCanvas extends JComponent {
    private static final int BASE_PERIOD = 9;
    private static final int FIRST_HOUR = 9;
    private static final int LEFT = 82;
    private static final int TOP = 40;
    private static final int MIN_ROW_HEIGHT = 46;
    private static final int MIN_CELL_WIDTH = 150;
    private static final Color[] BLOCK = {
            new Color(0xDBEBF1), new Color(0xFFF8CC), new Color(0xDDE6F6), new Color(0xFFE6E6),
            new Color(0xEEF8CB), new Color(0xFFE9D6), new Color(0xDBF0E5), new Color(0xEFE5E5)};
    private static final Color HEADER = new Color(0xF2F2F2);
    private static final Color CELL = new Color(0xFAFAFA);
    private static final Color LINE = new Color(0xDCDCDC);
    private static final Color LABEL = new Color(0x8A8A8A);
    private static final Color TEXT = new Color(0x1E1E1E);
    private static final Color SUB_TEXT = new Color(0x5C5C5C);

    private Timetable timetable;

    TimetableCanvas() {
        setToolTipText("과목을 누르면 시간표에서 삭제할 수 있습니다.");
    }

    Optional<Course> courseAt(Point point) {
        if (timetable == null) {
            return Optional.empty();
        }
        List<Day> days = days();
        int cellWidth = cellWidth(days);
        int rowHeight = rowHeight(lastPeriod());
        for (Course c : timetable.getCourses()) {
            for (TimeSlot t : c.getTimes()) {
                int x = LEFT + days.indexOf(t.getDay()) * cellWidth;
                int y = TOP + (t.getStart() - 1) * rowHeight;
                int h = (t.getEnd() - t.getStart() + 1) * rowHeight;
                if (point.x >= x && point.x < x + cellWidth && point.y >= y && point.y < y + h) {
                    return Optional.of(c);
                }
            }
        }
        return Optional.empty();
    }

    private int cellWidth(List<Day> days) {
        return Math.max(MIN_CELL_WIDTH, (getWidth() - LEFT - 1) / days.size());
    }

    private int rowHeight(int periods) {
        return Math.max(MIN_ROW_HEIGHT, (getHeight() - TOP - 1) / periods);
    }

    void setTimetable(Timetable timetable) {
        this.timetable = timetable;
        revalidate();
        repaint();
    }

    private List<Day> days() {
        boolean weekend = false;
        if (timetable != null) {
            for (Course c : timetable.getCourses()) {
                for (TimeSlot t : c.getTimes()) {
                    weekend |= t.getDay() == Day.SAT || t.getDay() == Day.SUN;
                }
            }
        }
        List<Day> days = new ArrayList<>();
        for (Day d : Day.values()) {
            if (weekend || (d != Day.SAT && d != Day.SUN)) {
                days.add(d);
            }
        }
        return days;
    }

    private int lastPeriod() {
        int last = BASE_PERIOD;
        if (timetable != null) {
            for (Course c : timetable.getCourses()) {
                for (TimeSlot t : c.getTimes()) {
                    last = Math.max(last, t.getEnd());
                }
            }
        }
        return last;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(LEFT + days().size() * MIN_CELL_WIDTH + 1, TOP + lastPeriod() * MIN_ROW_HEIGHT + 1);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        List<Day> days = days();
        int periods = lastPeriod();
        int cellWidth = cellWidth(days);
        int rowHeight = rowHeight(periods);
        int right = LEFT + cellWidth * days.size();
        int bottom = TOP + rowHeight * periods;
        Font base = getFont().deriveFont(Font.PLAIN, 13f);
        Font nameFont = base.deriveFont(Font.BOLD, 14f);
        Font smallBold = base.deriveFont(Font.BOLD, 12f);
        Font small = base.deriveFont(12f);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(CELL);
        g.fillRect(LEFT, TOP, right - LEFT, bottom - TOP);
        g.setColor(HEADER);
        g.fillRect(0, 0, right, TOP);
        g.fillRect(0, TOP, LEFT, bottom - TOP);

        g.setFont(base);
        g.setColor(LABEL);
        for (int i = 0; i < days.size(); i++) {
            drawCentered(g, days.get(i).getLabel(), LEFT + i * cellWidth, cellWidth, TOP / 2 + 5);
        }
        for (int p = 1; p <= periods; p++) {
            drawCentered(g, hourLabel(p), 0, LEFT, TOP + (p - 1) * rowHeight + rowHeight / 2 + 5);
        }

        g.setColor(LINE);
        for (int p = 0; p <= periods; p++) {
            g.drawLine(0, TOP + p * rowHeight, right, TOP + p * rowHeight);
        }
        g.drawLine(0, 0, right, 0);
        g.drawLine(0, 0, 0, bottom);
        for (int i = 0; i <= days.size(); i++) {
            g.drawLine(LEFT + i * cellWidth, 0, LEFT + i * cellWidth, bottom);
        }
        if (timetable == null) {
            g.dispose();
            return;
        }

        List<Course> courses = timetable.getCourses();
        for (int index = 0; index < courses.size(); index++) {
            Course c = courses.get(index);
            for (TimeSlot t : c.getTimes()) {
                int x = LEFT + days.indexOf(t.getDay()) * cellWidth + 1;
                int y = TOP + (t.getStart() - 1) * rowHeight + 1;
                int w = cellWidth - 1;
                int h = (t.getEnd() - t.getStart() + 1) * rowHeight - 1;
                g.setColor(BLOCK[index % BLOCK.length]);
                g.fillRect(x, y, w, h);

                int textWidth = w - 20;
                g.setColor(TEXT);
                g.setFont(nameFont);
                g.drawString(fit(g.getFontMetrics(), c.getName(), textWidth), x + 10, y + 23);

                g.setColor(SUB_TEXT);
                int tx = x + 10;
                if (!c.getProfessor().isEmpty()) {
                    g.setFont(smallBold);
                    String professor = fit(g.getFontMetrics(), c.getProfessor(), textWidth);
                    g.drawString(professor, tx, y + 41);
                    tx += g.getFontMetrics().stringWidth(professor) + 6;
                }
                g.setFont(small);
                String place = c.getRoom().isEmpty() && c.getProfessor().isEmpty() ? c.getCode() : c.getRoom();
                if (x + 10 + textWidth - tx > 20) {
                    g.drawString(fit(g.getFontMetrics(), place, x + 10 + textWidth - tx), tx, y + 41);
                }
                if (h >= 64) {
                    g.setColor(LABEL);
                    g.drawString(fit(g.getFontMetrics(), c.getCode() + " · " + c.getType().getLabel(), textWidth),
                            x + 10, y + 59);
                }
            }
        }
        g.dispose();
    }

    private static String hourLabel(int period) {
        int hour = FIRST_HOUR + period - 1;
        if (hour < 12) {
            return "오전 " + hour + "시";
        }
        return "오후 " + (hour == 12 ? 12 : hour - 12) + "시";
    }

    private static void drawCentered(Graphics2D g, String text, int x, int width, int baseline) {
        g.drawString(text, x + (width - g.getFontMetrics().stringWidth(text)) / 2, baseline);
    }

    private static String fit(FontMetrics metrics, String text, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && metrics.stringWidth(cut + "..") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }
}
