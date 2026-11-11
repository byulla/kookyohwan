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

/** 학번 로그인. 시작할 때와 사용자 전환이 같은 메소드를 쓴다 (콘솔의 Main.login과 같은 규칙). */
public final class LoginGate {
    private LoginGate() {
    }

    /** 취소하면 empty. 처음 보는 학번이면 이름을 받아 빈 시간표로 등록한다. */
    public static Optional<Student> ask(Component parent, StudentRegistry registry) {
        while (true) {
            String id = prompt(parent, "구교환 - 학번 입력", "학번을 입력하세요.");
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
                continue; // 등록하지 않고 학번을 다시 묻는다
            }
            if (name.contains(",")) {
                JOptionPane.showMessageDialog(parent, "이름에는 쉼표(,)를 쓸 수 없습니다.", "오류",
                        JOptionPane.ERROR_MESSAGE);
                continue;
            }
            return Optional.of(registry.register(id, name.trim()));
        }
    }

    /** 둥근 입력 칸 하나짜리 입력 창. 취소하면 null. (기본 입력 창의 칸은 작고 각져서 따로 만든다) */
    private static String prompt(Component parent, String title, String message) {
        JTextField field = Theme.field(16);
        JPanel box = new JPanel(new BorderLayout(0, 10));
        box.add(new JLabel(message), BorderLayout.NORTH);
        box.add(field, BorderLayout.CENTER);
        field.addAncestorListener(new AncestorListener() { // 창이 뜨면 바로 입력할 수 있게
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
