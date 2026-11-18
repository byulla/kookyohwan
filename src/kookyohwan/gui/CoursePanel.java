package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.service.EnrollmentService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static kookyohwan.gui.Session.ROW_GAP;

class CoursePanel extends JPanel {
    private static final String ALL_TYPES = "전체";
    private static final int SEAT_COLUMN = 5;

    private final Session session;
    private final JTextField keyword = Theme.field(18);
    private final JComboBox<String> typeFilter = Theme.combo();
    private final JComboBox<String> seatFilter = Theme.combo(new String[]{"전체", "빈자리 있음", "마감"});
    private final JLabel count = new JLabel();
    private final JLabel hint = new JLabel();
    private final JButton addButton = Theme.primary("선택한 과목을 내 시간표에 넣기");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"학수번호", "교과목명", "분반", "구분", "학점", "빈자리", "시간", "교수", "강의실"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<Course> shown = new ArrayList<>();

    CoursePanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        typeFilter.addItem(ALL_TYPES);
        for (CourseType type : CourseType.values()) {
            typeFilter.addItem(type.getLabel());
        }
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, ROW_GAP));
        top.add(new JLabel("검색 (교과목명, 교수명, 학수번호)"));
        top.add(keyword);
        top.add(new JLabel("구분"));
        top.add(typeFilter);
        top.add(new JLabel("빈자리"));
        top.add(seatFilter);
        top.add(count);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.table(table, null);
        int[] widths = {85, 270, 50, 50, 45, 140, 120, 80, 170};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(SEAT_COLUMN).setCellRenderer(Theme.pill(null));
        add(Theme.scroll(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, ROW_GAP));
        buttonRow.add(addButton);
        hint.setForeground(Theme.MUTED);
        bottom.add(hint, BorderLayout.CENTER);
        bottom.add(buttonRow, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        keyword.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        typeFilter.addActionListener(e -> refresh());
        seatFilter.addActionListener(e -> refresh());
        addButton.addActionListener(e -> UiEvents.afterInput(this::addSelected));
        table.getSelectionModel().addListSelectionListener(e -> updateButton());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && addButton.isEnabled()) {
                    UiEvents.afterInput(CoursePanel.this::addSelected);
                }
            }
        });
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        String text = keyword.getText().trim();
        String type = (String) typeFilter.getSelectedItem();
        int seat = seatFilter.getSelectedIndex();
        EnrollmentService enrollment = session.getEnrollment();
        List<Course> found = text.isEmpty() ? session.getCatalog().all() : session.getCatalog().search(text);
        shown.clear();
        model.setRowCount(0);
        for (Course c : found) {
            if (!ALL_TYPES.equals(type) && !c.getType().getLabel().equals(type)) {
                continue;
            }
            boolean full = enrollment.isFull(c);
            if ((seat == 1 && full) || (seat == 2 && !full)) {
                continue;
            }
            shown.add(c);
            model.addRow(new Object[]{c.getCode(), c.getName(), c.getSection(), c.getType().getLabel(),
                    c.getCredits(), enrollment.describe(c), c.timesToString(), c.getProfessor(), c.getRoom()});
        }
        count.setText(shown.size() + "개 / 전체 " + session.getCatalog().size() + "개");
        updateButton();
    }

    private void updateButton() {
        boolean confirmed = session.getMe().isTimetableConfirmed();
        int row = table.getSelectedRow();
        Course selected = row >= 0 && row < shown.size() ? shown.get(row) : null;
        addButton.setText(confirmed ? "선택한 과목 수강신청" : "선택한 과목을 내 시간표에 넣기");
        if (!confirmed) {
            addButton.setEnabled(selected != null);
            hint.setText("시간표 편집 중: 지금 듣는 과목을 넣으세요. 다 넣었으면 [내 시간표] 탭에서 저장(확정)합니다.");
        } else if (selected != null && session.getEnrollment().isFull(selected)) {
            addButton.setEnabled(false);
            hint.setText("마감된 과목입니다. [교환 요청] 탭에서 이 과목을 희망 과목으로 요청을 올리세요.");
        } else {
            addButton.setEnabled(selected != null);
            hint.setText("빈자리가 있는 과목만 수강신청할 수 있습니다. 마감된 과목은 교환 요청으로 상대를 찾으세요.");
        }
    }

    private void addSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            JOptionPane.showMessageDialog(this, "과목을 먼저 선택하세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TimetableActions.add(this, session, shown.get(row));
    }
}
