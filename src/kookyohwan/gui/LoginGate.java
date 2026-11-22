package kookyohwan.gui;

import kookyohwan.model.Student;
import kookyohwan.service.StudentRegistry;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Optional;

public final class LoginGate {
    private static final String TEST_ID = "202633781";

    private LoginGate() {
    }

    public static Optional<Student> ask(Component parent, StudentRegistry registry) {
        while (true) {
            String hint = registry.find(TEST_ID).isPresent() ? " (테스트 학번 " + TEST_ID + ")" : "";
            String id = prompt(parent, "구교환 - 학번 입력", "학번을 입력하세요." + hint);
            if (id == null || id.trim().isEmpty()) {
                return Optional.empty();
            }
            id = id.trim();
            if (!id.matches("[0-9]+")) {
                JOptionPane.showMessageDialog(parent, "학번은 숫자로 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            Optional<Student> found = registry.find(id);
            if (found.isPresent()) {
                return found;
            }
            String name = prompt(parent, "새 학생 등록", "처음 보는 학번입니다. 이름을 입력하세요.");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (name.contains(",")) {
                JOptionPane.showMessageDialog(parent, "이름에는 쉼표(,)를 쓸 수 없습니다.", "오류",
                        JOptionPane.ERROR_MESSAGE);
                continue;
            }
            return Optional.of(registry.register(id, name.trim()));
        }
    }

    private static String prompt(Component parent, String title, String message) {
        JTextField field = Theme.field(16);
        JPanel box = new JPanel(new BorderLayout(0, 10));
        box.add(new JLabel(message), BorderLayout.NORTH);
        box.add(field, BorderLayout.CENTER);
        field.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                field.requestFocusInWindow();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
        int answer = JOptionPane.showConfirmDialog(parent, box, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? field.getText() : null;
    }
}
