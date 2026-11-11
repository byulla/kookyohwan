package kookyohwan.gui;

import kookyohwan.model.Student;
import kookyohwan.model.Timetable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 창의 내용 전체: 위에 앱 이름·메뉴 탭·현재 사용자, 가운데 고른 탭의 카드, 아래 상태 줄. */
public class MainPanel extends JPanel {
    private static final String[] TABS = {"내 시간표", "과목 목록", "교환 요청", "매칭"};

    private final Session session;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final List<Theme.NavButton> navButtons = new ArrayList<>();
    private final JLabel user = new JLabel();
    private final JLabel status = new JLabel(" ");

    public MainPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        // 위쪽 줄: 앱 이름 + 메뉴 탭 (왼쪽), 현재 사용자 + 전환·저장 (오른쪽)
        JLabel appName = new JLabel("구교환");
        appName.setFont(appName.getFont().deriveFont(Font.BOLD, 21f));
        appName.setForeground(Theme.ACCENT);
        appName.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 18));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(appName);
        for (int i = 0; i < TABS.length; i++) {
            Theme.NavButton nav = new Theme.NavButton(TABS[i]);
            int index = i;
            nav.addActionListener(e -> showTab(index));
            navButtons.add(nav);
            left.add(nav);
        }

        JButton switchButton = Theme.secondary("사용자 전환");
        JButton saveButton = Theme.secondary("저장");
        Theme.RoundPanel userChip = new Theme.RoundPanel(30, Theme.BACKGROUND, null);
        userChip.setLayout(new FlowLayout(FlowLayout.CENTER, 14, 7));
        userChip.add(user);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 11));
        right.setOpaque(false);
        right.add(userChip);
        right.add(switchButton);
        right.add(saveButton);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CARD);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), BorderFactory.createEmptyBorder(0, 16, 0, 12)));
        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // 가운데: 탭마다 흰 카드 하나
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 6, 18));
        JComponent[] panels = {new TimetablePanel(session), new CoursePanel(session),
                new RequestPanel(session), new MatchPanel(session)};
        for (int i = 0; i < panels.length; i++) {
            Theme.transparent(panels[i]);
            content.add(Theme.card(panels[i]), TABS[i]);
        }
        add(content, BorderLayout.CENTER);

        status.setForeground(Theme.MUTED);
        status.setBorder(BorderFactory.createEmptyBorder(4, 22, 10, 22));
        add(status, BorderLayout.SOUTH);

        session.setStatusSink(status::setText);
        session.addListener(this::refreshUser);
        switchButton.addActionListener(e -> UiEvents.afterInput(this::switchUser));
        saveButton.addActionListener(e -> UiEvents.afterInput(session::save));
        refreshUser();
        showTab(0);
    }

    /** index번째 탭을 보여 준다 (0 내 시간표, 1 과목 목록, 2 교환 요청, 3 매칭). */
    public void showTab(int index) {
        cards.show(content, TABS[index]);
        for (int i = 0; i < navButtons.size(); i++) {
            navButtons.get(i).setSelectedTab(i == index);
        }
    }

    private void refreshUser() {
        Student me = session.getMe();
        user.setText(me.getName() + "  ·  " + me.getId() + "  ·  " + me.getTimetable().getTotalCredits() + "/"
                + Timetable.MAX_CREDITS + "학점" + (me.isTimetableConfirmed() ? "" : "  ·  편집 중"));
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
