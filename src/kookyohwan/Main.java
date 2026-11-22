package kookyohwan;

import kookyohwan.model.Course;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.MatchService;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.StudentRegistry;
import kookyohwan.storage.FileStorage;
import kookyohwan.storage.Storage;
import kookyohwan.ui.ConsoleUI;
import kookyohwan.ui.TextUtil;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Main {
    private static final String DEFAULT_DATA_DIR = "data";
    private static final String TEST_ID = "202633781";

    public static void main(String[] args) {
        String dataDir = args.length > 0 ? args[0] : DEFAULT_DATA_DIR;
        Storage storage;
        try {
            storage = new FileStorage(dataDir);
        } catch (InvalidPathException e) {
            System.out.println("[오류] 데이터 폴더 경로를 쓸 수 없습니다. 폴더 이름을 확인하세요. 프로그램을 종료합니다.");
            return;
        }
        CourseCatalog catalog = new CourseCatalog();
        StudentRegistry registry = new StudentRegistry();
        RequestBoard board = new RequestBoard(catalog);

        printBanner();
        if (!restore(storage, dataDir, catalog, registry, board)) {
            return;
        }

        Scanner in = new Scanner(System.in);
        System.out.println();
        Optional<Student> me = login(in, registry);
        if (me.isEmpty()) {
            System.out.println("[안내] 학번을 입력하지 않아 종료합니다.");
            return;
        }
        ConsoleUI ui = new ConsoleUI(in, me.get(), catalog, registry, board, new MatchService(board, registry),
                () -> login(in, registry),
                current -> save(storage, registry, board, current));
        ui.run();

        System.out.println();
        save(storage, registry, board, ui.getCurrentStudent());
        System.out.println("구교환을 종료합니다.");
    }

    private static void printBanner() {
        System.out.println("========================================================");
        System.out.println("  구교환 (求교환) - 교양 교환 상대 찾기");
        System.out.println("  수강신청 정정기간용 1:1 교환 매칭");
        System.out.println("========================================================");
    }

    private static boolean restore(Storage storage, String dataDir, CourseCatalog catalog,
                                   StudentRegistry registry, RequestBoard board) {
        try {
            List<Course> courses = storage.loadCourses();
            for (Course c : courses) {
                catalog.add(c);
            }
            System.out.println("[복원] " + TextUtil.padRight(FileStorage.COURSES_FILE, 14)
                    + "과목 " + courses.size() + "개");

            List<Student> students = storage.loadStudents(courses);
            for (Student s : students) {
                registry.put(s);
            }
            System.out.println("[복원] " + TextUtil.padRight(FileStorage.STUDENTS_FILE, 14)
                    + "학생 " + students.size() + "명");

            List<SwapRequest> requests = storage.loadRequests(courses);
            for (SwapRequest r : requests) {
                board.put(r);
            }
            System.out.println("[복원] " + TextUtil.padRight(FileStorage.REQUESTS_FILE, 14)
                    + "교환 요청 " + requests.size() + "건");
            return true;
        } catch (NoSuchFileException e) {
            System.out.println("[안내] " + dataDir + "/" + FileStorage.COURSES_FILE + " 파일을 찾을 수 없습니다.");
            System.out.println("       프로젝트 루트(kookyohwan/)에서 실행했는지 확인하세요. 프로그램을 종료합니다.");
            return false;
        } catch (IOException e) {
            System.out.println("[오류] 파일을 읽지 못했습니다. " + e.getMessage());
            System.out.println("       파일을 고친 뒤 다시 실행하세요. 프로그램을 종료합니다.");
            return false;
        }
    }

    private static Optional<Student> login(Scanner in, StudentRegistry registry) {
        while (true) {
            String hint = registry.find(TEST_ID).isPresent() ? " (테스트 학번 " + TEST_ID + ")" : "";
            String id = ask(in, "학번 입력" + hint + " > ");
            if (id.isEmpty()) {
                return Optional.empty();
            }
            if (!id.matches("[0-9]+")) {
                System.out.println("[오류] 학번은 숫자로 입력하세요.");
                continue;
            }
            Optional<Student> found = registry.find(id);
            if (found.isPresent()) {
                Student s = found.get();
                System.out.println("[확인] " + s.getName() + " 님의 시간표를 불러왔습니다. ("
                        + s.getTimetable().size() + "과목)");
                return found;
            }

            System.out.println("[안내] 등록되지 않은 학번입니다. 이름을 입력하면 빈 시간표로 시작합니다.");
            String name = ask(in, "이름 입력 > ");
            while (name.contains(",")) {
                System.out.println("[오류] 이름에는 쉼표(,)를 쓸 수 없습니다.");
                name = ask(in, "이름 입력 > ");
            }
            if (name.isEmpty()) {
                System.out.println("[취소] 학생을 등록하지 않았습니다.");
                continue;
            }
            Student s = registry.register(id, name);
            System.out.println("[등록] " + s.getName() + " (" + s.getId() + ") 님을 등록했습니다. "
                    + "2번 메뉴에서 과목을 추가하세요.");
            return Optional.of(s);
        }
    }

    private static void save(Storage storage, StudentRegistry registry, RequestBoard board, Student current) {
        try {
            storage.saveStudents(registry.all());
            System.out.println("[저장] " + TextUtil.padRight(FileStorage.STUDENTS_FILE, 14)
                    + "학생 " + registry.size() + "명 (내 시간표 " + current.getTimetable().size() + "과목)");
            storage.saveRequests(board.all());
            System.out.println("[저장] " + TextUtil.padRight(FileStorage.REQUESTS_FILE, 14)
                    + "교환 요청 " + board.size() + "건");
        } catch (IOException e) {
            System.out.println("[오류] 파일에 저장하지 못했습니다. " + e.getMessage());
        }
    }

    private static String ask(Scanner in, String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println();
            return "";
        }
        return in.nextLine().trim();
    }
}
