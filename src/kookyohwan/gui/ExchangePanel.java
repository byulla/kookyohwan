package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CycleMatch;
import kookyohwan.model.MatchResult;
import kookyohwan.model.SwapRequest;
import kookyohwan.service.RequestRejectedException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class ExchangePanel extends JPanel {
    private static final int LEFT_WIDTH = 390;
    private static final int MAX_CHOICES = 30;

    private final Session session;
    private final CardList requestList = new CardList();
    private final CardList resultList = new CardList();
    private final JComboBox<String> giveBox = Theme.combo();
    private final List<Course> giveCourses = new ArrayList<>();
    private final JTextField wantField = Theme.field(16);
    private final JButton registerButton = Theme.primary("요청 등록");
    private final JLabel formNote = Theme.muted(" ");
    private int selectedId = -1;
    private String flash;

    ExchangePanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(14, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints at = new GridBagConstraints();
        at.anchor = GridBagConstraints.WEST;
        at.insets = new Insets(4, 0, 4, 8);
        at.gridy = 0;
        at.gridwidth = 2;
        JLabel formTitle = new JLabel("새 요청");
        formTitle.setFont(formTitle.getFont().deriveFont(Font.BOLD, 14f));
        form.add(formTitle, at);
        at.gridwidth = 1;
        at.gridy = 1;
        form.add(new JLabel("내놓을 과목"), at);
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        at.insets = new Insets(4, 0, 4, 0);
        form.add(giveBox, at);
        at.gridy = 2;
        at.fill = GridBagConstraints.NONE;
        at.weightx = 0;
        at.insets = new Insets(4, 0, 4, 8);
        form.add(new JLabel("희망 과목"), at);
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        at.insets = new Insets(4, 0, 4, 0);
        form.add(wantField, at);
        at.gridy = 3;
        at.gridx = 0;
        at.gridwidth = 2;
        form.add(registerButton, at);
        at.gridy = 4;
        form.add(formNote, at);

        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.add(Theme.title("내 교환 요청"), BorderLayout.NORTH);
        left.add(scrollOf(requestList), BorderLayout.CENTER);
        left.add(form, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(LEFT_WIDTH, 0));
        add(left, BorderLayout.WEST);
        add(scrollOf(resultList), BorderLayout.CENTER);

        registerButton.addActionListener(e -> UiEvents.afterInput(this::register));
        wantField.addActionListener(e -> UiEvents.afterInput(this::register));
        session.addListener(this::refresh);
        refresh();
    }

    void prefillWant(String wantName) {
        wantField.setText(wantName);
        wantField.requestFocusInWindow();
    }

    private static JScrollPane scrollOf(CardList list) {
        JScrollPane scroll = Theme.scroll(list);
        scroll.getViewport().setBackground(Theme.HEADER);
        return scroll;
    }

    private void refresh() {
        List<SwapRequest> waiting = session.getBoard().waitingOf(session.getMe().getId());
        boolean found = false;
        for (SwapRequest r : waiting) {
            found |= r.getId() == selectedId;
        }
        if (!found) {
            selectedId = waiting.isEmpty() ? -1 : waiting.get(0).getId();
        }
        showRequests(waiting);
        showForm();
        showResults();
    }

    private void select(int requestId) {
        selectedId = requestId;
        flash = null;
        refresh();
    }

    private void showRequests(List<SwapRequest> waiting) {
        requestList.removeAll();
        if (waiting.isEmpty()) {
            requestList.add(note(session.getMe().isTimetableConfirmed()
                    ? "대기 중인 요청이 없습니다. 아래에서 내놓을 과목과 희망 과목을 넣고 등록하세요."
                    : "시간표를 확정한 뒤에 교환 요청을 등록할 수 있습니다. [내 시간표] 탭에서 확정하세요."));
        }
        for (SwapRequest r : waiting) {
            if (requestList.getComponentCount() > 0) {
                requestList.add(Box.createVerticalStrut(8));
            }
            requestList.add(left(requestCard(r)));
        }
        requestList.refreshView(false);
    }

    private RoundCard requestCard(SwapRequest r) {
        RoundCard card = new RoundCard(r.getId() == selectedId, true);
        card.setLayout(new BorderLayout(10, 0));
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel body = column();
        JLabel give = new JLabel(Session.label(r.getGiveCourse()));
        give.setFont(give.getFont().deriveFont(Font.BOLD));
        give.setForeground(Theme.TEXT);
        body.add(left(give));
        body.add(Box.createVerticalStrut(4));
        body.add(left(html("→ " + Theme.escapeHtml(r.getWantName())
                + "&nbsp;&nbsp;<span style='color:" + hex(Theme.MUTED) + "'>#" + r.getId() + "</span>")));
        card.add(body, BorderLayout.CENTER);

        int possible = 0;
        int resolvable = 0;
        for (MatchResult m : session.getMatcher().match(r, session.getMe().getTimetable())) {
            possible += m.isPossible() ? 1 : 0;
            resolvable += m.isResolvable() ? 1 : 0;
        }
        for (CycleMatch c : session.getMatcher().matchCycles(r, session.getMe().getTimetable())) {
            possible += c.isPossible() ? 1 : 0;
        }
        Chip count = possible > 0 ? new Chip("가능 " + possible + "건", Theme.GREEN_SOFT, Theme.GREEN)
                : resolvable > 0 ? new Chip("겹침 " + resolvable + "건", Theme.AMBER_SOFT, Theme.AMBER)
                : new Chip("상대 없음", Theme.GRAY_SOFT, Theme.MUTED);
        JLabel cancel = new JLabel("요청 취소");
        cancel.setForeground(Theme.MUTED);
        cancel.setFont(cancel.getFont().deriveFont(12f));
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                UiEvents.afterInput(() -> cancel(r));
            }
        });
        JPanel side = column();
        count.setAlignmentX(RIGHT_ALIGNMENT);
        cancel.setAlignmentX(RIGHT_ALIGNMENT);
        side.add(count);
        side.add(Box.createVerticalStrut(6));
        side.add(cancel);
        card.add(side, BorderLayout.EAST);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                SwingUtilities.invokeLater(() -> select(r.getId()));
            }
        });
        return card;
    }

    private void showForm() {
        giveBox.removeAllItems();
        giveCourses.clear();
        for (Course c : session.getMe().getTimetable().getCourses()) {
            if (c.isGeneral()) {
                giveCourses.add(c);
                giveBox.addItem(Session.label(c));
            }
        }
        boolean confirmed = session.getMe().isTimetableConfirmed();
        boolean ready = confirmed && !giveCourses.isEmpty();
        giveBox.setEnabled(ready);
        wantField.setEnabled(ready);
        registerButton.setEnabled(ready);
        formNote.setText(!confirmed ? "시간표를 확정해야 요청을 등록할 수 있습니다."
                : giveCourses.isEmpty() ? "시간표에 교양 과목이 없습니다. 교양 과목만 교환할 수 있습니다."
                : "과목명 일부나 학수번호를 넣어도 됩니다. 분반은 상관없습니다.");
    }

    private void showResults() {
        resultList.removeAll();
        if (flash != null) {
            Theme.RoundPanel banner = new Theme.RoundPanel(12, Theme.GREEN_SOFT, null);
            banner.setLayout(new BorderLayout());
            banner.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            JTextArea text = note(flash);
            text.setForeground(Theme.GREEN);
            banner.add(text);
            resultList.add(left(banner));
            resultList.add(Box.createVerticalStrut(12));
        }
        SwapRequest mine = selectedId < 0 ? null : session.getBoard().find(selectedId).orElse(null);
        if (mine == null) {
            resultList.add(left(Theme.title("교환 상대")));
            resultList.add(Box.createVerticalStrut(4));
            resultList.add(note("왼쪽에서 요청을 등록하면 교환할 수 있는 상대가 여기에 바로 나옵니다."));
            resultList.refreshView(true);
            return;
        }
        Course give = mine.getGiveCourse();
        List<MatchResult> results = session.getMatcher().match(mine, session.getMe().getTimetable());
        List<CycleMatch> cycles = session.getMatcher().matchCycles(mine, session.getMe().getTimetable());
        List<MatchResult> possible = new ArrayList<>();
        List<MatchResult> resolvable = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (MatchResult r : results) {
            if (r.isPossible()) {
                possible.add(r);
            } else if (r.isResolvable()) {
                resolvable.add(r);
            } else {
                excluded.add(excludedLine(session.nameOf(r.getRequest().getStudentId()), r.getReason()));
            }
        }
        List<CycleMatch> possibleCycles = new ArrayList<>();
        for (CycleMatch c : cycles) {
            if (c.isPossible()) {
                possibleCycles.add(c);
            } else {
                excluded.add(excludedLine(partnersOf(c), reasonOf(c)));
            }
        }
        int ready = possible.size() + possibleCycles.size();

        resultList.add(left(Theme.title(give.getName() + " → " + mine.getWantName())));
        resultList.add(Box.createVerticalStrut(4));
        resultList.add(left(Theme.muted(ready > 0 ? "교환 가능 " + ready + "건. 상대와 합의하고 수강신청 시스템에서 정정했다면 [교환 확정]을 누르세요."
                : !resolvable.isEmpty() ? "바로 교환할 수 있는 상대는 없습니다. 겹치는 내 과목을 빼면 가능한 상대가 있습니다."
                : "아직 조건에 맞는 상대가 없습니다. 요청은 대기 상태로 남습니다.")));

        String myCourse = give.getName() + " (" + give.timesToString() + ")";
        for (MatchResult r : possible) {
            SwapRequest other = r.getRequest();
            String who = session.nameOf(other.getStudentId());
            List<String> lines = new ArrayList<>();
            lines.add(detail("주는 과목", myCourse, "→ " + who));
            lines.add(detail("받는 과목", Session.label(r.getReceiveCourse()), "← " + who));
            addCard(who, who.equals(other.getStudentId()) ? "" : other.getStudentId(),
                    Theme.muted("둘이서 교환"), lines,
                    button(Theme.primary("교환 확정"), () -> confirmPair(mine, r)));
        }
        for (CycleMatch c : possibleCycles) {
            List<SwapRequest> requests = c.getRequests();
            List<String> lines = new ArrayList<>();
            lines.add(detail("주는 과목", myCourse,
                    "→ " + session.nameOf(requests.get(requests.size() - 1).getStudentId())));
            lines.add(detail("받는 과목", Session.label(c.receiveCourseOf(0)),
                    "← " + session.nameOf(requests.get(1).getStudentId())));
            for (int i = requests.size() - 1; i > 1; i--) {
                lines.add(detail("함께 도는 과목", Session.label(requests.get(i).getGiveCourse()),
                        session.nameOf(requests.get(i).getStudentId()) + " → "
                                + session.nameOf(requests.get(i - 1).getStudentId())));
            }
            addCard(partnersOf(c), "", Theme.muted("세 명이 함께"), lines,
                    button(Theme.primary("교환 확정"), () -> confirmCycle(c)));
        }
        for (MatchResult r : resolvable) {
            SwapRequest other = r.getRequest();
            String who = session.nameOf(other.getStudentId());
            List<String> drops = new ArrayList<>();
            for (Course c : r.getDroppable()) {
                drops.add(c.getName());
            }
            List<String> lines = new ArrayList<>();
            lines.add(detail("주는 과목", myCourse, "→ " + who));
            lines.add(detail("받는 과목", Session.label(r.getReceiveCourse()), "← " + who));
            lines.add("<html><span style='color:" + hex(Theme.AMBER) + "'>" + Theme.escapeHtml(String.join(", ", drops))
                    + " 과목과 시간이 겹칩니다. 이 과목을 빼면 교환할 수 있습니다.</span></html>");
            addCard(who, who.equals(other.getStudentId()) ? "" : other.getStudentId(),
                    tinted("시간 겹침", Theme.AMBER), lines,
                    button(Theme.secondary("빼고 교환"), () -> confirmPair(mine, r)));
        }
        if (!excluded.isEmpty()) {
            resultList.add(Box.createVerticalStrut(16));
            JLabel head = Theme.muted("교환할 수 없는 후보 " + excluded.size() + "건");
            head.setFont(head.getFont().deriveFont(Font.BOLD));
            resultList.add(left(head));
            for (String line : excluded) {
                resultList.add(Box.createVerticalStrut(5));
                resultList.add(left(html(line)));
            }
        }
        resultList.refreshView(true);
    }

    private String excludedLine(String who, String reason) {
        return "<span style='color:" + hex(Theme.TEXT) + "'>" + Theme.escapeHtml(who) + "</span>&nbsp;&nbsp;&nbsp;"
                + "<span style='color:" + hex(Theme.MUTED) + "'>" + Theme.escapeHtml(reason) + "</span>";
    }

    private void addCard(String who, String idNote, JLabel tag, List<String> lines, JButton action) {
        RoundCard card = new RoundCard(false, false);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 14));

        JPanel body = column();
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        head.setOpaque(false);
        JLabel name = new JLabel(who);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 14f));
        name.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        name.setForeground(Theme.TEXT);
        head.add(name);
        if (!idNote.isEmpty()) {
            JLabel id = Theme.muted(idNote);
            id.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
            head.add(id);
        }
        head.add(tag);
        body.add(left(head));
        for (String line : lines) {
            body.add(Box.createVerticalStrut(6));
            JLabel label = new JLabel(line);
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
            label.setForeground(Theme.TEXT);
            body.add(left(label));
        }
        card.add(body, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(action);
        card.add(right, BorderLayout.EAST);

        resultList.add(Box.createVerticalStrut(10));
        resultList.add(left(card));
    }

    private static JLabel tinted(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }

    private static JButton button(JButton button, Runnable action) {
        button.addActionListener(e -> UiEvents.afterInput(action));
        return button;
    }

    private String partnersOf(CycleMatch c) {
        List<String> names = new ArrayList<>();
        for (SwapRequest r : c.getRequests().subList(1, c.getRequests().size())) {
            names.add(session.nameOf(r.getStudentId()));
        }
        return String.join(", ", names);
    }

    private List<String> flowOf(CycleMatch c) {
        List<SwapRequest> requests = c.getRequests();
        List<String> lines = new ArrayList<>();
        for (int k = 0; k < requests.size(); k++) {
            int giver = (requests.size() - k) % requests.size();
            int taker = (giver + requests.size() - 1) % requests.size();
            Course course = requests.get(giver).getGiveCourse();
            lines.add((giver == 0 ? "내 " + course.getName() + " (" + course.timesToString() + ")"
                    : session.nameOf(requests.get(giver).getStudentId()) + " " + Session.label(course))
                    + " -> " + (taker == 0 ? "나" : session.nameOf(requests.get(taker).getStudentId())));
        }
        return lines;
    }

    private String reasonOf(CycleMatch c) {
        String reason = c.getReason();
        for (SwapRequest r : c.getRequests()) {
            reason = reason.replace("#" + r.getId() + " ", session.nameOf(r.getStudentId()) + " 님 ");
        }
        return reason;
    }

    private void register() {
        if (giveCourses.isEmpty() || giveBox.getSelectedIndex() < 0) {
            return;
        }
        Course give = giveCourses.get(giveBox.getSelectedIndex());
        String input = wantField.getText().trim();
        if (input.isEmpty()) {
            wantField.requestFocusInWindow();
            session.say("[안내] 희망 과목을 입력하세요.");
            return;
        }
        String wantName = resolveWant(input);
        if (wantName == null) {
            return;
        }
        try {
            session.getBoard().validate(session.getMe(), give, wantName);
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
            return;
        }
        int open = 0;
        for (Course c : session.getCatalog().findByName(wantName)) {
            if (!session.getEnrollment().isFull(c)) {
                open++;
            }
        }
        if (open > 0) {
            int answer = JOptionPane.showConfirmDialog(this, wantName + " 과목은 빈자리 있는 분반이 " + open
                            + "개 있어 바로 수강신청할 수 있습니다.\n그래도 교환 요청을 등록할까요?",
                    "교환 요청 등록", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                session.say("[취소] 요청을 등록하지 않았습니다.");
                return;
            }
        }
        try {
            SwapRequest request = session.getBoard().register(session.getMe(), give, wantName);
            wantField.setText("");
            selectedId = request.getId();
            flash = null;
            session.fireChanged();
            session.say("[등록] 요청 #" + request.getId() + " 등록 완료  상태: " + request.getStatus().getLabel()
                    + "   " + request.summary());
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
        }
    }

    private String resolveWant(String input) {
        Optional<String> exact = session.getCatalog().resolveName(input);
        if (exact.isPresent()) {
            return exact.get();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Course c : session.getCatalog().search(input)) {
            if (c.isGeneral() && c.getName().contains(input) && names.size() < MAX_CHOICES) {
                names.add(c.getName());
            }
        }
        if (names.isEmpty()) {
            return input;
        }
        if (names.size() == 1) {
            return names.iterator().next();
        }
        Object picked = JOptionPane.showInputDialog(this, "'" + input + "'에 맞는 과목이 여러 개입니다. 희망 과목을 고르세요.",
                "희망 과목 선택", JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.iterator().next());
        if (picked == null) {
            session.say("[취소] 요청을 등록하지 않았습니다.");
            return null;
        }
        return (String) picked;
    }

    private void cancel(SwapRequest request) {
        int answer = JOptionPane.showConfirmDialog(this,
                "요청 #" + request.getId() + " (" + request.getGiveCourse().getName() + " -> " + request.getWantName()
                        + ")을 취소할까요?", "요청 취소", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 요청 목록을 변경하지 않았습니다.");
            return;
        }
        session.getBoard().cancel(request.getId());
        flash = null;
        session.fireChanged();
        session.say("[등록] 요청 #" + request.getId() + " 취소 완료");
    }

    private void confirmPair(SwapRequest mine, MatchResult picked) {
        SwapRequest other = picked.getRequest();
        List<String> dropNames = new ArrayList<>();
        for (Course c : picked.getDroppable()) {
            dropNames.add(c.getName());
        }
        Course give = mine.getGiveCourse();
        Course receive = other.getGiveCourse();
        String who = session.nameOf(other.getStudentId());

        String question = dropNames.isEmpty()
                ? who + " 님과 교환을 확정할까요?\n" + give.getName() + " <-> " + receive.getName()
                : String.join(", ", dropNames) + " 과목과 시간이 겹칩니다.\n빼고 " + who + " 님과 교환할까요?";
        int answer = JOptionPane.showConfirmDialog(this, question,
                dropNames.isEmpty() ? "교환 확정" : "시간 겹침", JOptionPane.YES_NO_OPTION,
                dropNames.isEmpty() ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 교환을 확정하지 않았습니다.");
            return;
        }
        List<SwapRequest> cancelled;
        try {
            cancelled = session.getExchange().confirm(mine, other, !dropNames.isEmpty());
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
            return;
        }
        StringBuilder done = new StringBuilder("교환 확정: " + who + " 님과 " + give.getName() + " <-> "
                + receive.getName() + ". 요청 #" + mine.getId() + ", #" + other.getId() + " 매칭완료");
        if (!dropNames.isEmpty()) {
            done.append("  / ").append(String.join(", ", dropNames)).append(" 삭제(시간 겹침)");
        }
        finish(done, cancelled);
    }

    private void confirmCycle(CycleMatch picked) {
        List<String> ids = new ArrayList<>();
        for (SwapRequest r : picked.getRequests()) {
            ids.add("#" + r.getId());
        }
        String who = partnersOf(picked);
        int answer = JOptionPane.showConfirmDialog(this,
                who + " 님과 교환을 확정할까요?\n" + String.join("\n", flowOf(picked)), "교환 확정",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 교환을 확정하지 않았습니다.");
            return;
        }
        List<SwapRequest> cancelled;
        try {
            cancelled = session.getExchange().confirmCycle(picked.getRequests());
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
            return;
        }
        finish(new StringBuilder("교환 확정: " + who + " 님과 "
                + picked.getRequests().get(0).getGiveCourse().getName() + " 주고 " + picked.receiveCourseOf(0).getName()
                + " 받음. 요청 " + String.join(", ", ids) + " 매칭완료"), cancelled);
    }

    private void finish(StringBuilder done, List<SwapRequest> cancelled) {
        for (SwapRequest r : cancelled) {
            done.append("  / 요청 #").append(r.getId()).append(" 자동 취소");
        }
        flash = done + "\n바뀐 시간표는 [내 시간표] 탭에서 볼 수 있습니다.";
        session.fireChanged();
        session.say("[등록] " + done);
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static <T extends JComponent> T left(T component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        return component;
    }

    private static JTextArea note(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(Theme.MUTED);
        return left(area);
    }

    private static JLabel html(String body) {
        JLabel label = new JLabel("<html>" + body + "</html>");
        label.setFont(label.getFont().deriveFont(Font.PLAIN));
        return label;
    }

    private static String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    private static String detail(String label, String course, String tail) {
        return "<html><span style='color:" + hex(Theme.MUTED) + "'>" + Theme.escapeHtml(label) + "</span>&nbsp;&nbsp;&nbsp;<b>"
                + Theme.escapeHtml(course) + "</b>&nbsp;&nbsp;&nbsp;" + Theme.escapeHtml(tail) + "</html>";
    }

    private static final class CardList extends JPanel implements Scrollable {
        CardList() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Theme.HEADER);
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                }
            });
        }

        void refreshView(boolean toTop) {
            revalidate();
            repaint();
            if (toTop) {
                SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0, 0, 1, 1)));
            }
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(400, 220);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class RoundCard extends JPanel {
        private final boolean selected;

        RoundCard(boolean selected, boolean clickable) {
            this.selected = selected;
            setOpaque(false);
            if (clickable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(selected ? Theme.ROW_MINE : Theme.CARD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(selected ? Theme.BLUE : Theme.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.dispose();
        }
    }

    private static final class Chip extends JLabel {
        private final Color fill;

        Chip(String text, Color fill, Color foreground) {
            super(text);
            this.fill = fill;
            setForeground(foreground);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
