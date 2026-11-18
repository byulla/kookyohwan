package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.SwapRequest;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import static kookyohwan.gui.Session.ROW_GAP;
import java.util.ArrayList;
import java.util.List;

class MatchPanel extends JPanel {
    private final Session session;
    private final JComboBox<String> requestBox = Theme.combo();
    private final List<SwapRequest> waiting = new ArrayList<>();
    private final JButton runButton = Theme.primary("매칭 실행");
    private final JLabel info = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"요청", "요청자", "받을 과목", "판정", "사유"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JTextArea summary = new JTextArea(8, 60);
    private final JButton confirmButton = Theme.primary("선택한 상대와 교환 확정");
    private final List<MatchResult> shown = new ArrayList<>();
    private SwapRequest matched;

    MatchPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, ROW_GAP));
        pick.add(new JLabel("내 대기 요청"));
        pick.add(requestBox);
        pick.add(runButton);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(pick, BorderLayout.NORTH);
        info.setForeground(Theme.MUTED);
        top.add(info, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        Theme.table(table, null);
        int[] widths = {60, 90, 280, 80, 380};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(3).setCellRenderer(Theme.pill(null));
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setBackground(Theme.HEADER);
        summary.setForeground(Theme.TEXT);
        summary.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Theme.scroll(table), Theme.scroll(summary));
        split.setBorder(null);
        split.setDividerSize(8);
        split.setOpaque(false);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, ROW_GAP));
        bottom.add(Theme.muted("표에서 가능이나 겹침인 상대를 고른 뒤 누르세요."));
        bottom.add(confirmButton);
        add(bottom, BorderLayout.SOUTH);
        confirmButton.setEnabled(false);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateConfirmButton());
        confirmButton.addActionListener(e -> UiEvents.afterInput(this::confirmExchange));

        runButton.addActionListener(e -> runMatch());
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        waiting.clear();
        requestBox.removeAllItems();
        for (SwapRequest r : session.getBoard().waitingOf(session.getMe().getId())) {
            waiting.add(r);
            requestBox.addItem("#" + r.getId() + "  " + r.summary());
        }
        runButton.setEnabled(!waiting.isEmpty());
        model.setRowCount(0);
        shown.clear();
        matched = null;
        updateConfirmButton();
        info.setText(" ");
        summary.setText(waiting.isEmpty()
                ? "[안내] 대기 중인 내 교환 요청이 없습니다. 먼저 교환 요청 탭에서 요청을 등록하세요."
                : "요청을 고르고 [매칭 실행]을 누르세요.");
    }

    private void runMatch() {
        if (waiting.isEmpty() || requestBox.getSelectedIndex() < 0) {
            return;
        }
        SwapRequest mine = waiting.get(requestBox.getSelectedIndex());
        Course give = mine.getGiveCourse();
        List<MatchResult> results = session.getMatcher().match(mine, session.getMe().getTimetable());
        int others = session.getBoard().waitingExcept(session.getMe().getId()).size();

        model.setRowCount(0);
        shown.clear();
        matched = mine;
        info.setText("대기 중인 다른 요청 " + others + "건 중 조건이 맞는 후보 " + results.size() + "건"
                + (results.isEmpty() ? "" : " (조건 불일치 " + (others - results.size()) + "건은 후보 아님)"));
        if (results.isEmpty()) {
            summary.setText("[결과] 조건에 맞는 상대가 없습니다. 요청 #" + mine.getId() + ": 대기 상태로 유지됩니다.");
            session.say("[결과] 요청 #" + mine.getId() + ": 조건에 맞는 상대 없음");
            return;
        }
        List<MatchResult> possible = new ArrayList<>();
        for (MatchResult r : results) {
            shown.add(r);
            model.addRow(new Object[]{"#" + r.getRequest().getId(), session.nameOf(r.getRequest().getStudentId()),
                    Session.label(r.getReceiveCourse()), r.isResolvable() ? "겹침" : r.getVerdict().getLabel(),
                    r.isResolvable() ? r.getReason() + " (빼면 가능)" : r.getReason()});
            if (r.isPossible()) {
                possible.add(r);
            }
        }

        StringBuilder text = new StringBuilder("[결과] 교환 가능한 상대 " + possible.size() + "명\n");
        String myCourse = give.getName() + " (" + give.timesToString() + ")";
        for (int i = 0; i < possible.size(); i++) {
            SwapRequest other = possible.get(i).getRequest();
            Course receive = possible.get(i).getReceiveCourse();
            String who = session.nameOf(other.getStudentId());
            text.append("\n").append(i + 1).append(") ").append(who);
            if (!who.equals(other.getStudentId())) {
                text.append(" (").append(other.getStudentId()).append(")");
            }
            text.append("   내 ").append(myCourse).append("  <->  ").append(who).append(" ")
                    .append(Session.label(receive));
            text.append("\n     교환 후 내 시간표: ").append(give.timesToString()).append(" ").append(give.getName())
                    .append(" 삭제, ").append(receive.timesToString()).append(" ").append(receive.getName())
                    .append(" 추가\n");
        }
        int resolvable = 0;
        for (MatchResult r : results) {
            if (r.isResolvable()) {
                resolvable++;
            }
        }
        if (resolvable > 0) {
            text.append("\n겹침 ").append(resolvable).append("명: 겹치는 내 과목을 빼면 교환할 수 있습니다. 표에서 고르고 확정을 누르면 빠질 과목을 물어봅니다.\n");
        }
        if (possible.isEmpty() && resolvable == 0) {
            text.append("\n요청 #").append(mine.getId()).append(": 대기 상태로 유지됩니다.");
        } else if (!possible.isEmpty()) {
            text.append("\n두 사람이 합의하고 수강신청 시스템에서 정정했다면, 위 표에서 상대를 고르고 [선택한 상대와 교환 확정]을 누르세요."
                    + "\n(매칭 실행만으로는 요청 상태가 바뀌지 않습니다)");
        }
        summary.setText(text.toString());
        summary.setCaretPosition(0);
        session.say("[결과] 요청 #" + mine.getId() + ": 교환 가능한 상대 " + possible.size() + "명");
    }

    private void updateConfirmButton() {
        confirmButton.setEnabled(selectedResult() != null);
    }

    private MatchResult selectedResult() {
        int row = table.getSelectedRow();
        if (matched == null || row < 0 || row >= shown.size()) {
            return null;
        }
        MatchResult r = shown.get(row);
        return r.isPossible() || r.isResolvable() ? r : null;
    }

    private void confirmExchange() {
        MatchResult picked = selectedResult();
        if (picked == null) {
            return;
        }
        SwapRequest mine = matched;
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
        int answer = javax.swing.JOptionPane.showConfirmDialog(this, question,
                dropNames.isEmpty() ? "교환 확정" : "시간 겹침", javax.swing.JOptionPane.YES_NO_OPTION,
                dropNames.isEmpty() ? javax.swing.JOptionPane.QUESTION_MESSAGE
                        : javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.YES_OPTION) {
            session.say("[취소] 교환을 확정하지 않았습니다.");
            return;
        }
        List<SwapRequest> cancelled;
        try {
            cancelled = session.getExchange().confirm(mine, other, !dropNames.isEmpty());
        } catch (kookyohwan.service.RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
            return;
        }
        StringBuilder done = new StringBuilder("[등록] 교환 확정: " + who + " 님과 " + give.getName() + " <-> "
                + receive.getName() + ". 요청 #" + mine.getId() + ", #" + other.getId() + " 매칭완료");
        if (!dropNames.isEmpty()) {
            done.append("  / ").append(String.join(", ", dropNames)).append(" 삭제(시간 겹침)");
        }
        for (SwapRequest r : cancelled) {
            done.append("  / 요청 #").append(r.getId()).append(" 자동 취소");
        }
        session.fireChanged();
        session.say(done.toString());
        summary.setText(done + "\n\n내 시간표 탭에서 바뀐 시간표를, 교환 요청 탭에서 요청 상태를 확인하세요.");
    }
}
