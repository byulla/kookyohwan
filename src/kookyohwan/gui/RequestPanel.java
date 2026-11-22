package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CycleMatch;
import kookyohwan.model.MatchResult;
import kookyohwan.model.RequestStatus;
import kookyohwan.model.SwapRequest;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

class RequestPanel extends JPanel {
    private final Session session;
    private final JLabel title = new JLabel();
    private final JLabel guide = Theme.muted(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"번호", "요청자", "내놓는 과목", "희망 과목", "상태", ""}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final List<SwapRequest> rows = new ArrayList<>();
    private final List<Boolean> wantsMine = new ArrayList<>();
    private final JTable table = new JTable(model) {
        @Override
        public String getToolTipText(MouseEvent e) {
            int row = rowAtPoint(e.getPoint());
            return row < 0 || row >= rows.size() ? null : tipFor(rows.get(row));
        }
    };

    RequestPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JPanel head = new JPanel(new GridLayout(2, 1, 0, 4));
        head.add(title);
        head.add(guide);
        add(head, BorderLayout.NORTH);

        Theme.table(table, row -> row < wantsMine.size() && wantsMine.get(row));
        int[] widths = {60, 90, 300, 260, 90, 100};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(4).setCellRenderer(Theme.pill(row -> row < wantsMine.size() && wantsMine.get(row)));
        table.getColumnModel().getColumn(5).setCellRenderer(Theme.pill(row -> row < wantsMine.size() && wantsMine.get(row)));
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(table);
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(15000);
        add(Theme.scroll(table), BorderLayout.CENTER);

        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        List<SwapRequest> requests = session.getBoard().all();
        List<SwapRequest> first = new ArrayList<>();
        List<SwapRequest> rest = new ArrayList<>();
        int waiting = 0;
        for (SwapRequest r : requests) {
            waiting += r.isWaiting() ? 1 : 0;
            (wantedCourse(r) != null ? first : rest).add(r);
        }
        rows.clear();
        rows.addAll(first);
        rows.addAll(rest);
        model.setRowCount(0);
        wantsMine.clear();
        for (SwapRequest r : rows) {
            boolean isMine = r.getStudentId().equals(session.getMe().getId());
            boolean wanted = first.contains(r);
            wantsMine.add(wanted);
            model.addRow(new Object[]{"#" + r.getId(), session.nameOf(r.getStudentId()),
                    Session.label(r.getGiveCourse()), r.getWantName(), r.getStatus().getLabel(),
                    isMine ? "내 요청" : wanted ? "내 과목 희망" : ""});
        }
        title.setText("전체 교환 요청   총 " + requests.size() + "건 (대기 " + waiting + ")");
        guide.setText((first.isEmpty() ? "내 과목을 희망하는 요청은 아직 없습니다."
                : "색칠한 " + first.size() + "건은 내 과목을 희망하는 요청입니다.")
                + " 줄에 마우스를 올리면 그 요청과 교환할 방법을 알려 줍니다.");
    }

    private Course wantedCourse(SwapRequest r) {
        if (!r.isWaiting() || r.getStudentId().equals(session.getMe().getId())) {
            return null;
        }
        for (Course c : session.getMe().getTimetable().getCourses()) {
            if (c.isGeneral() && c.getName().equals(r.getWantName())) {
                return c;
            }
        }
        return null;
    }

    private SwapRequest suppose(Course give, SwapRequest target) {
        return new SwapRequest(0, session.getMe().getId(), give, target.getGiveCourse().getName(),
                RequestStatus.WAITING);
    }

    private String tipFor(SwapRequest r) {
        if (!r.isWaiting() || r.getStudentId().equals(session.getMe().getId())) {
            return null;
        }
        String receive = r.getGiveCourse().getName();
        Course wanted = wantedCourse(r);
        if (wanted != null) {
            for (MatchResult m : session.getMatcher().match(suppose(wanted, r), session.getMe().getTimetable())) {
                if (m.getRequest() != r) {
                    continue;
                }
                List<String> drops = new ArrayList<>();
                for (Course c : m.getDroppable()) {
                    drops.add(c.getName());
                }
                String head = m.isPossible() ? "둘이서 교환 가능"
                        : m.isResolvable() ? "둘이서 교환 가능 (" + String.join(", ", drops) + " 빼야 함)"
                        : "교환 불가: " + m.getReason();
                return tip(head, wanted.getName() + " 주고 → " + receive + " 받기");
            }
            return null;
        }
        List<String> ways = new ArrayList<>();
        for (Course give : session.getMe().getTimetable().getCourses()) {
            if (!give.isGeneral()) {
                continue;
            }
            for (CycleMatch c : session.getMatcher().matchCycles(suppose(give, r), session.getMe().getTimetable())) {
                if (c.isPossible() && c.getRequests().get(1) == r) {
                    ways.add(give.getName() + " 주고 → " + receive + " 받기  ("
                            + session.nameOf(c.getRequests().get(2).getStudentId()) + " 님과 함께)");
                }
            }
        }
        if (ways.isEmpty()) {
            return null;
        }
        String more = ways.size() > 1 ? "  외 " + (ways.size() - 1) + "가지" : "";
        return tip("세 명 교환 가능", ways.get(0) + more);
    }

    private static String tip(String head, String body) {
        return "<html><div style='padding:3px 5px'><b>" + Theme.escapeHtml(head) + "</b><br>" + Theme.escapeHtml(body) + "</div></html>";
    }
}
