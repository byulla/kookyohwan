package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.SwapRequest;
import kookyohwan.service.RequestRejectedException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

class RequestPanel extends JPanel {
    private final Session session;
    private final JLabel title = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"번호", "요청자", "내놓는 과목", "희망 과목", "상태", ""}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<Boolean> mineRows = new ArrayList<>();
    private final JComboBox<String> giveBox = Theme.combo();
    private final List<Course> giveCourses = new ArrayList<>();
    private final JTextField wantField = Theme.field(24);

    RequestPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JButton cancelButton = Theme.secondary("선택한 내 요청 취소");
        JPanel head = new JPanel(new BorderLayout());
        head.add(title, BorderLayout.WEST);
        head.add(cancelButton, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        Theme.table(table, row -> row < mineRows.size() && mineRows.get(row));
        int[] widths = {60, 90, 300, 260, 90, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(4).setCellRenderer(Theme.pill(row -> row < mineRows.size() && mineRows.get(row)));
        table.getColumnModel().getColumn(5).setCellRenderer(Theme.pill(row -> row < mineRows.size() && mineRows.get(row)));
        add(Theme.scroll(table), BorderLayout.CENTER);

        JButton registerButton = Theme.primary("요청 등록");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        GridBagConstraints at = new GridBagConstraints();
        at.insets = new Insets(4, 8, 6, 4);
        at.anchor = GridBagConstraints.WEST;
        at.gridy = 0;
        form.add(new JLabel("내놓을 과목"), at);
        at.gridwidth = 2;
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        form.add(giveBox, at);
        at.gridy = 1;
        at.gridwidth = 1;
        at.fill = GridBagConstraints.NONE;
        at.weightx = 0;
        form.add(new JLabel("희망 과목명"), at);
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        form.add(wantField, at);
        at.fill = GridBagConstraints.NONE;
        at.weightx = 0;
        form.add(registerButton, at);
        at.gridy = 2;
        at.gridx = 1;
        at.insets = new Insets(0, 8, 6, 4);
        form.add(Theme.muted("분반 무관. 과목명을 그대로 쓰거나 학수번호를 넣으면 과목명으로 바뀝니다."), at);
        cancelButton.addActionListener(e -> UiEvents.afterInput(this::cancelSelected));
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        add(form, BorderLayout.SOUTH);

        registerButton.addActionListener(e -> UiEvents.afterInput(this::register));
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        List<SwapRequest> requests = session.getBoard().all();
        int waiting = 0;
        model.setRowCount(0);
        mineRows.clear();
        for (SwapRequest r : requests) {
            boolean isMine = r.getStudentId().equals(session.getMe().getId());
            if (r.isWaiting()) {
                waiting++;
            }
            mineRows.add(isMine);
            model.addRow(new Object[]{"#" + r.getId(), session.nameOf(r.getStudentId()),
                    Session.label(r.getGiveCourse()), r.getWantName(), r.getStatus().getLabel(),
                    isMine ? "내 요청" : ""});
        }
        title.setText("교환 요청 목록   총 " + requests.size() + "건 (대기 " + waiting + ")");

        giveBox.removeAllItems();
        giveCourses.clear();
        for (Course c : session.getMe().getTimetable().getCourses()) {
            giveCourses.add(c);
            giveBox.addItem(Session.label(c) + "  [" + c.getType().getLabel()
                    + (c.isGeneral() ? "" : " - 교환 불가") + "]");
        }
        for (int i = 0; i < giveCourses.size(); i++) {
            if (giveCourses.get(i).isGeneral()) {
                giveBox.setSelectedIndex(i);
                break;
            }
        }
    }

    private void register() {
        if (giveCourses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "시간표가 비어 있습니다.", "안내",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Course give = giveCourses.get(giveBox.getSelectedIndex());
        String input = wantField.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "희망 과목명을 입력하세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String wantName = session.getCatalog().resolveName(input).orElse(input);
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
        int answer = JOptionPane.showConfirmDialog(this,
                give.getName() + " -> " + wantName + "\n"
                        + (open > 0 ? "빈자리 있는 분반이 " + open + "개 있습니다. 그래도 등록할까요?" : "등록할까요?"),
                "교환 요청 등록", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 요청을 등록하지 않았습니다.");
            return;
        }
        try {
            SwapRequest request = session.getBoard().register(session.getMe(), give, wantName);
            session.say("[등록] 요청 #" + request.getId() + " 등록 완료  상태: " + request.getStatus().getLabel()
                    + "   " + request.summary());
            wantField.setText("");
            session.fireChanged();
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, TimetableActions.brief(e.getReason()));
        }
    }

    private void cancelSelected() {
        int row = table.getSelectedRow();
        List<SwapRequest> requests = session.getBoard().all();
        if (row < 0 || row >= requests.size()) {
            JOptionPane.showMessageDialog(this, "취소할 요청을 먼저 선택하세요.", "안내",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SwapRequest request = requests.get(row);
        if (!request.getStudentId().equals(session.getMe().getId()) || !request.isWaiting()) {
            TimetableActions.reject(this, session, "대기 중인 내 요청만 취소할 수 있습니다.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "요청 #" + request.getId() + " 취소할까요?",
                "요청 취소", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            session.say("[취소] 요청 목록을 변경하지 않았습니다.");
            return;
        }
        session.getBoard().cancel(request.getId());
        session.say("[등록] 요청 #" + request.getId() + " 취소 완료");
        session.fireChanged();
    }
}
