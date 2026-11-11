package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.SwapRequest;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import static kookyohwan.gui.Session.ROW_GAP;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/** 매칭 탭(R4·R5·R5-2): 내 대기 요청을 골라 매칭을 돌리고 가능/제외와 사유를 본다. 조회 전용. */
class MatchPanel extends JPanel {
    private static final Color POSSIBLE = new Color(0x1B7F3B);
    private static final Color EXCLUDED = new Color(0xC0392B);

    private final Session session;
    private final JComboBox<String> requestBox = new JComboBox<>();
    private final List<SwapRequest> waiting = new ArrayList<>(); // requestBox의 항목 순서와 같은 요청
    private final JButton runButton = new JButton("매칭 실행");
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
        top.add(info, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        int[] widths = {50, 90, 280, 60, 380};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() { // 판정 색
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                if (!selected) {
                    c.setForeground("가능".equals(value) ? POSSIBLE : EXCLUDED);
                }
                return c;
            }
        });
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(summary));
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);

        runButton.addActionListener(e -> runMatch());
        session.addListener(this::refresh);
        refresh();
    }

    /** 요청이나 사용자가 바뀌면 고를 목록을 다시 채우고, 지난 결과는 지운다 (낡은 결과를 보고 판단하지 않게). */
    private void refresh() {
        waiting.clear();
        requestBox.removeAllItems();
        for (SwapRequest r : session.getBoard().waitingOf(session.getMe().getId())) {
            waiting.add(r);
            requestBox.addItem("#" + r.getId() + "  " + r.summary());
        }
        runButton.setEnabled(!waiting.isEmpty());
        model.setRowCount(0);
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
        info.setText("대기 중인 다른 요청 " + others + "건 중 조건이 맞는 후보 " + results.size() + "건"
                + (results.isEmpty() ? "" : " (조건 불일치 " + (others - results.size()) + "건은 후보 아님)"));
        if (results.isEmpty()) {
            summary.setText("[결과] 조건에 맞는 상대가 없습니다. 요청 #" + mine.getId() + " 은(는) 대기 상태로 유지됩니다.");
            session.say("[결과] 요청 #" + mine.getId() + ": 조건에 맞는 상대 없음");
            return;
        }
        List<MatchResult> possible = new ArrayList<>();
        for (MatchResult r : results) {
            model.addRow(new Object[]{"#" + r.getRequest().getId(), session.nameOf(r.getRequest().getStudentId()),
                    Session.label(r.getReceiveCourse()), r.getVerdict().getLabel(), r.getReason()});
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
        if (possible.isEmpty()) {
            text.append("\n요청 #").append(mine.getId()).append(" 은(는) 대기 상태로 유지됩니다.");
        } else {
            text.append("\n두 사람이 합의한 뒤 수강신청 시스템에서 직접 정정하세요.\n(매칭 실행은 요청 상태를 바꾸지 않습니다)");
        }
        summary.setText(text.toString());
        summary.setCaretPosition(0);
        session.say("[결과] 요청 #" + mine.getId() + ": 교환 가능한 상대 " + possible.size() + "명");
    }
}
