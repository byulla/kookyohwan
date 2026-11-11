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
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/** 교환 요청 탭(R3): 전체 요청 목록 + 내 요청 등록. 등록 조건 검사는 RequestBoard가 한다. */
class RequestPanel extends JPanel {
    private static final Color MINE = new Color(0xEAF3FF);

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
    private final JComboBox<String> giveBox = new JComboBox<>();
    private final List<Course> giveCourses = new ArrayList<>(); // giveBox의 항목 순서와 같은 과목
    private final JTextField wantField = new JTextField(24);


    RequestPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        add(title, BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        int[] widths = {50, 90, 300, 260, 70, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() { // 내 요청 줄은 색으로 표시
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (!selected) {
                    c.setBackground(mineRows.get(row) ? MINE : Color.WHITE);
                }
                return c;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton registerButton = new JButton("요청 등록");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("교환 요청 등록"));
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
        form.add(new JLabel("분반 무관. 과목명을 그대로 쓰거나 학수번호를 넣으면 과목명으로 바뀝니다."), at);
        add(form, BorderLayout.SOUTH);

        registerButton.addActionListener(e -> register());
        wantField.addActionListener(e -> register());
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
        for (int i = 0; i < giveCourses.size(); i++) { // 처음 보이는 항목은 교환 가능한 교양으로
            if (giveCourses.get(i).isGeneral()) {
                giveBox.setSelectedIndex(i);
                break;
            }
        }
    }

    private void register() {
        if (giveCourses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "시간표가 비어 있습니다. 먼저 과목을 추가하세요.", "안내",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Course give = giveCourses.get(giveBox.getSelectedIndex());
        String input = wantField.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "희망 과목명을 입력하세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // 학수번호를 넣으면 과목명으로 바꿔 등록(5.3). 둘 다 아니면 입력 그대로 넘겨 거절받는다
        String wantName = session.getCatalog().resolveName(input).orElse(input);
        try {
            session.getBoard().validate(session.getMe(), give, wantName);
        } catch (RequestRejectedException e) {
            TimetableActions.reject(this, session, e.getTarget() + ": " + e.getReason()
                    + ".\n요청 목록을 변경하지 않았습니다.");
            return;
        }
        int sections = session.getCatalog().findByName(wantName).size();
        String converted = wantName.equals(input) ? "" : "(" + input + " -> " + wantName + ")\n";
        int answer = JOptionPane.showConfirmDialog(this,
                converted + give.getName() + " (" + give.timesToString() + ")  ->  " + wantName
                        + "\n" + (sections > 1 ? "분반 " + sections + "개 중 시간이 맞는 것" : "분반 1개")
                        + "\n\n등록할까요?",
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
            TimetableActions.reject(this, session, e.getTarget() + ": " + e.getReason()
                    + ".\n요청 목록을 변경하지 않았습니다.");
        }
    }
}
