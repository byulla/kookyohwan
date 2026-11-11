package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.Timetable;
import kookyohwan.service.RequestRejectedException;

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
            reject(parent, session, "이미 시간표에 있는 과목입니다.");
            return;
        }
        List<Course> conflicts = table.findConflicts(course, null);
        // 같은 과목의 다른 분반을 이미 듣는 경우. 시간이 겹치는 분반이면 아래 교체 흐름으로 분반을 바꿀 수 있다
        Optional<Course> same = table.findSameSubject(course, conflicts);
        if (same.isPresent()) {
            reject(parent, session, "이미 듣는 과목입니다.\n(" + same.get().getSection() + " 분반)");
            return;
        }
        try { // 시간표를 확정한 뒤에는 마감된 과목을 바로 넣을 수 없다 -> 교환 요청으로
            session.getEnrollment().validateEnroll(session.getMe(), course);
        } catch (RequestRejectedException e) {
            reject(parent, session, "정원이 마감된 과목입니다.\n교환 요청을 이용하세요.");
            return;
        }
        if (table.exceedsLimit(course, conflicts)) { // 겹치는 과목을 빼고 넣어도 넘는 경우까지 여기서 거절
            reject(parent, session, Timetable.MAX_CREDITS + "학점을 넘을 수 없습니다.\n(현재 " + table.getTotalCredits()
                    + "학점, 넣으면 " + table.creditsAfter(course, conflicts) + "학점)");
            return;
        }
        if (conflicts.isEmpty()) {
            table.add(course);
            session.say("[등록] " + course.getCode() + " " + Session.label(course) + " 추가. " + table.size() + "과목 "
                    + table.getTotalCredits() + "학점");
            session.fireChanged();
            return;
        }

        List<String> removedNames = new ArrayList<>();
        for (Course c : conflicts) {
            removedNames.add(c.getName());
        }
        // 빠질 과목이 내 대기 요청의 내놓는 과목이면 교체 대신 거절 (요청만 남고 과목이 사라지는 상태를 막는다)
        for (Course c : conflicts) {
            Optional<SwapRequest> blocking = session.getBoard().findGiving(session.getMe().getId(), c.getCode());
            if (blocking.isPresent()) {
                reject(parent, session, "교환 요청 중인 " + c.getName() + " 과목과 겹칩니다.\n요청 #"
                        + blocking.get().getId() + " 취소 후 다시 시도하세요.");
                return;
            }
        }
        int answer = JOptionPane.showConfirmDialog(parent,
                String.join(", ", removedNames) + " 과목과 시간이 겹칩니다.\n빼고 넣을까요?",
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

    /** 수강 취소(과목 빼기). 내 대기 요청이 내놓는 과목은 뺄 수 없다. */
    static void remove(Component parent, Session session, Course course) {
        Optional<SwapRequest> blocking = session.getBoard().findGiving(session.getMe().getId(), course.getCode());
        if (blocking.isPresent()) {
            reject(parent, session, "교환 요청 중인 과목입니다.\n요청 #" + blocking.get().getId() + " 취소 후 다시 시도하세요.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(parent,
                course.getName() + " 과목을 뺄까요?",
                "과목 빼기", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 시간표를 변경하지 않았습니다.");
            return;
        }
        Timetable table = session.getMe().getTimetable();
        table.remove(course);
        session.say("[등록] " + course.getName() + " " + course.getSection() + " 삭제. " + table.size() + "과목 "
                + table.getTotalCredits() + "학점");
        session.fireChanged();
    }

    /** 시간표 확정(저장). 되돌릴 수 없으므로 확인을 받는다. 확정하면 파일에도 바로 저장한다. */
    static void confirmTimetable(Component parent, Session session) {
        Timetable table = session.getMe().getTimetable();
        if (table.size() == 0) {
            JOptionPane.showMessageDialog(parent, "시간표가 비어 있습니다.", "안내",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(parent,
                "시간표를 확정할까요?\n확정 후에는 되돌릴 수 없습니다.",
                "시간표 저장", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 시간표를 확정하지 않았습니다.");
            return;
        }
        session.getRegistry().confirmTimetable(session.getMe().getId());
        session.fireChanged();
        session.save();
    }

    /** 거절 사유 중 첫 문장만. 서비스의 사유에는 안내 문장이 붙어 있어 창에 그대로 띄우기엔 길다. */
    static String brief(String reason) {
        int end = reason.indexOf(". ");
        return (end < 0 ? reason : reason.substring(0, end)) + ".";
    }

    /** 거절 알림 창. 창에는 한두 줄만 보여 준다. */
    static void reject(Component parent, Session session, String message) {
        session.say("[거절] " + message.replace('\n', ' '));
        JOptionPane.showMessageDialog(parent, message, "거절", JOptionPane.WARNING_MESSAGE);
    }
}
