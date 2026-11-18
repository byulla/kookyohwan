package kookyohwan;

import kookyohwan.gui.LoginGate;
import kookyohwan.gui.MainPanel;
import kookyohwan.gui.Session;
import kookyohwan.gui.Theme;
import kookyohwan.model.Course;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.MatchService;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.StudentRegistry;
import kookyohwan.storage.FileStorage;
import kookyohwan.storage.Storage;

import javax.swing.JFrame;
import java.awt.Dimension;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;

public class GuiMain {
    private static final String DEFAULT_DATA_DIR = "data";

    public static void main(String[] args) {
        String dataDir = args.length > 0 ? args[0] : DEFAULT_DATA_DIR;
        SwingUtilities.invokeLater(() -> start(dataDir));
    }

    private static void start(String dataDir) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        Theme.install();
        Storage storage;
        try {
            storage = new FileStorage(dataDir);
        } catch (InvalidPathException e) {
            JOptionPane.showMessageDialog(null, "데이터 폴더 경로를 쓸 수 없습니다.", "구교환", JOptionPane.ERROR_MESSAGE);
            return;
        }
        CourseCatalog catalog = new CourseCatalog();
        StudentRegistry registry = new StudentRegistry();
        RequestBoard board = new RequestBoard(catalog);

        try {
            List<Course> courses = storage.loadCourses();
            for (Course c : courses) {
                catalog.add(c);
            }
            for (Student s : storage.loadStudents(courses)) {
                registry.put(s);
            }
            for (SwapRequest r : storage.loadRequests(courses)) {
                board.put(r);
            }
        } catch (NoSuchFileException e) {
            JOptionPane.showMessageDialog(null, FileStorage.COURSES_FILE + " 파일을 찾을 수 없습니다.", "구교환", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "파일을 읽지 못했습니다.\n" + e.getMessage(), "구교환", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Optional<Student> me = LoginGate.ask(null, registry);
        if (me.isEmpty()) {
            return;
        }
        Session session = new Session(me.get(), catalog, registry, board, new MatchService(board, registry),
                current -> save(storage, registry, board));

        JFrame frame = new JFrame("구교환 (求교환) - 교양 교환 상대 찾기");
        frame.setContentPane(new MainPanel(session));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                String result = save(storage, registry, board);
                if (result.startsWith("[오류]")) {
                    int answer = JOptionPane.showConfirmDialog(frame, "저장하지 못했습니다. 그대로 종료할까요?",
                            "구교환", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setSize(1180, 880);
        frame.setMinimumSize(new Dimension(960, 640));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        session.say("[복원] 과목 " + catalog.size() + "개, 학생 " + registry.size() + "명, 교환 요청 "
                + board.size() + "건   |   " + me.get().getName() + " 님의 시간표를 불러왔습니다.");
    }

    private static String save(Storage storage, StudentRegistry registry, RequestBoard board) {
        try {
            storage.saveStudents(registry.all());
            storage.saveRequests(board.all());
            return "[저장] students.csv 학생 " + registry.size() + "명, requests.csv 교환 요청 " + board.size() + "건";
        } catch (IOException e) {
            return "[오류] 파일에 저장하지 못했습니다. " + e.getMessage();
        }
    }
}
