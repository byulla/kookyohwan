package kookyohwan.gui;

import kookyohwan.model.Student;
import kookyohwan.service.StudentRegistry;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Optional;

/** 학번 로그인. 시작할 때와 사용자 전환이 같은 메소드를 쓴다 (콘솔의 Main.login과 같은 규칙). */
public final class LoginGate {
    private LoginGate() {
    }

    /** 취소하면 empty. 처음 보는 학번이면 이름을 받아 빈 시간표로 등록한다. */
    public static Optional<Student> ask(Component parent, StudentRegistry registry) {
        while (true) {
            String id = JOptionPane.showInputDialog(parent, "학번을 입력하세요.",
                    "구교환 - 학번 입력", JOptionPane.QUESTION_MESSAGE);
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
            String name = JOptionPane.showInputDialog(parent,
                    "처음 보는 학번입니다. 이름을 입력하세요.",
                    "새 학생 등록", JOptionPane.QUESTION_MESSAGE);
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
}
