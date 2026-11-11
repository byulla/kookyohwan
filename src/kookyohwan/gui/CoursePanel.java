package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import static kookyohwan.gui.Session.ROW_GAP;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** 과목 목록 탭(R1): 검색어와 구분으로 좁혀 보고, 고른 과목을 내 시간표에 추가한다. */
class CoursePanel extends JPanel {
    private static final String ALL_TYPES = "전체";

    private final Session session;
    private final JTextField keyword = new JTextField(22);
    private final JComboBox<String> typeFilter = new JComboBox<>();
    private final JLabel count = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"학수번호", "교과목명", "분반", "구분", "학점", "시간", "교수", "강의실"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<Course> shown = new ArrayList<>(); // 표의 줄 순서와 같은 과목 목록

    CoursePanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        typeFilter.addItem(ALL_TYPES);
        for (CourseType type : CourseType.values()) {
            typeFilter.addItem(type.getLabel());
        }
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, ROW_GAP));
        top.add(new JLabel("검색 (교과목명 일부 또는 학수번호 앞자리)"));
        top.add(keyword);
        top.add(new JLabel("구분"));
        top.add(typeFilter);
        top.add(count);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        int[] widths = {80, 300, 45, 45, 40, 130, 80, 200};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton addButton = new JButton("선택한 과목을 내 시간표에 추가");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, ROW_GAP));
        bottom.add(new JLabel("과목을 두 번 눌러도 추가됩니다."));
        bottom.add(addButton);
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
        addButton.addActionListener(e -> addSelected());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addSelected();
                }
            }
        });
        refresh();
    }

    private void refresh() {
        String text = keyword.getText().trim();
        String type = (String) typeFilter.getSelectedItem();
        List<Course> found = text.isEmpty() ? session.getCatalog().all() : session.getCatalog().search(text);
        shown.clear();
        model.setRowCount(0);
        for (Course c : found) {
            if (!ALL_TYPES.equals(type) && !c.getType().getLabel().equals(type)) {
                continue;
            }
            shown.add(c);
            model.addRow(new Object[]{c.getCode(), c.getName(), c.getSection(), c.getType().getLabel(),
                    c.getCredits(), c.timesToString(), c.getProfessor(), c.getRoom()});
        }
        count.setText(shown.size() + "개 / 전체 " + session.getCatalog().size() + "개");
    }

    private void addSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "추가할 과목을 먼저 고르세요.", "안내", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TimetableActions.add(this, session, shown.get(row));
    }
}
