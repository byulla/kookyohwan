package kookyohwan.gui;

import kookyohwan.model.Student;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import static kookyohwan.gui.Session.ROW_GAP;
import java.awt.Font;
import java.util.Optional;

/** 창의 내용 전체: 위에 현재 사용자와 전환·저장 버튼, 가운데 탭 4개, 아래 상태 줄. */
public class MainPanel extends JPanel {
    private final Session session;
    private final JLabel user = new JLabel();
    private final JLabel status = new JLabel(" ");

    public MainPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout());

        JLabel appName = new JLabel("구교환 (求교환)");
        appName.setFont(appName.getFont().deriveFont(Font.BOLD, 17f));
        JButton switchButton = new JButton("사용자 전환");
        JButton saveButton = new JButton("저장");
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, ROW_GAP));
        left.add(appName);
        left.add(user);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, ROW_GAP));
        right.add(switchButton);
        right.add(saveButton);
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("내 시간표", new TimetablePanel(session));
        tabs.addTab("과목 목록", new CoursePanel(session));
        tabs.addTab("교환 요청", new RequestPanel(session));
        tabs.addTab("매칭", new MatchPanel(session));
        add(tabs, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        add(status, BorderLayout.SOUTH);

        session.setStatusSink(status::setText);
        session.addListener(this::refreshUser);
        switchButton.addActionListener(e -> switchUser());
        saveButton.addActionListener(e -> session.save());
        refreshUser();
    }

    private void refreshUser() {
        Student me = session.getMe();
        user.setText(me.getName() + " (" + me.getId() + ")  시간표 " + me.getTimetable().size() + "과목 "
                + me.getTimetable().getTotalCredits() + "학점");
    }

    /** 저장 먼저 -> 학번 다시 입력 (콘솔 메뉴 9와 같은 순서). */
    private void switchUser() {
        session.save();
        Optional<Student> next = LoginGate.ask(this, session.getRegistry());
        if (next.isEmpty()) {
            session.say("[취소] 사용자를 바꾸지 않았습니다.");
            return;
        }
        session.setMe(next.get());
        session.say("[확인] " + next.get().getName() + " 님의 시간표를 불러왔습니다. ("
                + next.get().getTimetable().size() + "과목)");
    }
}
