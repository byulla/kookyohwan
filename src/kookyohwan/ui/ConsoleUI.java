package kookyohwan.ui;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.MatchResult;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.Timetable;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.MatchService;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.RequestRejectedException;
import kookyohwan.service.StudentRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 메뉴 루프와 화면 출력(계획서 12·13절). 입력을 받아 서비스에 넘기고 결과·거절 사유를 보여 줄 뿐,
 * 조건 검사는 RequestBoard, 겹침 판정은 Timetable, 매칭은 MatchService가 한다.
 * 저장과 로그인은 Main이 넘겨준 동작(saver, login)을 부르기만 해서 Storage를 모른다.
 */
public class ConsoleUI {
    private static final int MAX_SEARCH_ROWS = 30; // 메뉴 1 검색 결과를 한 화면에 보이는 만큼만

    private final Scanner in;
    private final CourseCatalog catalog;
    private final StudentRegistry registry;
    private final RequestBoard board;
    private final MatchService matcher;
    private final Supplier<Optional<Student>> login; // Main.login — 시작 때와 메뉴 9가 같은 메소드
    private final Consumer<Student> saver;           // Main.save — 파일 쓰기는 FileStorage만
    private Student me;

    public ConsoleUI(Scanner in, Student me, CourseCatalog catalog, StudentRegistry registry,
                     RequestBoard board, MatchService matcher,
                     Supplier<Optional<Student>> login, Consumer<Student> saver) {
        this.in = in;
        this.me = me;
        this.catalog = catalog;
        this.registry = registry;
        this.board = board;
        this.matcher = matcher;
        this.login = login;
        this.saver = saver;
    }

    /** 종료 시 Main이 "내 시간표 n과목"을 찍을 수 있게 (메뉴 9로 바뀌었을 수 있다). */
    public Student getCurrentStudent() {
        return me;
    }

    public void run() {
        while (true) {
            printMenu();
            System.out.print("선택 > ");
            if (!in.hasNextLine()) { // 입력이 끊기면(Ctrl+D) 메뉴 0과 같이 저장하고 종료
                System.out.println();
                return;
            }
            String line = in.nextLine().trim(); // nextInt()와 섞지 않는다 (개행 문제)
            int choice;
            try {
                choice = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("[오류] 숫자를 입력하세요.");
                continue;
            }
            switch (choice) {
                case 1 -> showCourses();
                case 2 -> showTimetable();
                case 3 -> registerRequest();
                case 4 -> showRequests();
                case 5 -> runMatch();
                case 9 -> switchUser();
                case 0 -> {
                    return; // Main이 이어서 저장
                }
                default -> System.out.println("[오류] 메뉴에 있는 번호(0~5, 9)를 입력하세요.");
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("--------------------- 메뉴 ---------------------");
        System.out.println(" 1. 과목 목록 보기");
        System.out.println(" 2. 내 시간표 보기 / 과목 추가");
        System.out.println(" 3. 교환 요청 등록");
        System.out.println(" 4. 교환 요청 목록 보기");
        System.out.println(" 5. 매칭 실행");
        System.out.println(" 9. 사용자 전환");
        System.out.println(" 0. 저장하고 종료");
        System.out.println("------------------------------------------------");
    }

    // ---------------------------------------------------------------- 메뉴 1

    /** 과목이 수천 개라 전체를 찍지 않고 검색어로 좁혀 보여 준다. 빈 줄이면 메뉴로. */
    private void showCourses() {
        List<Course> courses = catalog.all();
        EnumMap<CourseType, Integer> counts = new EnumMap<>(CourseType.class);
        for (Course c : courses) {
            counts.merge(c.getType(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (CourseType type : new CourseType[]{CourseType.GENERAL, CourseType.MAJOR,
                CourseType.TEACHING, CourseType.MILITARY}) {
            if (counts.containsKey(type)) {
                parts.add(type.getLabel() + " " + counts.get(type));
            }
        }
        System.out.println();
        System.out.println("[과목 목록] 총 " + courses.size() + "개 (" + String.join(", ", parts) + ")");
        while (true) {
            String keyword = ask("검색어 (교과목명 일부 또는 학수번호 앞자리, 돌아가려면 Enter) > ");
            if (keyword.isEmpty()) {
                return;
            }
            List<Course> found = catalog.search(keyword);
            if (found.isEmpty()) {
                System.out.println("[안내] '" + keyword + "'에 맞는 과목이 없습니다.");
                continue;
            }
            System.out.println("[확인] '" + keyword + "' 검색 결과 " + found.size() + "개");
            System.out.println(" " + col("학수번호", 10) + col("분반", 6) + col("구분", 6) + col("학점", 6) + col("시간", 22) + "교과목명");
            for (int i = 0; i < found.size() && i < MAX_SEARCH_ROWS; i++) {
                Course c = found.get(i);
                System.out.println(" " + col(c.getCode(), 10) + col(c.getSection(), 6)
                        + col(c.getType().getLabel(), 6) + col(String.valueOf(c.getCredits()), 6)
                        + col(c.timesToString(), 22) + c.getName());
            }
            if (found.size() > MAX_SEARCH_ROWS) {
                System.out.println(" ... 외 " + (found.size() - MAX_SEARCH_ROWS)
                        + "개는 생략했습니다. 검색어를 더 자세히 입력하세요.");
            }
        }
    }

    // ---------------------------------------------------------------- 메뉴 2

    private void showTimetable() {
        printTimetable();
        Timetable table = me.getTimetable();
        while (true) {
            String code = ask("추가할 학수번호 (돌아가려면 Enter) > ");
            if (code.isEmpty()) {
                return;
            }
            Optional<Course> found = catalog.findByCode(code);
            if (found.isEmpty()) {
                reject(code, "과목 목록에 없는 학수번호입니다. 1번 메뉴에서 검색해 확인하세요", "시간표를");
                continue;
            }
            Course course = found.get();
            if (table.contains(code)) {
                reject(course.getName() + " " + course.getSection(), "이미 시간표에 있습니다", "시간표를");
                continue;
            }
            List<Course> conflicts = table.findConflicts(course, null);
            if (table.exceedsLimit(course, conflicts)) { // 겹치는 과목을 빼고 넣어도 넘는 경우까지 여기서 거절
                reject(course.getName() + " " + course.getSection(), "최대 " + Timetable.MAX_CREDITS
                        + "학점을 넘습니다 (현재 " + table.getTotalCredits() + "학점, 넣으면 "
                        + table.creditsAfter(course, conflicts) + "학점)", "시간표를");
                continue;
            }
            if (conflicts.isEmpty()) {
                table.add(course);
                System.out.println("[등록] " + course.getCode() + " " + label(course) + " 추가. "
                        + table.size() + "과목 " + table.getTotalCredits() + "학점");
                printTimetable();
                continue;
            }

            // 겹침: 겹치는 과목을 전부 보여 주고 교체 여부를 묻는다 (정정기간의 실제 동작이 '빼고 넣기')
            System.out.println("[확인] " + course.getCode() + " " + label(course) + ": 다음 과목과 시간이 겹칩니다.");
            List<String> removedNames = new ArrayList<>();
            Optional<SwapRequest> blocking = Optional.empty();
            Course blocked = null;
            for (Course c : conflicts) {
                System.out.println("  - " + c.getCode() + " " + c.getName() + " (" + c.timesToString() + ")");
                removedNames.add(c.getName());
                if (blocking.isEmpty()) {
                    blocking = board.findGiving(me.getId(), c.getCode());
                    blocked = c;
                }
            }
            if (blocking.isPresent()) { // 요청은 남았는데 과목만 사라지는 상태를 막는다(AC5-2)
                int id = blocking.get().getId();
                reject(blocked.getName(), "요청 #" + id + josa(id, "이", "가")
                        + " 이 과목을 내놓는 중입니다. 먼저 요청을 취소하세요", "시간표를");
                continue;
            }
            if (!confirm("겹치는 과목을 빼고 이 과목을 넣을까요? (y/n) > ")) {
                System.out.println("[취소] 시간표를 변경하지 않았습니다.");
                continue;
            }
            table.replace(conflicts, course);
            System.out.println("[등록] " + String.join(", ", removedNames) + " 삭제, " + course.getName() + " "
                    + course.getSection() + " 추가. " + table.size() + "과목 " + table.getTotalCredits() + "학점");
            printTimetable();
        }
    }

    private void printTimetable() {
        System.out.println();
        System.out.println("[내 시간표] " + me.getName() + " (" + me.getId() + ")  "
                + me.getTimetable().size() + "과목  " + me.getTimetable().getTotalCredits() + "학점 (최대 "
                + Timetable.MAX_CREDITS + ")");
        TimetableGrid.print(me.getTimetable());
    }

    // ---------------------------------------------------------------- 메뉴 3

    private void registerRequest() {
        List<Course> mine = me.getTimetable().getCourses();
        if (mine.isEmpty()) {
            System.out.println("[안내] 시간표가 비어 있습니다. 먼저 2번 메뉴에서 과목을 추가하세요.");
            return;
        }
        System.out.println();
        System.out.println("[교환 요청 등록] " + me.getName());
        System.out.println("내 시간표:");
        for (int i = 0; i < mine.size(); i++) {
            Course c = mine.get(i);
            System.out.println("  " + (i + 1) + ") " + col(c.getCode(), 10) + col(label(c), 34)
                    + c.getType().getLabel() + (c.isGeneral() ? "" : " - 교환 불가"));
        }

        Course give = askGiveCourse(mine);
        if (give == null) {
            return;
        }
        while (true) {
            String input = ask("희망 과목명 (분반 무관, 학수번호도 가능) > ");
            if (input.isEmpty()) {
                return;
            }
            // 코드를 넣으면 과목명으로 바꿔 등록하고 변환 사실을 보여 준다(5.3). 둘 다 아니면 입력 그대로 넘겨 거절받는다
            String wantName = catalog.resolveName(input).orElse(input);
            if (!wantName.equals(input)) {
                System.out.println("[확인] " + input + " -> " + wantName);
            }
            try {
                board.validate(me, give, wantName);
            } catch (RequestRejectedException e) {
                reject(e.getTarget(), e.getReason(), "요청 목록을");
                continue;
            }
            System.out.println("[확인] " + give.getName() + " (" + give.timesToString() + ") -> " + wantName
                    + " (" + sectionHint(wantName) + ")");
            if (!confirm("등록할까요? (y/n) > ")) {
                System.out.println("[취소] 요청을 등록하지 않았습니다.");
                return;
            }
            try {
                SwapRequest request = board.register(me, give, wantName);
                System.out.println("[등록] 요청 #" + request.getId() + " 등록 완료  상태: "
                        + request.getStatus().getLabel());
            } catch (RequestRejectedException e) {
                reject(e.getTarget(), e.getReason(), "요청 목록을");
            }
            return;
        }
    }

    /** 내놓을 과목 번호를 받는다. 거절(전공)이면 같은 입력을 다시 받고, 빈 줄이면 null(메뉴로). */
    private Course askGiveCourse(List<Course> mine) {
        while (true) {
            String line = ask("내놓을 과목 번호 > ");
            if (line.isEmpty()) {
                return null;
            }
            int no;
            try {
                no = Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("[오류] 숫자를 입력하세요.");
                continue;
            }
            if (no < 1 || no > mine.size()) {
                System.out.println("[오류] 1~" + mine.size() + " 사이의 번호를 입력하세요.");
                continue;
            }
            Course give = mine.get(no - 1);
            try {
                board.validateGive(me, give);
                return give;
            } catch (RequestRejectedException e) {
                reject(e.getTarget(), e.getReason(), "요청 목록을");
            }
        }
    }

    /** "분반 01, 02, 03 중 시간이 맞는 것" / "분반 01" */
    private String sectionHint(String wantName) {
        List<String> sections = new ArrayList<>();
        for (Course c : catalog.findByName(wantName)) {
            sections.add(c.getSection());
        }
        String joined = "분반 " + String.join(", ", sections);
        return sections.size() > 1 ? joined + " 중 시간이 맞는 것" : joined;
    }

    // ---------------------------------------------------------------- 메뉴 4

    private void showRequests() {
        List<SwapRequest> requests = board.all();
        if (requests.isEmpty()) {
            System.out.println("[안내] 등록된 교환 요청이 없습니다.");
            return;
        }
        int waiting = 0;
        for (SwapRequest r : requests) {
            if (r.isWaiting()) {
                waiting++;
            }
        }
        System.out.println();
        System.out.println("[교환 요청 목록] 총 " + requests.size() + "건 (대기 " + waiting + ")");
        System.out.println(" " + col("번호", 6) + col("요청자", 8) + col("내놓는 과목", 28)
                + col("희망 과목", 16) + "상태");
        for (SwapRequest r : requests) {
            boolean isMine = r.getStudentId().equals(me.getId());
            System.out.println(" " + col("#" + r.getId(), 6) + col(nameOf(r.getStudentId()), 8)
                    + col(label(r.getGiveCourse()), 28) + col(r.getWantName(), 16)
                    + (isMine ? col(r.getStatus().getLabel(), 6) + "<- 내 요청" : r.getStatus().getLabel()));
        }
    }

    // ---------------------------------------------------------------- 메뉴 5

    private void runMatch() {
        List<SwapRequest> waiting = board.waitingOf(me.getId());
        if (waiting.isEmpty()) {
            System.out.println("[안내] 대기 중인 내 교환 요청이 없습니다. 먼저 3번 메뉴에서 요청을 등록하세요.");
            return;
        }
        SwapRequest mine = waiting.size() == 1 ? waiting.get(0) : askMyRequest(waiting);
        if (mine == null) {
            return;
        }
        Course give = mine.getGiveCourse();
        List<MatchResult> results = matcher.match(mine, me.getTimetable());
        int others = board.waitingExcept(me.getId()).size();

        System.out.println();
        System.out.println("[매칭 실행] 내 요청 #" + mine.getId() + ": " + mine.summary());
        if (results.isEmpty()) {
            System.out.println(" - 대기 중인 다른 요청 " + others + "건 중 조건이 맞는 후보 0건");
            System.out.println();
            System.out.println("[결과] 조건에 맞는 상대가 없습니다. 요청 #" + mine.getId()
                    + josa(mine.getId(), "은", "는") + " 대기 상태로 유지됩니다.");
            return;
        }
        System.out.println(" - 대기 중인 다른 요청 " + others + "건 중 조건이 맞는 후보 " + results.size()
                + "건 (조건 불일치 " + (others - results.size()) + "건은 후보 아님)");
        System.out.println(" - 내 시간표 검사: 내놓는 " + give.getName() + " 자리는 빼고 나머지("
                + remainingTimes(give) + ")와 비교");
        System.out.println(" - 상대 시간표 검사: 상대가 받을 " + give.getName() + " (" + give.timesToString() + ") 기준");
        System.out.println();
        System.out.println(" " + col("요청", 6) + col("요청자", 8) + col("받을 과목", 27) + col("판정", 8) + "사유");
        List<MatchResult> possible = new ArrayList<>();
        for (MatchResult r : results) {
            System.out.println(" " + col("#" + r.getRequest().getId(), 6)
                    + col(nameOf(r.getRequest().getStudentId()), 8) + col(label(r.getReceiveCourse()), 27)
                    + col("[" + r.getVerdict().getLabel() + "]", 8) + r.getReason());
            if (r.isPossible()) {
                possible.add(r);
            }
        }

        System.out.println();
        System.out.println("[결과] 교환 가능한 상대 " + possible.size() + "명");
        if (possible.isEmpty()) {
            System.out.println("요청 #" + mine.getId() + josa(mine.getId(), "은", "는") + " 대기 상태로 유지됩니다.");
            return;
        }
        String myCourse = give.getName() + " (" + give.timesToString() + ")";
        for (int i = 0; i < possible.size(); i++) {
            SwapRequest other = possible.get(i).getRequest();
            Course receive = possible.get(i).getReceiveCourse();
            String who = nameOf(other.getStudentId());
            String idNote = who.equals(other.getStudentId()) ? "" : " (" + other.getStudentId() + ")";
            System.out.println("  " + (i + 1) + ") " + who + idNote + "  내 " + myCourse + " <-> "
                    + who + " " + label(receive));
            System.out.println("     교환 후 내 시간표: " + give.timesToString() + " " + give.getName() + " 삭제, "
                    + receive.timesToString() + " " + receive.getName() + " 추가");
        }
        System.out.println();
        System.out.println("두 사람이 합의한 뒤 수강신청 시스템에서 직접 정정하세요.");
        System.out.println("(매칭 실행은 요청 상태를 바꾸지 않습니다)");
    }

    /** 내 대기 요청이 2건 이상일 때 번호로 고른다. 빈 줄이면 null(메뉴로). */
    private SwapRequest askMyRequest(List<SwapRequest> waiting) {
        System.out.println();
        System.out.println("내 대기 요청이 " + waiting.size() + "건입니다.");
        for (SwapRequest r : waiting) {
            System.out.println("  " + col("#" + r.getId(), 5) + r.summary());
        }
        while (true) {
            String line = ask("매칭할 요청 번호 > ");
            if (line.isEmpty()) {
                return null;
            }
            int id;
            try {
                id = Integer.parseInt(line.startsWith("#") ? line.substring(1) : line);
            } catch (NumberFormatException e) {
                System.out.println("[오류] 숫자를 입력하세요.");
                continue;
            }
            for (SwapRequest r : waiting) {
                if (r.getId() == id) {
                    return r;
                }
            }
            System.out.println("[오류] 목록에 있는 요청 번호를 입력하세요.");
        }
    }

    /** 내놓는 과목을 뺀 내 시간표의 시간들: "월 3-4, 수 1-2, 금 3-4" */
    private String remainingTimes(Course give) {
        List<String> times = new ArrayList<>();
        for (Course c : me.getTimetable().getCourses()) {
            if (!c.getCode().equals(give.getCode())) {
                times.add(c.timesToString());
            }
        }
        return times.isEmpty() ? "없음" : String.join(", ", times);
    }

    // ---------------------------------------------------------------- 메뉴 9

    /** 저장 먼저 -> login() 다시 호출. 전환 중 꺼져도 등록한 요청이 남는다. */
    private void switchUser() {
        System.out.println();
        saver.accept(me);
        Optional<Student> next = login.get();
        if (next.isEmpty()) {
            System.out.println("[취소] 사용자를 바꾸지 않았습니다. (" + me.getName() + ")");
            return;
        }
        me = next.get();
    }

    // ---------------------------------------------------------------- 공통

    /** 프롬프트를 찍고 한 줄 입력. 입력이 끊기면 빈 줄로 취급해 메뉴로 돌아가게 한다. */
    private String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println();
            return "";
        }
        return in.nextLine().trim();
    }

    /** (y/n) 확인. y만 true, n이나 빈 줄은 false. 그 밖의 입력은 다시 묻는다. */
    private boolean confirm(String prompt) {
        while (true) {
            String answer = ask(prompt);
            if (answer.equalsIgnoreCase("y")) {
                return true;
            }
            if (answer.isEmpty() || answer.equalsIgnoreCase("n")) {
                return false;
            }
            System.out.println("[오류] y 또는 n을 입력하세요.");
        }
    }

    /** 거절 형식 통일(5.3): [거절] 대상: 사유. …을 변경하지 않았습니다. (80칸을 넘지 않게 두 줄) */
    private void reject(String target, String reason, String unchanged) { // unchanged: "시간표를", "요청 목록을"
        System.out.println("[거절] " + target + ": " + reason + ".");
        System.out.println("       " + unchanged + " 변경하지 않았습니다.");
    }

    /** 표의 한 칸. 내용이 폭을 넘쳐도 다음 칸과 붙지 않게 공백 하나는 남긴다. */
    private static String col(String s, int width) {
        return TextUtil.padRight(s, width - 1) + " ";
    }

    /** 요청 번호 뒤 조사: #6은 / #5는. 숫자를 한글로 읽었을 때 받침이 있으면(0·1·3·6·7·8) 앞의 것. */
    private static String josa(int number, String withBatchim, String without) {
        return "013678".indexOf('0' + number % 10) >= 0 ? withBatchim : without;
    }

    /** "생명과 나눔 02 (월 3-4)" */
    private static String label(Course c) {
        return c.getName() + " " + c.getSection() + " (" + c.timesToString() + ")";
    }

    /** 학번 -> 이름. students.csv에 없는 학번이면 학번 그대로. */
    private String nameOf(String studentId) {
        return registry.find(studentId).map(Student::getName).orElse(studentId);
    }
}
