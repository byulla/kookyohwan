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
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import static kookyohwan.gui.Session.ROW_GAP;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 내 시간표 탭(R2): 격자 그림 + 학수번호로 과목 추가. */
class TimetablePanel extends JPanel {
    private final Session session;
    private final TimetableCanvas canvas = new TimetableCanvas();
    private final JLabel title = new JLabel();
    private final JLabel summary = new JLabel();
    private final JTextField codeField = new JTextField(12);

    TimetablePanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);

        JButton addButton = new JButton("추가");
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, ROW_GAP));
        addRow.add(new JLabel("추가할 학수번호"));
        addRow.add(codeField);
        addRow.add(addButton);
        addRow.add(new JLabel("(과목 목록 탭에서 골라 넣을 수도 있습니다)"));
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(summary, BorderLayout.NORTH);
        bottom.add(addRow, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addByCode());
        codeField.addActionListener(e -> addByCode());
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        Student me = session.getMe();
        List<Course> courses = me.getTimetable().getCourses();
        title.setText("내 시간표   " + me.getName() + " (" + me.getId() + ")   " + courses.size() + "과목   "
                + me.getTimetable().getTotalCredits() + " / " + Timetable.MAX_CREDITS + "학점");
        summary.setText("전공: " + names(courses, CourseType.MAJOR) + "      교양: " + names(courses, CourseType.GENERAL));
        canvas.setTimetable(me.getTimetable());
    }

    private void addByCode() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            return;
        }
        Optional<Course> found = session.getCatalog().findByCode(code);
        if (found.isEmpty()) {
            TimetableActions.reject(this, session, code + ": 과목 목록에 없는 학수번호입니다.\n"
                    + "과목 목록 탭에서 검색해 확인하세요. 시간표를 변경하지 않았습니다.");
            return;
        }
        TimetableActions.add(this, session, found.get());
        codeField.setText("");
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
