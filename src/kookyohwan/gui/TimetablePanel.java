package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.Student;
import kookyohwan.model.Timetable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kookyohwan.gui.Session.ROW_GAP;

class TimetablePanel extends JPanel {
    private static final Color EDITING = new Color(0xB45309);

    private final Session session;
    private final TimetableCanvas canvas = new TimetableCanvas();
    private final JLabel title = new JLabel();
    private final JLabel mode = new JLabel();
    private final JLabel summary = new JLabel();
    private final JLabel addLabel = new JLabel();
    private final JTextField codeField = Theme.field(9);
    private final JButton addButton = Theme.secondary("수강신청");
    private final JComboBox<String> removeBox = Theme.combo();
    private final List<Course> removeCourses = new ArrayList<>();
    private final JButton removeButton = Theme.secondary("과목 삭제");
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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, ROW_GAP));
        actions.add(addLabel);
        actions.add(codeField);
        actions.add(addButton);
        actions.add(new JLabel("   삭제할 과목"));
        actions.add(removeBox);
        actions.add(removeButton);
        actions.add(confirmButton);
        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        summary.setForeground(Theme.MUTED);
        bottom.add(summary, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        addButton.addActionListener(e -> UiEvents.afterInput(this::addByCode));
        codeField.addActionListener(e -> UiEvents.afterInput(this::addByCode));
        removeButton.addActionListener(e -> UiEvents.afterInput(this::removeSelected));
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
        addLabel.setText(confirmed ? "수강신청할 학수번호" : "넣을 학수번호");
        addButton.setText(confirmed ? "수강신청" : "넣기");
        confirmButton.setVisible(!confirmed);

        removeCourses.clear();
        removeBox.removeAllItems();
        for (Course c : courses) {
            removeCourses.add(c);
            removeBox.addItem(c.getName() + " " + c.getSection());
        }
        removeButton.setEnabled(!removeCourses.isEmpty());
        canvas.setTimetable(me.getTimetable());
    }

    private void addByCode() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            return;
        }
        Optional<Course> found = session.getCatalog().findByCode(code);
        if (found.isEmpty()) {
            TimetableActions.reject(this, session, "없는 학수번호입니다.");
            return;
        }
        TimetableActions.add(this, session, found.get());
        codeField.setText("");
    }

    private void removeSelected() {
        int index = removeBox.getSelectedIndex();
        if (index >= 0 && index < removeCourses.size()) {
            TimetableActions.remove(this, session, removeCourses.get(index));
        }
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
