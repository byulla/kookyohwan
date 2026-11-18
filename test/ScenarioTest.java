import kookyohwan.model.*;
import kookyohwan.service.*;
import kookyohwan.storage.FileStorage;
import kookyohwan.ui.TextUtil;
import kookyohwan.ui.TimetableGrid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ScenarioTest {
    static int pass = 0, fail = 0;
    static String section = "";
    static final List<String> failures = new ArrayList<>();
    static Path base;

    static final String A = "202612345", B = "202612346", C = "202612347", D = "202612348",
            E = "202612349", G = "202612350", F = "202612351";
    static final String FRENCH = "05306002", DISCRETE = "09392004", OS = "08306001",
            LIFE6 = "11393006", LIFE2 = "11393002", LIFE3 = "11393003", WRITING = "14632001";

    interface Body { void run() throws Exception; }

    static void sec(String name) { section = name; System.out.println("\n== " + name); }

    static void t(String name, Body body) {
        try {
            body.run();
            pass++;
            System.out.println("  ok   " + name);
        } catch (Throwable e) {
            fail++;
            String msg = (e instanceof AssertionError ? "" : e.getClass().getSimpleName() + ": ") + e.getMessage();
            failures.add("[" + section + "] " + name + " -> " + msg);
            System.out.println("  FAIL " + name + " -> " + msg);
        }
    }

    static void ok(boolean cond, String what) { if (!cond) throw new AssertionError(what); }
    static void eq(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) throw new AssertionError(what + ": 기대 <" + expected + "> 실제 <" + actual + ">");
    }
    static void has(String text, String part) { if (!text.contains(part)) throw new AssertionError("'" + part + "' 없음: " + text); }
    static void hasNot(String text, String part) { if (text.contains(part)) throw new AssertionError("'" + part + "' 가 있으면 안 됨: " + text); }
    static void throwsIAE(Body b, String what) {
        try { b.run(); } catch (IllegalArgumentException e) { return; }
        catch (Throwable e) { throw new AssertionError(what + ": IllegalArgumentException이 아닌 " + e); }
        throw new AssertionError(what + ": 예외가 안 남");
    }
    static void seq(String out, String... parts) {
        int at = 0;
        for (String p : parts) {
            int i = out.indexOf(p, at);
            if (i < 0) throw new AssertionError("'" + p + "' 가 (그 순서에) 없음\n----\n" + out.substring(Math.min(at, out.length())).replaceAll("(?s)^(.{1500}).*", "$1"));
            at = i + p.length();
        }
    }

    static Course course(String code, String name, CourseType type, String... times) {
        List<TimeSlot> slots = new ArrayList<>();
        for (String s : times) slots.add(TimeSlot.parse(s));
        return new Course(code, name, code.substring(code.length() - 3), type, slots, "교수", "강의실", 2);
    }

    static class W {
        final CourseCatalog catalog = new CourseCatalog();
        final StudentRegistry registry = new StudentRegistry();
        final RequestBoard board = new RequestBoard(catalog);
        final MatchService matcher = new MatchService(board, registry);
        final List<Course> courses;

        W(Path dir) throws IOException {
            FileStorage fs = new FileStorage(dir.toString());
            courses = fs.loadCourses();
            for (Course c : courses) catalog.add(c);
            for (Student s : fs.loadStudents(courses)) registry.put(s);
            for (SwapRequest r : fs.loadRequests(courses)) board.put(r);
        }
        Course c(String code) { return catalog.findByCode(code).orElseThrow(() -> new AssertionError("과목 없음 " + code)); }
        Student stu(String id) { return registry.find(id).orElseThrow(() -> new AssertionError("학생 없음 " + id)); }
        SwapRequest req(int id) { return board.find(id).orElseThrow(() -> new AssertionError("요청 없음 #" + id)); }
        List<MatchResult> match(int id) { return matcher.match(req(id), stu(req(id).getStudentId()).getTimetable()); }
        MatchResult result(int mine, int other) {
            for (MatchResult r : match(mine)) if (r.getRequest().getId() == other) return r;
            throw new AssertionError("#" + mine + "의 후보에 #" + other + " 없음");
        }
        List<Integer> candidates(int mine) {
            List<Integer> ids = new ArrayList<>();
            for (MatchResult r : match(mine)) ids.add(r.getRequest().getId());
            return ids;
        }
    }

    static Path copyData(String from, String name) throws IOException {
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        for (String f : new String[]{"courses.csv", "students.csv", "requests.csv"})
            Files.copy(Paths.get(from, f), dir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        return dir;
    }

    static String runConsole(Path dir, String... lines) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), "kookyohwan.Main", dir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("콘솔이 끝나지 않음"); }
        hasNot(out, "Exception");
        return out;
    }

    static String grid(Timetable table) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            TimetableGrid.print(table);
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
    static Timetable table(Course... courses) {
        Timetable tt = new Timetable();
        for (Course c : courses) ok(tt.restore(c), "준비 시간표 restore 실패 " + c.getCode());
        return tt;
    }

    public static void main(String[] args) throws Exception {
        base = args.length > 0 ? Paths.get(args[0]) : Files.createTempDirectory("kookyohwan-scenario");
        Files.createDirectories(base);
        parsing();
        courseAndRequest();
        repository();
        catalog();
        registryAndBoard();
        textUtil();
        timetableGrid();
        storage();
        planMatching();
        planConsole();

        System.out.println("\n==================================================");
        System.out.println("통과 " + pass + " / 실패 " + fail);
        for (String f : failures) System.out.println("  " + f);
        System.exit(fail == 0 ? 0 : 1);
    }

    static void parsing() {
        sec("1. 요일·시간 표기 (Day, TimeSlot)");
        t("요일 라벨 왕복: 월~일 7개", () -> {
            eq(7, Day.values().length, "요일 수");
            for (Day d : Day.values()) eq(d, Day.fromLabel(d.getLabel()), d.name());
            eq("월화수목금토일", String.join("", Arrays.stream(Day.values()).map(Day::getLabel).toArray(String[]::new)), "순서"); });
        t("모르는 요일 라벨은 거절", () -> { throwsIAE(() -> Day.fromLabel("X"), "X"); throwsIAE(() -> Day.fromLabel("MON"), "MON"); throwsIAE(() -> Day.fromLabel(""), "빈 문자열"); });
        t("월3-4 -> 월요일 3~4교시, 화면에는 '월 3-4'", () -> { TimeSlot s = TimeSlot.parse("월3-4");
            eq(Day.MON, s.getDay(), "요일"); eq(3, s.getStart(), "시작"); eq(4, s.getEnd(), "끝"); eq("월 3-4", s.toString(), "표기"); });
        t("한 교시짜리 '금3'은 '금 3'", () -> { TimeSlot s = TimeSlot.parse("금3"); eq(3, s.getStart(), "시작"); eq(3, s.getEnd(), "끝"); eq("금 3", s.toString(), "표기"); });
        t("두 자리 교시와 앞뒤 공백", () -> { TimeSlot s = TimeSlot.parse(" 수13-14 "); eq(13, s.getStart(), "시작"); eq(14, s.getEnd(), "끝"); });
        for (String bad : new String[]{"", "월", "3-4", "월3-", "월-4", "월 3-4", "월3~4", "월0", "월5-3", "월3-4-5", "월요일3"})
            t("시간 형식 거절: '" + bad + "'", () -> throwsIAE(() -> TimeSlot.parse(bad), bad));
    }

    static void courseAndRequest() {
        sec("2. 과목·요청·판정의 표기 (Course, SwapRequest, enum 라벨)");
        Course french = course("05306002", "프랑스어1", CourseType.GENERAL, "화3-4");
        Course os = course("08306001", "운영체제", CourseType.MAJOR, "목4-5", "금3");
        t("교양만 isGeneral", () -> { ok(french.isGeneral(), "교양"); ok(!os.isGeneral(), "전공");
            ok(!course("30001001", "교직론", CourseType.TEACHING).isGeneral(), "교직"); ok(!course("40001001", "군사학", CourseType.MILITARY).isGeneral(), "군사"); });
        t("시간 여러 개는 쉼표로 이어 쓴다", () -> { eq("목 4-5, 금 3", os.timesToString(), "두 구간"); eq("화 3-4", french.timesToString(), "한 구간");
            eq("", course("20006001", "온라인", CourseType.GENERAL).timesToString(), "시간 없음"); });
        t("toString: 코드 과목명 분반 (시간)", () -> eq("05306002 프랑스어1 002 (화 3-4)", french.toString(), "표기"));
        t("학수번호에서 분반을 뺀 앞부분", () -> { eq("05306", french.getSubjectCode(), "앞부분");
            eq("ABC", new Course("ABC", "이상한", "001", CourseType.GENERAL, Collections.emptyList()).getSubjectCode(), "분반으로 안 끝나면 그대로"); });
        t("같은 과목 판정: 과목명과 앞부분이 모두 같아야", () -> {
            ok(french.isSameSubject(course("05306001", "프랑스어1", CourseType.GENERAL, "월2-3")), "다른 분반");
            ok(!french.isSameSubject(course("05306003", "프랑스어2", CourseType.GENERAL, "월2-3")), "이름이 다름");
            ok(!french.isSameSubject(course("99999002", "프랑스어1", CourseType.GENERAL, "월2-3")), "앞부분이 다름"); });
        t("시간 목록은 밖에서 못 바꾼다", () -> {
            try { french.getTimes().add(TimeSlot.parse("월1")); } catch (UnsupportedOperationException e) { return; }
            throw new AssertionError("시간이 추가됨"); });
        t("요청 요약: 내놓는 과목 (시간) -> 희망 과목", () -> eq("프랑스어1 (화 3-4) -> 생명과 나눔",
                new SwapRequest(6, A, french, "생명과 나눔", RequestStatus.WAITING).summary(), "요약"));
        t("요청 상태: 대기 -> 취소 / 매칭완료", () -> {
            SwapRequest r = new SwapRequest(1, A, french, "생명과 나눔", RequestStatus.WAITING); ok(r.isWaiting(), "처음");
            r.cancel(); eq(RequestStatus.CANCELLED, r.getStatus(), "취소"); ok(!r.isWaiting(), "취소 뒤");
            SwapRequest m = new SwapRequest(2, A, french, "생명과 나눔", RequestStatus.WAITING); m.complete(); eq(RequestStatus.MATCHED, m.getStatus(), "확정"); });
        t("화면 라벨: 구분·상태·판정", () -> {
            eq("전공", CourseType.MAJOR.getLabel(), "MAJOR"); eq("교양", CourseType.GENERAL.getLabel(), "GENERAL");
            eq("교직", CourseType.TEACHING.getLabel(), "TEACHING"); eq("군사", CourseType.MILITARY.getLabel(), "MILITARY");
            eq("대기", RequestStatus.WAITING.getLabel(), "WAITING"); eq("매칭완료", RequestStatus.MATCHED.getLabel(), "MATCHED"); eq("취소", RequestStatus.CANCELLED.getLabel(), "CANCELLED");
            eq("가능", MatchVerdict.POSSIBLE.getLabel(), "POSSIBLE"); eq("제외", MatchVerdict.EXCLUDED_MINE.getLabel(), "EXCLUDED_MINE"); eq("제외", MatchVerdict.EXCLUDED_PARTNER.getLabel(), "EXCLUDED_PARTNER"); });
        t("판정 결과: 가능 / 제외", () -> {
            SwapRequest r = new SwapRequest(1, B, french, "생명과 나눔", RequestStatus.WAITING);
            MatchResult p = MatchResult.possible(r, french, "겹침 없음"); ok(p.isPossible(), "가능"); eq(MatchVerdict.POSSIBLE, p.getVerdict(), "판정"); eq("겹침 없음", p.getReason(), "사유");
            MatchResult x = MatchResult.excluded(r, french, MatchVerdict.EXCLUDED_PARTNER, "상대 시간표 겹침: 운영체제"); ok(!x.isPossible(), "제외"); ok(!x.isResolvable(), "뺄 수 없음"); eq(french, x.getReceiveCourse(), "받을 과목"); });
    }

    static void repository() {
        sec("3. 보관소 (Repository)");
        t("없는 키는 Optional.empty", () -> { Repository<String, Integer> r = new Repository<>(); ok(r.find("x").isEmpty(), "find"); ok(!r.exists("x"), "exists"); eq(0, r.size(), "크기"); });
        t("넣은 순서대로 나온다", () -> { Repository<Integer, String> r = new Repository<>(); r.put(3, "c"); r.put(1, "a"); r.put(2, "b"); eq(Arrays.asList("c", "a", "b"), r.all(), "순서"); });
        t("같은 키로 다시 넣으면 값만 바뀌고 자리는 그대로", () -> { Repository<Integer, String> r = new Repository<>(); r.put(1, "a"); r.put(2, "b"); r.put(1, "z");
            eq(2, r.size(), "크기"); eq(Arrays.asList("z", "b"), r.all(), "순서"); eq("z", r.find(1).get(), "값"); });
        t("all()로 받은 목록을 고쳐도 보관소는 그대로", () -> { Repository<Integer, String> r = new Repository<>(); r.put(1, "a"); r.all().clear(); eq(1, r.size(), "크기"); });
    }

    static void catalog() {
        sec("4. 과목 목록 (CourseCatalog)");
        Course fr1 = course("05306001", "프랑스어1", CourseType.GENERAL, "월2-3");
        Course fr2 = course("05306002", "프랑스어1", CourseType.GENERAL, "화3-4");
        Course fr21 = course("05307001", "프랑스어2", CourseType.GENERAL, "수2-3");
        Course os = course("08306001", "운영체제", CourseType.MAJOR, "목4-5", "금3");
        Body with = () -> { };
        CourseCatalog cat = new CourseCatalog();
        for (Course c : new Course[]{fr1, fr2, fr21, os}) cat.add(c);
        t("코드로 찾기", () -> { eq(fr2, cat.findByCode("05306002").get(), "있는 코드"); ok(cat.findByCode("00000000").isEmpty(), "없는 코드"); eq(4, cat.size(), "크기"); });
        t("과목명으로 찾으면 분반이 전부 나온다", () -> { eq(Arrays.asList(fr1, fr2), cat.findByName("프랑스어1"), "분반"); eq(Collections.emptyList(), cat.findByName("프랑스어"), "일부만 맞으면 없음"); });
        t("검색: 과목명 일부", () -> eq(Arrays.asList(fr1, fr2, fr21), cat.search("프랑스어"), "프랑스어"));
        t("검색: 학수번호 앞자리 (중간은 안 됨)", () -> { eq(Arrays.asList(fr1, fr2), cat.search("05306"), "앞자리"); eq(Collections.emptyList(), cat.search("306"), "중간"); });
        t("검색: 맞는 것이 없으면 빈 목록", () -> eq(Collections.emptyList(), cat.search("독일어"), "독일어"));
        t("existsName은 정확히 같은 이름만", () -> { ok(cat.existsName("운영체제"), "있음"); ok(!cat.existsName("운영"), "일부"); ok(!cat.existsName("운영체제 "), "뒤에 공백 (정규화 안 함)"); });
        t("resolveName: 학수번호 -> 과목명", () -> eq(Optional.of("프랑스어1"), cat.resolveName("05306002"), "코드"));
        t("resolveName: 과목명은 그대로", () -> eq(Optional.of("프랑스어2"), cat.resolveName("프랑스어2"), "이름"));
        t("resolveName: 코드도 과목명도 아니면 empty", () -> { ok(cat.resolveName("없는과목").isEmpty(), "없는 이름"); ok(cat.resolveName("99999999").isEmpty(), "없는 코드"); ok(cat.resolveName("").isEmpty(), "빈 문자열"); });
    }

    static void registryAndBoard() {
        sec("5. 학생 목록·요청 목록 (StudentRegistry, RequestBoard)");
        Course fr = course("05306002", "프랑스어1", CourseType.GENERAL, "화3-4");
        Course jp = course("05310003", "일본어1", CourseType.GENERAL, "목3-4");
        Course life = course("11393006", "생명과 나눔", CourseType.GENERAL, "수5-6");
        t("새 학생은 빈 시간표, 편집 중", () -> { StudentRegistry reg = new StudentRegistry(); Student s = reg.register("1", "가");
            eq(0, s.getTimetable().size(), "과목 수"); ok(!s.isTimetableConfirmed(), "편집 중"); eq(s, reg.find("1").get(), "찾기"); ok(reg.find("2").isEmpty(), "없는 학번"); });
        t("시간표 확정은 한 번만, 없는 학번은 false", () -> { StudentRegistry reg = new StudentRegistry(); reg.register("1", "가");
            ok(reg.confirmTimetable("1"), "첫 확정"); ok(reg.find("1").get().isTimetableConfirmed(), "확정됨"); ok(!reg.confirmTimetable("1"), "두 번째"); ok(!reg.confirmTimetable("9"), "없는 학번"); });
        t("학생 목록은 등록 순서", () -> { StudentRegistry reg = new StudentRegistry(); reg.register("3", "다"); reg.register("1", "가"); reg.register("2", "나");
            eq(3, reg.size(), "크기"); eq("다가나", reg.all().get(0).getName() + reg.all().get(1).getName() + reg.all().get(2).getName(), "순서"); });

        class Board {
            final CourseCatalog cat = new CourseCatalog();
            final RequestBoard board = new RequestBoard(cat);
            final Student me = new Student("1", "가", table(fr, jp), true);
            Board() { cat.add(fr); cat.add(jp); cat.add(life); }
        }
        t("요청 번호는 1부터 차례로", () -> { Board b = new Board();
            eq(1, b.board.register(b.me, fr, "생명과 나눔").getId(), "첫 요청"); eq(2, b.board.register(b.me, jp, "생명과 나눔").getId(), "둘째 요청"); });
        t("복원한 번호 다음부터 이어서 매긴다", () -> { Board b = new Board(); b.board.put(new SwapRequest(7, "2", life, "프랑스어1", RequestStatus.CANCELLED));
            eq(8, b.board.register(b.me, fr, "생명과 나눔").getId(), "다음 번호"); });
        t("등록 직후 상태는 대기, 시간표는 그대로", () -> { Board b = new Board(); SwapRequest r = b.board.register(b.me, fr, "생명과 나눔");
            eq(RequestStatus.WAITING, r.getStatus(), "상태"); eq(2, b.me.getTimetable().size(), "과목 수"); eq("1", r.getStudentId(), "학번"); });
        t("waitingOf / waitingExcept는 대기 요청만, 학번으로 나눈다", () -> { Board b = new Board();
            SwapRequest mine = b.board.register(b.me, fr, "생명과 나눔");
            b.board.put(new SwapRequest(10, "2", life, "프랑스어1", RequestStatus.WAITING));
            b.board.put(new SwapRequest(11, "2", life, "일본어1", RequestStatus.MATCHED));
            b.board.put(new SwapRequest(12, "1", jp, "생명과 나눔", RequestStatus.CANCELLED));
            eq(Collections.singletonList(mine), b.board.waitingOf("1"), "내 대기"); eq(1, b.board.waitingExcept("1").size(), "남의 대기"); eq(10, b.board.waitingExcept("1").get(0).getId(), "번호"); eq(4, b.board.size(), "전체"); });
        t("isGiving: 대기 중인 요청의 내놓는 과목만", () -> { Board b = new Board(); SwapRequest r = b.board.register(b.me, fr, "생명과 나눔");
            ok(b.board.isGiving("1", fr.getCode()), "내놓는 중"); ok(!b.board.isGiving("1", jp.getCode()), "안 내놓음"); ok(!b.board.isGiving("2", fr.getCode()), "다른 학생");
            eq(r, b.board.findGiving("1", fr.getCode()).get(), "findGiving"); b.board.cancel(r.getId()); ok(!b.board.isGiving("1", fr.getCode()), "취소 뒤"); });
        t("취소·확정은 대기 요청에만 한 번", () -> { Board b = new Board(); SwapRequest r = b.board.register(b.me, fr, "생명과 나눔"); SwapRequest s = b.board.register(b.me, jp, "생명과 나눔");
            ok(b.board.cancel(r.getId()), "취소"); ok(!b.board.cancel(r.getId()), "두 번 취소"); ok(!b.board.complete(r.getId()), "취소된 것을 확정");
            ok(b.board.complete(s.getId()), "확정"); ok(!b.board.cancel(s.getId()), "확정된 것을 취소"); ok(!b.board.cancel(99), "없는 번호"); ok(b.board.find(99).isEmpty(), "find"); });
        t("취소한 요청과 같은 내용은 다시 등록할 수 있다", () -> { Board b = new Board(); SwapRequest r = b.board.register(b.me, fr, "생명과 나눔"); b.board.cancel(r.getId());
            eq(2, b.board.register(b.me, fr, "생명과 나눔").getId(), "다시 등록"); });
    }

    static void textUtil() {
        sec("6. 한글 폭 보정 (TextUtil)");
        t("한글·한자는 2칸, 나머지는 1칸", () -> { eq(0, TextUtil.displayWidth(""), "빈 문자열"); eq(3, TextUtil.displayWidth("abc"), "영문"); eq(4, TextUtil.displayWidth("한글"), "한글");
            eq(4, TextUtil.displayWidth("求교"), "한자+한글"); eq(9, TextUtil.displayWidth("프랑스어1"), "섞임"); eq(11, TextUtil.displayWidth("생명과 나눔"), "공백 포함"); });
        t("padRight: 화면 폭 기준으로 채운다", () -> { eq("한글    ", TextUtil.padRight("한글", 8), "한글"); eq("ab  ", TextUtil.padRight("ab", 4), "영문");
            eq(14, TextUtil.displayWidth(TextUtil.padRight("프랑스어1", 14)), "폭"); });
        t("padRight: 이미 넓으면 그대로", () -> eq("프랑스어1", TextUtil.padRight("프랑스어1", 4), "그대로"));
        t("cut: 폭 안에 들면 그대로", () -> { eq("프랑스어1", TextUtil.cut("프랑스어1", 14), "짧은 이름"); eq("일이삼사오육칠", TextUtil.cut("일이삼사오육칠", 14), "딱 14칸"); });
        t("cut: 넘으면 12칸에서 자르고 ..", () -> { eq("일이삼사오육..", TextUtil.cut("일이삼사오육칠팔", 14), "한글"); eq("abcdefghijkl..", TextUtil.cut("abcdefghijklmnop", 14), "영문"); });
        t("cut: 한글이 반쯤 걸리면 그 글자를 버린다 (14칸을 넘지 않음)", () -> { String s = TextUtil.cut("대학생을 위한 학술적글쓰기", 14);
            eq("대학생을 위..", s, "결과"); ok(TextUtil.displayWidth(s) <= 14, "폭 " + TextUtil.displayWidth(s)); });
        t("center: 남는 칸을 좌우로 나눈다 (홀수면 오른쪽이 한 칸 더)", () -> { eq("      월      ", TextUtil.center("월", 14), "월"); eq(" ab  ", TextUtil.center("ab", 5), "홀수"); eq("넓은글자", TextUtil.center("넓은글자", 4), "넘침"); });
    }

    static void timetableGrid() {
        sec("7. 시간표 격자 (TimetableGrid)");
        Course discrete = course(DISCRETE, "이산수학", CourseType.MAJOR, "월4-6");
        Course os = course(OS, "운영체제", CourseType.MAJOR, "목4-5", "금3");
        Course french = course(FRENCH, "프랑스어1", CourseType.GENERAL, "화3-4");
        String out = grid(table(discrete, os, french));
        String[] lines = out.split("\n");
        t("머리줄: 월~금 다섯 열, 주말 열 없음", () -> { eq("    |      월      |      화      |      수      |      목      |      금      |", lines[0], "머리줄"); hasNot(lines[0], "토"); });
        t("모든 줄이 80칸", () -> { for (int i = 0; i < lines.length - 1; i++) eq(80, TextUtil.displayWidth(lines[i]), (i + 1) + "번째 줄 폭 [" + lines[i] + "]"); });
        t("늦은 수업이 없으면 9교시까지만", () -> { has(out, "\n 9  |"); hasNot(out, "\n 10 |"); });
        t("블록 첫 줄에 과목명, 둘째 줄에 학수번호", () -> {
            eq(" 3  |              |프랑스어1     |              |              |운영체제      |", line(lines, " 3  |"), "3교시");
            eq(" 4  |이산수학      |05306002      |              |운영체제      |              |", line(lines, " 4  |"), "4교시");
            eq(" 5  |09392004      |              |              |08306001      |              |", line(lines, " 5  |"), "5교시"); });
        t("한 교시짜리 블록에는 과목명만 (학수번호 줄이 없다)", () -> ok(!line(lines, " 4  |").endsWith("|08306001      |"), "금 4교시에 코드가 찍힘"));
        t("가로선은 과목이 바뀌는 곳에만: 이산수학 4~6교시 사이에는 선이 없다", () -> {
            int four = index(lines, " 4  |"), five = index(lines, " 5  |"), six = index(lines, " 6  |");
            ok(lines[four + 1].startsWith("    |              +"), "4-5 사이 월요일 칸에 선: " + lines[four + 1]);
            ok(lines[five + 1].startsWith("    |              |"), "5-6 사이 월요일 칸에 선: " + lines[five + 1]);
            eq("    +--------------+", lines[six + 1].substring(0, 20), "6교시 아래"); });
        t("위아래 테두리는 전부 선", () -> { String full = "    +--------------+--------------+--------------+--------------+--------------+";
            eq(full, lines[1], "위"); eq(full, lines[lines.length - 2], "아래"); });
        t("아래 요약 줄: 전공 / 교양", () -> eq(" 전공: 이산수학, 운영체제   교양: 프랑스어1", lines[lines.length - 1], "요약"));
        t("빈 시간표: 9교시까지 빈 칸, 전공·교양 없음", () -> { String empty = grid(new Timetable());
            has(empty, " 전공: 없음   교양: 없음"); has(empty, "\n 9  |"); hasNot(empty, "\n 10 |"); });
        t("늦은 수업이 있으면 그 교시까지 늘어난다", () -> { String late = grid(table(course("20010001", "야간교양", CourseType.GENERAL, "수11-12")));
            has(late, "\n 12 |              |              |20010001      |"); hasNot(late, "\n 13 |"); });
        t("주말 수업이 있으면 토·일 열이 생긴다", () -> { String weekend = grid(table(course("40001001", "군사학", CourseType.MILITARY, "토1-2")));
            String head = weekend.split("\n")[0]; has(head, "토"); has(head, "일"); has(weekend, "군사: 군사학"); hasNot(weekend, "교직:"); });
        t("긴 과목명은 잘라서 칸을 넘지 않는다", () -> { String longName = grid(table(course(WRITING, "대학생을 위한 학술적글쓰기", CourseType.GENERAL, "화3-4")));
            has(longName, "|대학생을 위.. |"); has(longName, "교양: 대학생을 위한 학술적글쓰기"); });
        t("ASCII와 한글만 쓴다 (박스 문자·특수 화살표 없음)", () -> { for (char ch : out.toCharArray())
            ok(ch < 128 || (ch >= '가' && ch <= '힣'), "허용되지 않는 문자 U+" + Integer.toHexString(ch)); });
    }
    static int index(String[] lines, String prefix) {
        for (int i = 0; i < lines.length; i++) if (lines[i].startsWith(prefix)) return i;
        throw new AssertionError("'" + prefix + "' 로 시작하는 줄 없음");
    }
    static String line(String[] lines, String prefix) { return lines[index(lines, prefix)]; }

    static void storage() {
        sec("8. 저장·복원 (FileStorage)");
        String header = "코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원\n";
        t("courses.csv가 없으면 IOException", () -> { Path dir = base.resolve("st-none"); Files.createDirectories(dir);
            try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { return; }
            throw new AssertionError("예외가 안 남"); });
        t("students·requests가 없으면 빈 상태로 시작", () -> { Path dir = base.resolve("st-empty"); Files.createDirectories(dir);
            Files.writeString(dir.resolve("courses.csv"), header + "10001001,전공A,001,MAJOR,월1-3,교수,강의실,3,0,0\n");
            FileStorage fs = new FileStorage(dir.toString()); List<Course> cs = fs.loadCourses();
            eq(1, cs.size(), "과목"); eq(0, fs.loadStudents(cs).size(), "학생"); eq(0, fs.loadRequests(cs).size(), "요청"); });
        t("과목 한 줄의 모든 칸이 읽힌다", () -> { Path dir = base.resolve("st-full"); Files.createDirectories(dir);
            Files.writeString(dir.resolve("courses.csv"), header + "09392004,이산수학,004,MAJOR,월4-6,이경복,AI관-415,3,60,58\n");
            Course c = new FileStorage(dir.toString()).loadCourses().get(0);
            eq("09392004", c.getCode(), "코드"); eq("이산수학", c.getName(), "과목명"); eq("004", c.getSection(), "분반"); eq(CourseType.MAJOR, c.getType(), "구분");
            eq("월 4-6", c.timesToString(), "시간"); eq("이경복", c.getProfessor(), "교수"); eq("AI관-415", c.getRoom(), "강의실");
            eq(3, c.getCredits(), "학점"); eq(60, c.getCapacity(), "정원"); eq(58, c.getBaseEnrolled(), "수강인원"); });
        t("다섯 칸짜리 옛 형식도 읽힌다 (뒤 칸은 빈 값·0)", () -> { Path dir = base.resolve("st-five"); Files.createDirectories(dir);
            Files.writeString(dir.resolve("courses.csv"), "코드,과목명,분반,구분,시간\n05306002,프랑스어1,002,GENERAL,화3-4\n\n08306001,운영체제,001,MAJOR,목4-5;금3\n");
            List<Course> cs = new FileStorage(dir.toString()).loadCourses();
            eq(2, cs.size(), "빈 줄은 건너뜀"); eq("", cs.get(0).getProfessor(), "교수"); eq(0, cs.get(0).getCredits(), "학점"); eq("목 4-5, 금 3", cs.get(1).timesToString(), "세미콜론 시간"); });
        t("칸 수가 모자란 줄은 줄 번호와 함께 거절", () -> { Path dir = base.resolve("st-short"); Files.createDirectories(dir);
            Files.writeString(dir.resolve("courses.csv"), header + "10001001,전공A,001,MAJOR,월1-3,교수,강의실,3,0,0\n10002001,전공B,001\n");
            try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { has(e.getMessage(), "3번째 줄"); has(e.getMessage(), "칸 수"); return; }
            throw new AssertionError("그대로 읽힘"); });
        t("시간표상태 칸이 없는 옛 students.csv: 과목이 있으면 확정, 없으면 편집 중", () -> { Path dir = base.resolve("st-old"); Files.createDirectories(dir);
            Files.writeString(dir.resolve("courses.csv"), header + "10001001,전공A,001,MAJOR,월1-3,교수,강의실,3,0,0\n");
            Files.writeString(dir.resolve("students.csv"), "학번,이름,과목코드\n1,가,10001001\n2,나,\n");
            FileStorage fs = new FileStorage(dir.toString()); List<Student> ss = fs.loadStudents(fs.loadCourses());
            ok(ss.get(0).isTimetableConfirmed(), "과목 있는 학생"); ok(!ss.get(1).isTimetableConfirmed(), "빈 시간표 학생"); eq(0, ss.get(1).getTimetable().size(), "과목 수"); });
        t("저장한 파일의 모양: 헤더, 세미콜론 목록, enum 이름", () -> { Path dir = base.resolve("st-shape"); Files.createDirectories(dir);
            Course a = course("10001001", "전공A", CourseType.MAJOR, "월1-3"), f = course("20001001", "프랑스어", CourseType.GENERAL, "화5-6");
            FileStorage fs = new FileStorage(dir.toString());
            fs.saveStudents(Arrays.asList(new Student("1", "가", table(a, f), true), new Student("2", "나", new Timetable(), false)));
            fs.saveRequests(Arrays.asList(new SwapRequest(1, "1", f, "생명과 나눔", RequestStatus.WAITING), new SwapRequest(2, "1", f, "영화", RequestStatus.CANCELLED), new SwapRequest(3, "1", f, "일본어", RequestStatus.MATCHED)));
            eq(Arrays.asList("학번,이름,과목코드,시간표상태", "1,가,10001001;20001001,CONFIRMED", "2,나,,EDITING"), Files.readAllLines(dir.resolve("students.csv")), "students.csv");
            eq(Arrays.asList("번호,학번,내놓는과목코드,희망과목명,상태", "1,1,20001001,생명과 나눔,WAITING", "2,1,20001001,영화,CANCELLED", "3,1,20001001,일본어,MATCHED"), Files.readAllLines(dir.resolve("requests.csv")), "requests.csv"); });
        t("없는 폴더에 저장하면 폴더를 만든다", () -> { Path dir = base.resolve("st-new").resolve("deep");
            new FileStorage(dir.toString()).saveRequests(Collections.emptyList()); ok(Files.exists(dir.resolve("requests.csv")), "파일"); });
        for (String from : new String[]{"data", "data-ac8"})
            t("실제 " + from + "/ 을 읽어 그대로 저장하고 다시 읽으면 같은 상태", () -> { Path dir = copyData(from, "st-round-" + from);
                W before = new W(dir); FileStorage fs = new FileStorage(dir.toString());
                fs.saveStudents(before.registry.all()); fs.saveRequests(before.board.all());
                W after = new W(dir); eq(snapshot(before), snapshot(after), "상태"); ok(before.registry.size() > 0 && before.board.size() > 0, "빈 데이터"); });
    }
    static String snapshot(W w) {
        StringBuilder sb = new StringBuilder();
        for (Student s : w.registry.all()) {
            sb.append(s.getId()).append(s.getName()).append(s.isTimetableConfirmed() ? "C" : "E").append(":");
            for (Course c : s.getTimetable().getCourses()) sb.append(c.getCode()).append(";");
            sb.append("|");
        }
        for (SwapRequest r : w.board.all())
            sb.append(r.getId()).append(r.getStudentId()).append(r.getGiveCourse().getCode()).append(r.getWantName()).append(r.getStatus()).append("|");
        return sb.toString();
    }

    static void planMatching() {
        sec("9. 계획서 시나리오: 매칭 판정 (data-ac8, 조혜라의 프랑스어1 -> 생명과 나눔)");
        t("준비 데이터: 학생 7명, 요청 8건, 조혜라 3과목 8학점", () -> { W w = new W(Paths.get("data-ac8"));
            eq(7, w.registry.size(), "학생"); eq(8, w.board.size(), "요청"); eq("조혜라", w.stu(A).getName(), "이름");
            eq(Arrays.asList(DISCRETE, OS, FRENCH), codes(w.stu(A)), "시간표"); eq(8, w.stu(A).getTimetable().getTotalCredits(), "학점"); eq("프랑스어1 (화 3-4) -> 생명과 나눔", w.req(6).summary(), "요청 #6"); });
        t("후보는 교차 일치하는 요청만, 요청 번호 순서", () -> { W w = new W(Paths.get("data-ac8")); eq(Arrays.asList(1, 4, 5, 8), w.candidates(6), "후보"); });
        t("AC1 가능: 백찬샘의 생명과 나눔 006 (수 5-6) - 겹침 없음", () -> { W w = new W(Paths.get("data-ac8")); MatchResult r = w.result(6, 1);
            ok(r.isPossible(), r.getReason()); eq(LIFE6, r.getReceiveCourse().getCode(), "받을 분반"); eq("겹침 없음", r.getReason(), "사유"); });
        t("AC2 제외: 김두호의 생명과 나눔 002 (월 3-4) - 내 이산수학과 겹침", () -> { W w = new W(Paths.get("data-ac8")); MatchResult r = w.result(6, 4);
            ok(!r.isPossible(), "가능으로 나옴"); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); eq("내 시간표 겹침: 이산수학", r.getReason(), "사유"); });
        t("가능: 김민재의 생명과 나눔 003 (화 3-4) - 내놓는 프랑스어1 자리라 겹침이 아님", () -> { W w = new W(Paths.get("data-ac8")); MatchResult r = w.result(6, 5);
            ok(r.isPossible(), r.getReason()); eq("겹침 없음 (프랑스어1 자리)", r.getReason(), "사유"); });
        t("AC8 제외: 유의열 - 내 프랑스어1이 상대의 학술적글쓰기 (화 3-4)와 겹침", () -> { W w = new W(Paths.get("data-ac8")); MatchResult r = w.result(6, 8);
            eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); eq("상대 시간표 겹침: 대학생을 위한 학술적글쓰기", r.getReason(), "사유"); });
        t("후보 아님: 희망이 다른 김병조(#2), 내놓는 과목이 다른 신성환(#3)", () -> { W w = new W(Paths.get("data-ac8")); ok(!w.candidates(6).contains(2), "#2"); ok(!w.candidates(6).contains(3), "#3"); });
        t("자기 요청끼리는 매칭하지 않는다", () -> { W w = new W(Paths.get("data-ac8")); ok(!w.candidates(6).contains(7), "#7"); ok(!w.candidates(6).contains(6), "#6"); });
        t("AC6 후보 없음: 영화의 이해를 내놓는 사람이 없다 (#7)", () -> { W w = new W(Paths.get("data-ac8")); eq(Collections.emptyList(), w.match(7), "후보"); });
        t("상대 쪽에서 돌려도 같은 결론: 백찬샘(#1)에게 조혜라(#6)는 가능, 유의열(#8)에게는 제외", () -> { W w = new W(Paths.get("data-ac8"));
            ok(w.result(1, 6).isPossible(), "#1 -> #6"); eq(MatchVerdict.EXCLUDED_MINE, w.result(8, 6).getVerdict(), "#8 -> #6"); });
        t("매칭은 조회 전용: 시간표·요청 상태가 그대로", () -> { W w = new W(Paths.get("data-ac8")); String before = snapshot(w);
            for (SwapRequest r : w.board.all()) w.match(r.getId()); eq(before, snapshot(w), "상태"); });
        t("상대 시간표가 없으면 내 쪽만 보고 '상대 시간표 미확인'", () -> { Path dir = copyData("data-ac8", "plan-unknown");
            List<String> kept = new ArrayList<>();
            for (String l : Files.readAllLines(dir.resolve("students.csv"))) if (!l.startsWith(F)) kept.add(l);
            Files.write(dir.resolve("students.csv"), kept, StandardCharsets.UTF_8);
            W w = new W(dir); MatchResult r = w.result(6, 8); ok(r.isPossible(), r.getReason()); eq("겹침 없음 (상대 시간표 미확인)", r.getReason(), "사유"); });
        t("AC3 등록 거절: 전공 과목(이산수학)은 내놓을 수 없다", () -> { W w = new W(Paths.get("data-ac8"));
            try { w.board.register(w.stu(A), w.c(DISCRETE), "생명과 나눔"); } catch (RequestRejectedException e) { eq("이산수학", e.getTarget(), "대상"); has(e.getReason(), "전공 과목은 교환 요청을 등록할 수 없습니다"); eq(8, w.board.size(), "요청 수"); return; }
            throw new AssertionError("등록됨"); });
        t("등록 거절: 과목 목록에 없는 희망 과목 / 같은 내용의 대기 요청(#6)", () -> { W w = new W(Paths.get("data-ac8"));
            try { w.board.register(w.stu(A), w.c(FRENCH), "없는 과목"); throw new AssertionError("없는 과목이 등록됨"); } catch (RequestRejectedException e) { has(e.getReason(), "과목 목록에 없습니다"); }
            try { w.board.register(w.stu(A), w.c(FRENCH), "생명과 나눔"); throw new AssertionError("중복이 등록됨"); } catch (RequestRejectedException e) { has(e.getReason(), "(#6)"); }
            eq(8, w.board.size(), "요청 수"); });
        t("같은 과목을 내놓고 희망만 다른 요청은 등록된다 (#9)", () -> { W w = new W(Paths.get("data-ac8"));
            SwapRequest r = w.board.register(w.stu(A), w.c(FRENCH), "심리학과 예술"); eq(9, r.getId(), "번호"); eq(3, w.board.waitingOf(A).size(), "내 대기 요청");
            eq(Collections.singletonList(3), w.candidates(9), "신성환이 후보가 됨"); });
    }
    static List<String> codes(Student s) {
        List<String> out = new ArrayList<>();
        for (Course c : s.getTimetable().getCourses()) out.add(c.getCode());
        return out;
    }

    static void planConsole() {
        sec("10. 계획서 시나리오: 콘솔 화면 (data-ac8 사본으로 실제 프로그램 실행)");
        t("시작: 복원 안내 -> 학번 -> 메뉴", () -> { Path dir = copyData("data-ac8", "con-start");
            String out = runConsole(dir, A, "0");
            seq(out, "[복원] courses.csv", "과목 3242개", "[복원] students.csv", "학생 7명", "[복원] requests.csv", "교환 요청 8건", "학번 입력 > ", "[확인] 조혜라 님의 시간표를 불러왔습니다. (3과목)",
                    " 1. 과목 목록 보기", " 2. 내 시간표 보기 / 과목 추가", " 3. 교환 요청 등록", " 4. 교환 요청 목록 보기", " 5. 매칭 실행", " 9. 사용자 전환", " 0. 저장하고 종료",
                    "[저장] students.csv", "[저장] requests.csv", "구교환을 종료합니다."); });
        t("메뉴 1: 검색어로 과목 조회, 없는 검색어에도 계속 진행", () -> { Path dir = copyData("data-ac8", "con-list");
            String out = runConsole(dir, A, "1", "프랑스어1", "없는과목이름", "", "0");
            seq(out, "[과목 목록] 총 3242개", "[확인] '프랑스어1' 검색 결과 2개", "05306001", "05306002", "화 3-4", "구교환을 종료합니다."); });
        t("메뉴 2: 내 시간표 격자와 요약 줄", () -> { Path dir = copyData("data-ac8", "con-grid");
            String out = runConsole(dir, A, "2", "", "0");
            seq(out, "[내 시간표] 조혜라 (202612345)  3과목  8학점 (최대 18)", "|      월      |      화      |", " 3  |              |프랑스어1     |", " 4  |이산수학      |05306002      |",
                    " 전공: 이산수학, 운영체제   교양: 프랑스어1"); });
        t("메뉴 4: 요청 목록에 내 요청 표시", () -> { Path dir = copyData("data-ac8", "con-req");
            String out = runConsole(dir, A, "4", "0");
            seq(out, "[교환 요청 목록] 총 8건 (대기 8)", "#1", "백찬샘", "#6", "조혜라", "프랑스어1 002 (화 3-4)", "생명과 나눔", "대기  <- 내 요청", "#8", "유의열"); });
        t("메뉴 5 (AC1·AC2·AC8): 요청 #6 매칭 -> 가능 2명, 제외 사유 2건", () -> { Path dir = copyData("data-ac8", "con-match");
            String out = runConsole(dir, A, "5", "6", "0");
            seq(out, "내 대기 요청이 2건입니다.", "#6", "프랑스어1 (화 3-4) -> 생명과 나눔", "#7", "매칭할 요청 번호 > ", "[매칭 실행] 내 요청 #6",
                    "[가능]", "겹침 없음", "[제외]", "내 시간표 겹침: 이산수학", "[가능]", "프랑스어1 자리", "[제외]", "상대 시간표 겹침: 대학생을 위한 학술적글쓰기", "[결과] 교환 가능한 상대 2명");
            W w = new W(dir); for (SwapRequest r : w.board.all()) eq(RequestStatus.WAITING, r.getStatus(), "매칭 실행만으로 상태가 바뀜 #" + r.getId()); });
        t("메뉴 5 (AC6): 요청 #7은 후보가 없다", () -> { Path dir = copyData("data-ac8", "con-none");
            String out = runConsole(dir, A, "5", "7", "0");
            seq(out, "[매칭 실행] 내 요청 #7"); hasNot(out, "[가능]"); hasNot(out, "[제외]"); });
        t("메뉴 5: 목록에 없는 요청 번호·글자는 다시 묻는다 (AC9)", () -> { Path dir = copyData("data-ac8", "con-badno");
            String out = runConsole(dir, A, "5", "1", "abc", "6", "0");
            seq(out, "[오류]", "[오류]", "[매칭 실행] 내 요청 #6", "[결과] 교환 가능한 상대 2명"); });
        t("메뉴 3 (AC3): 전공 과목은 요청 등록 거절, 요청 수 그대로", () -> { Path dir = copyData("data-ac8", "con-major");
            String out = runConsole(dir, A, "3", "1", "", "0");
            seq(out, "[거절] 이산수학: 전공 과목은 교환 요청을 등록할 수 없습니다"); eq(8, new W(dir).board.size(), "요청 수"); });
        t("메뉴 3 -> 0 (AC7): 등록한 요청이 저장되고 다음 실행에서 복원된다", () -> { Path dir = copyData("data-ac8", "con-save");
            String first = runConsole(dir, A, "3", "3", "11934001", "y", "", "0");
            seq(first, "[확인] 11934001 -> 심리학과 예술", "[등록] 요청 #9 등록 완료", "상태: 대기", "[저장] requests.csv  교환 요청 9건");
            W w = new W(dir); eq(9, w.board.size(), "저장된 요청 수"); eq("프랑스어1 (화 3-4) -> 심리학과 예술", w.req(9).summary(), "요청 #9"); eq(A, w.req(9).getStudentId(), "요청자");
            String second = runConsole(dir, A, "4", "0");
            seq(second, "[복원] requests.csv", "교환 요청 9건", "[교환 요청 목록] 총 9건 (대기 9)", "#9", "심리학과 예술"); });
        t("메뉴 3: 등록 확인에서 n이면 취소, 저장되지 않는다", () -> { Path dir = copyData("data-ac8", "con-cancel");
            String out = runConsole(dir, A, "3", "3", "심리학과 예술", "n", "", "0");
            seq(out, "[취소]"); hasNot(out, "[등록] 요청 #9"); eq(8, new W(dir).board.size(), "요청 수"); });
        t("메뉴 9: 사용자 전환 뒤에는 그 학생의 요청이 '내 요청'", () -> { Path dir = copyData("data-ac8", "con-switch");
            String out = runConsole(dir, A, "9", B, "5", "0");
            seq(out, "[확인] 조혜라 님", "[저장]", "[확인] 백찬샘 님의 시간표를 불러왔습니다. (2과목)", "[매칭 실행] 내 요청 #1", "[가능]", "[결과] 교환 가능한 상대 1명", "<-> 조혜라 프랑스어1 002 (화 3-4)"); });
        t("새 학번: 이름을 받아 새 학생으로 시작, 요청이 없으면 매칭 안내", () -> { Path dir = copyData("data-ac8", "con-new");
            String out = runConsole(dir, "202699999", "새학생", "5", "0");
            seq(out, "[안내] 대기 중인 내 교환 요청이 없습니다"); W w = new W(dir);
            eq(8, w.registry.size(), "학생 수"); eq("새학생", w.stu("202699999").getName(), "이름"); eq(0, w.stu("202699999").getTimetable().size(), "과목 수"); });
        t("없는 메뉴 번호·글자·빈 줄에도 종료되지 않는다 (AC9)", () -> { Path dir = copyData("data-ac8", "con-menu");
            String out = runConsole(dir, A, "7", "abc", "", "-1", "0");
            seq(out, "[오류] 메뉴에 있는 번호(0~5, 9)를 입력하세요.", "[오류]", "구교환을 종료합니다."); });
        t("화면 출력은 ASCII와 한글·한자만 (박스 문자·특수 화살표·이모지 없음)", () -> { Path dir = copyData("data-ac8", "con-ascii");
            String out = runConsole(dir, A, "1", "생명과 나눔", "", "2", "", "4", "5", "6", "0");
            for (String l : out.split("\n")) {
                for (char ch : l.toCharArray()) ok(ch < 128 || (ch >= '가' && ch <= '힣') || (ch >= '一' && ch <= '鿿'), "허용되지 않는 문자 U+" + Integer.toHexString(ch) + ": " + l); } });
    }
}
