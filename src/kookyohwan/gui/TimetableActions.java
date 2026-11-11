package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.Timetable;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 시간표에 과목 추가(R2). 과목 목록 탭과 내 시간표 탭이 같은 흐름을 쓰도록 한 곳에 둔다. */
final class TimetableActions {
    private TimetableActions() {
    }

    /** 콘솔 메뉴 2와 같은 순서: 이미 있음 거절 -> 겹침 없으면 추가 -> 겹치면 목록을 보여 주고 교체 확인. */
    static void add(Component parent, Session session, Course course) {
        Timetable table = session.getMe().getTimetable();
        if (table.contains(course.getCode())) {
            reject(parent, session, course.getName() + " " + course.getSection()
                    + ": 이미 시간표에 있습니다.\n시간표를 변경하지 않았습니다.");
            return;
        }
        List<Course> conflicts = table.findConflicts(course, null);
        if (table.exceedsLimit(course, conflicts)) { // 겹치는 과목을 빼고 넣어도 넘는 경우까지 여기서 거절
            reject(parent, session, course.getName() + " " + course.getSection() + ": 최대 " + Timetable.MAX_CREDITS
                    + "학점을 넘습니다.\n현재 " + table.getTotalCredits() + "학점, 이 과목(" + course.getCredits()
                    + "학점)을 넣으면 " + table.creditsAfter(course, conflicts) + "학점입니다.\n시간표를 변경하지 않았습니다.");
            return;
        }
        if (conflicts.isEmpty()) {
            table.add(course);
            session.say("[등록] " + course.getCode() + " " + Session.label(course) + " 추가. " + table.size() + "과목 "
                    + table.getTotalCredits() + "학점");
            session.fireChanged();
            return;
        }

        StringBuilder list = new StringBuilder();
        List<String> removedNames = new ArrayList<>();
        for (Course c : conflicts) {
            list.append("\n  - ").append(c.getCode()).append(" ").append(c.getName())
                    .append(" (").append(c.timesToString()).append(")");
            removedNames.add(c.getName());
        }
        // 빠질 과목이 내 대기 요청의 내놓는 과목이면 교체 대신 거절 (요청만 남고 과목이 사라지는 상태를 막는다)
        for (Course c : conflicts) {
            Optional<SwapRequest> blocking = session.getBoard().findGiving(session.getMe().getId(), c.getCode());
            if (blocking.isPresent()) {
                reject(parent, session, course.getCode() + " " + Session.label(course) + "\n다음 과목과 시간이 겹칩니다." + list
                        + "\n\n" + c.getName() + ": 요청 #" + blocking.get().getId()
                        + " 에서 이 과목을 내놓는 중입니다. 먼저 요청을 취소하세요.\n시간표를 변경하지 않았습니다.");
                return;
            }
        }
        int answer = JOptionPane.showConfirmDialog(parent,
                course.getCode() + " " + Session.label(course) + "\n다음 과목과 시간이 겹칩니다." + list
                        + "\n\n겹치는 과목을 빼고 이 과목을 넣을까요?",
                "시간 겹침", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 시간표를 변경하지 않았습니다.");
            return;
        }
        table.replace(conflicts, course);
        session.say("[등록] " + String.join(", ", removedNames) + " 삭제, " + course.getName() + " "
                + course.getSection() + " 추가. " + table.size() + "과목 " + table.getTotalCredits() + "학점");
        session.fireChanged();
    }

    static void reject(Component parent, Session session, String message) {
        session.say("[거절] " + message.replace('\n', ' '));
        JOptionPane.showMessageDialog(parent, message, "거절", JOptionPane.WARNING_MESSAGE);
    }
}
