package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.Student;
import kookyohwan.model.Timetable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import static kookyohwan.gui.Session.ROW_GAP;

class TimetablePanel extends JPanel {
    private static final Color EDITING = new Color(0xB45309);

    private final Session session;
    private final TimetableCanvas canvas = new TimetableCanvas();
    private final JLabel title = new JLabel();
    private final JLabel mode = new JLabel();
    private final JLabel summary = new JLabel();
    private final JButton confirmButton = Theme.primary("시간표 저장 (확정)");

    TimetablePanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.add(title);
        top.add(mode);
        add(top, BorderLayout.NORTH);
        JScrollPane scroll = Theme.scroll(canvas);
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, ROW_GAP));
        actions.add(confirmButton);
        JPanel notes = new JPanel(new GridLayout(2, 1, 0, 4));
        summary.setForeground(Theme.MUTED);
        notes.add(summary);
        notes.add(Theme.muted("과목을 넣으려면 [과목 목록] 탭에서 고르고, 빼려면 시간표에서 그 과목을 누르세요."));
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(notes, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                canvas.courseAt(e.getPoint()).ifPresent(
                        course -> TimetableActions.remove(TimetablePanel.this, session, course));
            }
        });
        confirmButton.addActionListener(e -> UiEvents.afterInput(
                () -> TimetableActions.confirmTimetable(this, session)));
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Student me = session.getMe();
        List<Course> courses = me.getTimetable().getCourses();
        boolean confirmed = me.isTimetableConfirmed();
        title.setText("내 시간표   " + me.getName() + " (" + me.getId() + ")   " + courses.size() + "과목   "
                + me.getTimetable().getTotalCredits() + " / " + Timetable.MAX_CREDITS + "학점");
        mode.setForeground(confirmed ? Theme.MUTED : EDITING);
        mode.setText(confirmed
                ? "확정된 시간표입니다. 빈자리가 있는 과목은 과목 목록에서 수강신청하고, 마감된 과목은 교환 요청으로 상대를 찾으세요."
                : "편집 중입니다. 지금 듣고 있는 과목을 과목 목록에서 모두 넣은 뒤 [시간표 저장 (확정)]을 누르세요.");
        summary.setText("전공: " + names(courses, CourseType.MAJOR) + "      교양: " + names(courses, CourseType.GENERAL));
        confirmButton.setVisible(!confirmed);
        canvas.setTimetable(me.getTimetable());
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
