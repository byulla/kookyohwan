import kookyohwan.model.*;
import kookyohwan.service.*;
import kookyohwan.storage.FileStorage;
import kookyohwan.ui.TextUtil;
import kookyohwan.ui.TimetableGrid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class EdgeCaseTest {
    static int pass = 0, fail = 0;
    static String section = "";
    static final List<String> failures = new ArrayList<>();
    static Path base;
    static boolean quiet = true;

    interface Body { void run() throws Exception; }

    static void sec(String name) { section = name; System.out.println("\n== " + name); }
    static void done() { System.out.println("     여기까지 통과 " + pass + " / 실패 " + fail); }

    static void t(String name, Body body) {
        try {
            body.run();
            pass++;
            if (!quiet) System.out.println("  ok   " + name);
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
    static void has(String text, String part) { if (!text.contains(part)) throw new AssertionError("'" + part + "' 없음: " + clip(text)); }
    static void hasNot(String text, String part) { if (text.contains(part)) throw new AssertionError("'" + part + "' 가 있으면 안 됨: " + clip(text)); }
    static String clip(String text) { return text.length() <= 1200 ? text : "..." + text.substring(text.length() - 1200); }
    static String show(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c < 32 || c == 127 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        return sb.length() <= 40 ? sb.toString() : sb.substring(0, 40) + "...(" + s.length() + "자)";
    }
    static void throwsIAE(Body b, String what) {
        try { b.run(); } catch (IllegalArgumentException e) { return; }
        catch (Throwable e) { throw new AssertionError(what + ": IllegalArgumentException이 아닌 " + e); }
        throw new AssertionError(what + ": 예외가 안 남");
    }
    static String rejected(Body b) {
        try { b.run(); } catch (RequestRejectedException e) { return e.getReason(); }
        catch (Throwable e) { throw new AssertionError("RequestRejectedException이 아닌 " + e); }
        return null;
    }
    static void seq(String out, String... parts) {
        int at = 0;
        for (String p : parts) {
            int i = out.indexOf(p, at);
            if (i < 0) throw new AssertionError("'" + p + "' 가 (그 순서에) 없음\n----\n" + clip(out.substring(Math.min(at, out.length()))));
            at = i + p.length();
        }
    }

    static final String HEAD_C = "코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원";
    static final String HEAD_S = "학번,이름,과목코드,시간표상태";
    static final String HEAD_R = "번호,학번,내놓는과목코드,희망과목명,상태";

    static final String[] COURSES = {
        "10001001,전공A,001,MAJOR,월1-3,교수,강의실,3,0,0",
        "10002001,전공B,001,MAJOR,화1-3,교수,강의실,3,0,0",
        "10003001,전공C,001,MAJOR,수1-3,교수,강의실,3,0,0",
        "10004001,전공D,001,MAJOR,목1-3,교수,강의실,3,0,0",
        "10005001,전공E,001,MAJOR,금1-3,교수,강의실,3,0,0",
        "20001001,프랑스어,001,GENERAL,화5-6,교수,강의실,2,0,0",
        "20001002,프랑스어,002,GENERAL,화7-8,교수,강의실,2,0,0",
        "20002001,생명,001,GENERAL,수5-6,교수,강의실,2,0,0",
        "20002002,생명,002,GENERAL,월2-3,교수,강의실,2,0,0",
        "20002003,생명,003,GENERAL,화5-6,교수,강의실,2,0,0",
        "20002004,생명,004,GENERAL,목7-8,교수,강의실,2,0,0",
        "20003001,일본어,001,GENERAL,목5-6,교수,강의실,3,0,0",
        "20003002,일본어,002,GENERAL,금5-6,교수,강의실,3,0,0",
        "20004001,큰교양,001,GENERAL,금7-9,교수,강의실,3,0,0",
        "20005001,한학점,001,GENERAL,월9,교수,강의실,1,0,0",
        "30001001,교직론,001,TEACHING,월7-8,교수,강의실,2,0,0",
        "40001001,군사학,001,MILITARY,토1-2,교수,강의실,2,0,0",
        "20006001,온라인교양,001,GENERAL,,교수,,2,0,0",
        "20006002,온라인교양,002,GENERAL,,교수,,2,0,0",
        "20007001,다중,001,GENERAL,월5;수7-8,교수,강의실,3,0,0",
        "20008001,주말교양,001,GENERAL,토3-4,교수,강의실,2,0,0",
        "20009001,마감교양,001,GENERAL,수9-10,교수,강의실,2,2,2",
        "20009002,마감교양,002,GENERAL,목9-10,교수,강의실,2,2,1",
        "20009003,마감교양,003,GENERAL,금10-11,교수,강의실,2,0,0",
        "20010001,데이터베이스,001,GENERAL,수11-12,교수,강의실,3,0,0",
        "10010001,데이터베이스,001,MAJOR,목11-12,교수,강의실,3,0,0",
        "20011001,영화,001,GENERAL,월11-12,교수,강의실,2,0,0",
        "20012001,인접,001,GENERAL,화4,교수,강의실,1,0,0",
        "20013001,넓은,001,GENERAL,화3-8,교수,강의실,3,0,0",
        "20014001,꽉찬교양,001,GENERAL,수13-14,교수,강의실,2,1,1",
        "20015001,\"쉼표,과목\",001,GENERAL,금12-13,교수,\"A-1,B-2\",2,0,0",
        "20016001,온라인둘,001,GENERAL,,교수,,2,0,0",
        "20017001,Java Programming,001,GENERAL,일1-2,교수,강의실,2,0,0",
    };
    static final String M1 = "10001001", M2 = "10002001", M3 = "10003001", M4 = "10004001", M5 = "10005001",
            FR1 = "20001001", FR2 = "20001002", LF1 = "20002001", LF2 = "20002002", LF3 = "20002003", LF4 = "20002004",
            JP1 = "20003001", JP2 = "20003002", BIG = "20004001", ONE = "20005001", TCH = "30001001", MIL = "40001001",
            ON1 = "20006001", ON2 = "20006002", MS = "20007001", SAT = "20008001", CF = "20009001", CO = "20009002",
            CN = "20009003", DBG = "20010001", DBM = "10010001", MOV = "20011001", ADJ = "20012001", WIDE = "20013001",
            FULL = "20014001", COMMA = "20015001", ONX = "20016001", JAVA = "20017001";

    static Path dirOf(String name) throws IOException {
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }
    static void put(Path dir, String file, String... lines) throws IOException {
        Files.write(dir.resolve(file), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    static Path writeCourses(String dirName) throws IOException {
        Path dir = dirOf(dirName);
        List<String> lines = new ArrayList<>();
        lines.add(HEAD_C);
        lines.addAll(Arrays.asList(COURSES));
        Files.write(dir.resolve("courses.csv"), lines, StandardCharsets.UTF_8);
        Files.deleteIfExists(dir.resolve("students.csv"));
        Files.deleteIfExists(dir.resolve("requests.csv"));
        return dir;
    }

    static class W {
        final CourseCatalog catalog = new CourseCatalog();
        final StudentRegistry registry = new StudentRegistry();
        final RequestBoard board = new RequestBoard(catalog);
        final MatchService matcher = new MatchService(board, registry);
        final ExchangeService exchange = new ExchangeService(board, registry, matcher);
        final EnrollmentService enroll = new EnrollmentService(registry);
        final List<Course> courses;

        W() throws IOException { this(writeCourses("syn")); }
        W(Path dir) throws IOException {
            courses = new FileStorage(dir.toString()).loadCourses();
            for (Course c : courses) catalog.add(c);
        }
        Course c(String code) { return catalog.findByCode(code).orElseThrow(() -> new AssertionError("과목 없음 " + code)); }
        Student stu(String id, boolean confirmed, String... codes) {
            Timetable tt = new Timetable();
            for (String code : codes) ok(tt.restore(c(code)), "준비 시간표 restore 실패 " + code);
            Student s = new Student(id, "학생" + id, tt, confirmed);
            registry.put(s);
            return s;
        }
        Student get(String id) { return registry.find(id).orElseThrow(() -> new AssertionError("학생 없음 " + id)); }
        SwapRequest raw(String sid, String give, String want, RequestStatus st) {
            int id = 1;
            for (SwapRequest r : board.all()) id = Math.max(id, r.getId() + 1);
            SwapRequest r = new SwapRequest(id, sid, c(give), want, st);
            board.put(r);
            return r;
        }
        SwapRequest raw(String sid, String give, String want) { return raw(sid, give, want, RequestStatus.WAITING); }
        List<MatchResult> match(SwapRequest mine) {
            return matcher.match(mine, registry.find(mine.getStudentId()).get().getTimetable());
        }
        MatchResult resultFor(SwapRequest mine, SwapRequest other) {
            for (MatchResult r : match(mine)) if (r.getRequest().getId() == other.getId()) return r;
            return null;
        }
        String snapshot() {
            StringBuilder sb = new StringBuilder();
            for (Student s : registry.all()) {
                sb.append(s.getId()).append("/").append(s.getName()).append(s.isTimetableConfirmed() ? "C" : "E").append(":");
                for (Course c : s.getTimetable().getCourses()) sb.append(c.getCode()).append(";");
                sb.append("|");
            }
            for (SwapRequest r : board.all())
                sb.append(r.getId()).append(r.getStudentId()).append(r.getGiveCourse().getCode())
                        .append(r.getWantName()).append(r.getStatus()).append("|");
            return sb.toString();
        }
        List<String> codes(String id) {
            List<String> out = new ArrayList<>();
            for (Course c : get(id).getTimetable().getCourses()) out.add(c.getCode());
            return out;
        }
        void save(Path dir) throws IOException {
            FileStorage fs = new FileStorage(dir.toString());
            fs.saveStudents(registry.all());
            fs.saveRequests(board.all());
        }
    }
    static W load(Path dir) throws IOException {
        W x = new W(dir);
        FileStorage fs = new FileStorage(dir.toString());
        for (Student s : fs.loadStudents(x.courses)) x.registry.put(s);
        for (SwapRequest r : fs.loadRequests(x.courses)) x.board.put(r);
        return x;
    }

    static Set<Integer> cells(Course c) {
        Set<Integer> out = new HashSet<>();
        for (TimeSlot t : c.getTimes())
            for (int p = t.getStart(); p <= t.getEnd(); p++) out.add(t.getDay().ordinal() * 100 + p);
        return out;
    }
    static boolean clash(Course a, Course b) {
        Set<Integer> x = cells(a);
        x.retainAll(cells(b));
        return !x.isEmpty();
    }
    static String subject(Course c) {
        String code = c.getCode(), sec = c.getSection();
        return (code.endsWith(sec) ? code.substring(0, code.length() - sec.length()) : code) + "/" + c.getName();
    }
    static List<String> violations(W w) {
        List<String> out = new ArrayList<>();
        for (Student s : w.registry.all()) {
            List<Course> cs = s.getTimetable().getCourses();
            int credits = 0;
            for (int i = 0; i < cs.size(); i++) {
                credits += cs.get(i).getCredits();
                for (int j = i + 1; j < cs.size(); j++) {
                    if (cs.get(i).getCode().equals(cs.get(j).getCode())) out.add(s.getId() + " 같은 분반 두 번 " + cs.get(i).getCode());
                    else if (clash(cs.get(i), cs.get(j))) out.add(s.getId() + " 시간 겹침 " + cs.get(i).getCode() + "/" + cs.get(j).getCode());
                    else if (subject(cs.get(i)).equals(subject(cs.get(j)))) out.add(s.getId() + " 같은 과목 두 분반 " + cs.get(i).getCode() + "/" + cs.get(j).getCode());
                }
            }
            if (credits > 18) out.add(s.getId() + " " + credits + "학점");
        }
        Set<String> seen = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (SwapRequest r : w.board.all()) {
            if (!ids.add(r.getId())) out.add("요청 번호 중복 #" + r.getId());
            if (r.getId() < 1) out.add("요청 번호가 1보다 작음 #" + r.getId());
            if (!r.isWaiting()) continue;
            Optional<Student> owner = w.registry.find(r.getStudentId());
            if (owner.isPresent() && !owner.get().getTimetable().contains(r.getGiveCourse().getCode()))
                out.add("요청 #" + r.getId() + " 내놓는 과목이 시간표에 없음");
            if (owner.isPresent() && !owner.get().isTimetableConfirmed()) out.add("요청 #" + r.getId() + " 편집 중인 학생의 요청");
            if (!r.getGiveCourse().isGeneral()) out.add("요청 #" + r.getId() + " 교양이 아닌 과목을 내놓음");
            if (!seen.add(r.getStudentId() + r.getGiveCourse().getCode() + r.getWantName()))
                out.add("요청 #" + r.getId() + " 같은 내용의 대기 요청 중복");
        }
        return out;
    }

    static Course course(String code, String name, CourseType type, int credits, String... times) {
        List<TimeSlot> slots = new ArrayList<>();
        for (String s : times) slots.add(TimeSlot.parse(s));
        return new Course(code, name, code.substring(Math.max(0, code.length() - 3)), type, slots, "교수", "강의실", credits);
    }
    static Timetable table(Course... courses) {
        Timetable tt = new Timetable();
        for (Course c : courses) ok(tt.restore(c), "준비 시간표 restore 실패 " + c.getCode());
        return tt;
    }
    static List<String> codes(Timetable tt) {
        List<String> out = new ArrayList<>();
        for (Course c : tt.getCourses()) out.add(c.getCode());
        return out;
    }

    static synchronized String console(Path dir, String... lines) {
        return consoleRaw(dir, (String.join("\n", lines) + (lines.length == 0 ? "" : "\n")).getBytes(StandardCharsets.UTF_8));
    }
    static synchronized String consoleRaw(Path dir, byte[] input) {
        PrintStream originalOut = System.out;
        InputStream originalIn = System.in;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Throwable thrown = null;
        try {
            System.setIn(new ByteArrayInputStream(input));
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            kookyohwan.Main.main(new String[]{dir.toString()});
        } catch (Throwable e) {
            thrown = e;
        } finally {
            System.setOut(originalOut);
            System.setIn(originalIn);
        }
        String out = buffer.toString(StandardCharsets.UTF_8);
        if (thrown != null) {
            StackTraceElement[] st = thrown.getStackTrace();
            throw new AssertionError("프로그램이 예외로 죽음: " + thrown + (st.length > 0 ? " at " + st[0] : "") + "\n" + clip(out));
        }
        return out;
    }
    static void endsCleanly(String out) {
        String trimmed = out.trim();
        ok(trimmed.endsWith("구교환을 종료합니다.") || trimmed.endsWith("종료합니다."), "정상 종료 문구로 끝나지 않음: " + clip(out));
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

    static Path world(String name) throws IOException {
        Path dir = writeCourses(name);
        W w = new W(dir);
        w.registry.put(new Student("100", "나편집", new Timetable(), false));
        w.stu("101", true, M1, M3, FR1); w.raw("101", FR1, "생명");
        w.stu("201", true, LF1); w.raw("201", LF1, "프랑스어");
        w.stu("202", true, LF2); w.raw("202", LF2, "프랑스어");
        w.stu("204", true, LF4, WIDE); w.raw("204", LF4, "프랑스어");
        w.stu("205", true, M1, M2, M3, M4, M5, JP1);
        w.stu("206", true, FR2, LF4);
        w.stu("300", true);
        w.stu("207", true, M2, JP1, MOV); w.raw("207", JP1, "생명"); w.raw("207", MOV, "프랑스어"); w.raw("207", MOV, "한학점");
        w.save(dir);
        return dir;
    }
    static String fileState(Path dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String f : new String[]{"students.csv", "requests.csv"})
            sb.append(f).append("=").append(Files.exists(dir.resolve(f)) ? new String(Files.readAllBytes(dir.resolve(f)), StandardCharsets.UTF_8) : "(없음)").append("\n");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        for (String a : args) if (a.equals("-v")) quiet = false;
        base = Files.createTempDirectory("kookyohwan-edge");
        timeSlotParsing();
        timeSlotOverlap();
        enums();
        courses();
        timetable();
        textUtil();
        timetableGrid();
        catalogAndRegistry();
        requestRules();
        matching();
        exchange();
        enrollment();
        storageCourses();
        storageStudents();
        storageRequests();
        storageRoundTrip();
        consoleStartup();
        consoleMenu();
        consoleLogin();
        consoleTimetable();
        consoleRequests();
        consoleEndOfInput();
        consoleFuzz();
        realData();

        System.out.println("\n==================================================");
        System.out.println("통과 " + pass + " / 실패 " + fail);
        for (String f : failures) System.out.println("  " + f);
        System.exit(fail == 0 ? 0 : 1);
    }

    static void timeSlotParsing() {
        sec("1. 시간 표기 읽기 (TimeSlot.parse)");
        String[][] valid = {
            {"월1", "MON", "1", "1", "월 1"}, {"월1-1", "MON", "1", "1", "월 1"}, {"월1-2", "MON", "1", "2", "월 1-2"},
            {"화3-4", "TUE", "3", "4", "화 3-4"}, {"수5-6", "WED", "5", "6", "수 5-6"}, {"목7-8", "THU", "7", "8", "목 7-8"},
            {"금9-10", "FRI", "9", "10", "금 9-10"}, {"토1-2", "SAT", "1", "2", "토 1-2"}, {"일13-14", "SUN", "13", "14", "일 13-14"},
            {"월01", "MON", "1", "1", "월 1"}, {"월01-02", "MON", "1", "2", "월 1-2"}, {"월9-10", "MON", "9", "10", "월 9-10"},
            {"월10-11", "MON", "10", "11", "월 10-11"}, {"월14", "MON", "14", "14", "월 14"}, {"월1-14", "MON", "1", "14", "월 1-14"},
            {"월99", "MON", "99", "99", "월 99"}, {"월1-99", "MON", "1", "99", "월 1-99"}, {" 월3-4", "MON", "3", "4", "월 3-4"},
            {"월3-4 ", "MON", "3", "4", "월 3-4"}, {"\t월3\t", "MON", "3", "3", "월 3"}, {"화12-12", "TUE", "12", "12", "화 12"},
            {"일1", "SUN", "1", "1", "일 1"}, {"토14", "SAT", "14", "14", "토 14"}, {"금2-13", "FRI", "2", "13", "금 2-13"},
            {"수07-9", "WED", "7", "9", "수 7-9"}, {"목3-03", "THU", "3", "3", "목 3"}, {"월3-4\n", "MON", "3", "4", "월 3-4"},
            {"\r\n화1-2\r\n", "TUE", "1", "2", "화 1-2"},
        };
        for (String[] v : valid) t("읽힘: '" + show(v[0]) + "' -> " + v[4], () -> { TimeSlot s = TimeSlot.parse(v[0]);
            eq(Day.valueOf(v[1]), s.getDay(), "요일"); eq(Integer.parseInt(v[2]), s.getStart(), "시작"); eq(Integer.parseInt(v[3]), s.getEnd(), "끝"); eq(v[4], s.toString(), "표기"); });
        for (String[] v : valid) t("표기를 다시 읽어도 같은 구간: " + v[4], () -> { TimeSlot s = TimeSlot.parse(v[4].replace(" ", ""));
            eq(v[4], s.toString(), "표기"); });
        String[] invalid = {"", " ", "\t", "월", "3", "3-4", "월3-", "월-4", "월 3-4", "월3 -4", "월3- 4", "월3~4", "월0", "월0-1", "월5-3",
            "월3-4-5", "월요일3", "월100", "월1-100", "월-1", "월+1", "월1.5", "월1,2", "월3–4", "월３", "MON3", "mon3", "M3", "月3",
            "월a", "월3a", "a월3", "월월3", "월3-4;화1", "월3,화4", "월1-0", "월2-1", "월00", "월0-0", "월三", "-", "월--3", "월3--4", "화", "토-",
            "일0", "월\t3", "１-２", "null", "월3-4 화1-2", "월3/4", "월3:4", "월(3)", "#", "월٣", "월3-4.", "월 ", "3월", "월3-월4",
            "월3-4교시", "월3교시", "월1-2-", "월-", "ㅇ3", "😀3", "월0x3", "월1e1", "월 3 - 4", "월3 4"};
        for (String bad : invalid) t("거절: '" + show(bad) + "'", () -> throwsIAE(() -> TimeSlot.parse(bad), bad));
        int[][] badRange = {{0, 1}, {-1, 1}, {2, 1}, {1, 0}, {0, 0}, {-3, -2}, {Integer.MIN_VALUE, 1}, {5, 4}, {14, 13}, {Integer.MAX_VALUE, 1}};
        for (int[] r : badRange) t("생성 거절: 교시 " + r[0] + "-" + r[1], () -> throwsIAE(() -> new TimeSlot(Day.MON, r[0], r[1]), "범위"));
        int[][] goodRange = {{1, 1}, {1, 14}, {14, 14}, {1, 100}, {7, 8}, {Integer.MAX_VALUE, Integer.MAX_VALUE}};
        for (int[] r : goodRange) t("생성 허용: 교시 " + r[0] + "-" + r[1], () -> { TimeSlot s = new TimeSlot(Day.FRI, r[0], r[1]); eq(r[0], s.getStart(), "시작"); eq(r[1], s.getEnd(), "끝"); });
        t("요일 없이는 만들 수 없다 (null)", () -> throwsIAE(() -> new TimeSlot(null, 1, 2), "null 요일"));
        t("오류 문구에 잘못된 입력과 예시가 들어 있다", () -> { try { TimeSlot.parse("월3~4"); } catch (IllegalArgumentException e) { has(e.getMessage(), "월3~4"); has(e.getMessage(), "월3-4"); return; } throw new AssertionError("예외 없음"); });
        done();
    }

    static void timeSlotOverlap() {
        sec("2. 교시 겹침 (TimeSlot.overlaps)");
        String[][] pairs = {
            {"월1-2", "월1-2", "y"}, {"월1-2", "월2-3", "y"}, {"월1-2", "월3-4", "n"}, {"월1-4", "월2-3", "y"}, {"월2-3", "월1-4", "y"},
            {"월1", "월1", "y"}, {"월1", "월2", "n"}, {"월1-14", "월14", "y"}, {"월1-14", "월7", "y"}, {"월3-4", "화3-4", "n"},
            {"월3-4", "일3-4", "n"}, {"토1-2", "토2", "y"}, {"토1-2", "일1-2", "n"}, {"월5", "월4-6", "y"}, {"월4-6", "월7-9", "n"},
            {"월4-6", "월6-9", "y"}, {"월4-6", "월1-3", "n"}, {"월4-6", "월1-4", "y"}, {"금9-10", "금10-11", "y"}, {"금9-10", "금11-12", "n"},
            {"수13-14", "수14", "y"}, {"수13-14", "수12", "n"}, {"목1-99", "목50", "y"}, {"목1", "목99", "n"}, {"화3-8", "화4", "y"},
            {"화3-8", "화9", "n"}, {"화3-8", "화2", "n"}, {"화3-8", "화1-3", "y"}, {"화3-8", "화8-9", "y"}, {"일1-2", "월1-2", "n"},
        };
        for (String[] p : pairs) t(p[0] + " / " + p[1] + " -> " + (p[2].equals("y") ? "겹침" : "안 겹침") + " (양방향)", () -> {
            TimeSlot a = TimeSlot.parse(p[0]), b = TimeSlot.parse(p[1]); boolean expect = p[2].equals("y");
            eq(expect, a.overlaps(b), "a.overlaps(b)"); eq(expect, b.overlaps(a), "b.overlaps(a)"); });
        for (int s = 1; s <= 6; s++) for (int e = s; e <= 6; e++) { final int fs = s, fe = e;
            t("전수 검사: 월" + s + "-" + e + " 과 1~6교시 모든 구간·모든 요일", () -> { TimeSlot a = new TimeSlot(Day.MON, fs, fe);
                ok(a.overlaps(a), "자기 자신");
                for (Day d : Day.values()) for (int s2 = 1; s2 <= 6; s2++) for (int e2 = s2; e2 <= 6; e2++) {
                    boolean expect = d == Day.MON && !(e2 < fs || fe < s2);
                    eq(expect, a.overlaps(new TimeSlot(d, s2, e2)), d.getLabel() + s2 + "-" + e2); } }); }
        done();
    }

    static void enums() {
        sec("3. 요일·구분·상태·판정 (enum)");
        String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
        for (int i = 0; i < labels.length; i++) { final int at = i; t("요일 라벨 '" + labels[i] + "' <-> " + Day.values()[i], () -> { eq(Day.values()[at], Day.fromLabel(labels[at]), "fromLabel"); eq(labels[at], Day.values()[at].getLabel(), "getLabel"); }); }
        String[] badLabels = {"", " ", "월 ", " 월", "월요일", "Mon", "MON", "mon", "月", "X", "0", "월화", "휴", "평일", "ㅇ"};
        for (String b : badLabels) t("모르는 요일 라벨 거절: '" + show(b) + "'", () -> throwsIAE(() -> Day.fromLabel(b), b));
        t("요일 라벨 null도 예외로 거절 (NullPointerException 아님)", () -> throwsIAE(() -> Day.fromLabel(null), "null"));
        String[][] names = {{"MAJOR", "전공"}, {"GENERAL", "교양"}, {"TEACHING", "교직"}, {"MILITARY", "군사"}};
        for (String[] n : names) t("파일에 쓰는 구분 이름 " + n[0] + " = 화면 '" + n[1] + "'", () -> eq(n[1], CourseType.valueOf(n[0]).getLabel(), "라벨"));
        String[][] states = {{"WAITING", "대기"}, {"MATCHED", "매칭완료"}, {"CANCELLED", "취소"}};
        for (String[] n : states) t("파일에 쓰는 상태 이름 " + n[0] + " = 화면 '" + n[1] + "'", () -> eq(n[1], RequestStatus.valueOf(n[0]).getLabel(), "라벨"));
        String[][] verdicts = {{"POSSIBLE", "가능"}, {"EXCLUDED_MINE", "제외"}, {"EXCLUDED_PARTNER", "제외"}};
        for (String[] n : verdicts) t("판정 " + n[0] + " = 화면 '" + n[1] + "'", () -> eq(n[1], MatchVerdict.valueOf(n[0]).getLabel(), "라벨"));
        t("enum 개수: 요일 7, 구분 4, 상태 3, 판정 3", () -> { eq(7, Day.values().length, "요일"); eq(4, CourseType.values().length, "구분"); eq(3, RequestStatus.values().length, "상태"); eq(3, MatchVerdict.values().length, "판정"); });
        t("라벨은 ASCII와 한글만", () -> { List<String> all = new ArrayList<>();
            for (Day d : Day.values()) all.add(d.getLabel()); for (CourseType c : CourseType.values()) all.add(c.getLabel());
            for (RequestStatus s : RequestStatus.values()) all.add(s.getLabel()); for (MatchVerdict v : MatchVerdict.values()) all.add(v.getLabel());
            for (String l : all) for (char ch : l.toCharArray()) ok(ch < 128 || (ch >= '가' && ch <= '힣'), l); });
        t("excluded()에 '가능' 판정을 넣으면 거절", () -> { Course c = course("20001001", "프랑스어", CourseType.GENERAL, 2, "화5-6");
            throwsIAE(() -> MatchResult.excluded(new SwapRequest(1, "1", c, "생명", RequestStatus.WAITING), c, MatchVerdict.POSSIBLE, "x"), "POSSIBLE"); });
        t("판정 결과의 뺄 과목 목록은 밖에서 못 바꾼다", () -> { Course c = course("20001001", "프랑스어", CourseType.GENERAL, 2, "화5-6");
            List<Course> drop = new ArrayList<>(Collections.singletonList(c));
            MatchResult r = MatchResult.resolvable(new SwapRequest(1, "1", c, "생명", RequestStatus.WAITING), c, "내 시간표 겹침: 프랑스어", drop);
            drop.clear(); eq(1, r.getDroppable().size(), "원본 목록을 비워도 그대로"); ok(r.isResolvable(), "뺄 수 있음"); ok(!r.isPossible(), "가능은 아님");
            try { r.getDroppable().clear(); } catch (UnsupportedOperationException e) { return; } throw new AssertionError("목록이 비워짐"); });
        done();
    }

    static void courses() {
        sec("4. 과목 (Course)");
        Object[][] timeTexts = {
            {new String[]{}, ""}, {new String[]{"월1"}, "월 1"}, {new String[]{"월1-2"}, "월 1-2"}, {new String[]{"목4-5", "금3"}, "목 4-5, 금 3"},
            {new String[]{"금3", "목4-5"}, "금 3, 목 4-5"}, {new String[]{"월1", "화1", "수1"}, "월 1, 화 1, 수 1"}, {new String[]{"토1-2", "일3-4"}, "토 1-2, 일 3-4"},
            {new String[]{"월1-2", "월3-4"}, "월 1-2, 월 3-4"}, {new String[]{"월13-14"}, "월 13-14"},
        };
        for (Object[] c : timeTexts) t("시간 표기: " + Arrays.toString((String[]) c[0]) + " -> '" + c[1] + "'", () -> eq(c[1], course("20001001", "가", CourseType.GENERAL, 2, (String[]) c[0]).timesToString(), "표기"));
        String[][] subjects = {
            {"05306002", "002", "05306"}, {"05306001", "001", "05306"}, {"ABC", "001", "ABC"}, {"001", "001", ""}, {"11393006", "006", "11393"},
            {"11393006", "", "11393006"}, {"11393006", "6", "1139300"}, {"11393006", "0006", "11393006"}, {"1", "1", ""}, {"12345678", "678", "12345"},
        };
        for (String[] s : subjects) t("분반을 뺀 앞부분: 코드 " + s[0] + " 분반 '" + s[1] + "' -> '" + s[2] + "'", () ->
            eq(s[2], new Course(s[0], "가", s[1], CourseType.GENERAL, Collections.emptyList()).getSubjectCode(), "앞부분"));
        Object[][] same = {
            {"05306001", "프랑스어1", "05306002", "프랑스어1", true}, {"05306001", "프랑스어1", "05306001", "프랑스어1", true},
            {"05306001", "프랑스어1", "05307001", "프랑스어1", false}, {"05306001", "프랑스어1", "05306002", "프랑스어2", false},
            {"05306001", "프랑스어1", "05306002", "프랑스어1 ", false}, {"05306001", "java", "05306002", "Java", false},
            {"20010001", "데이터베이스", "10010001", "데이터베이스", false}, {"05306001", "", "05306002", "", true},
        };
        for (Object[] s : same) t("같은 과목 판정 (양방향): " + s[0] + " '" + s[1] + "' / " + s[2] + " '" + s[3] + "' -> " + s[4], () -> {
            Course a = course((String) s[0], (String) s[1], CourseType.GENERAL, 2), b = course((String) s[2], (String) s[3], CourseType.GENERAL, 2);
            eq(s[4], a.isSameSubject(b), "a-b"); eq(s[4], b.isSameSubject(a), "b-a"); });
        Object[][] over = {
            {new String[]{"월1-2"}, new String[]{"월2-3"}, true}, {new String[]{"월1-2"}, new String[]{"월3-4"}, false}, {new String[]{}, new String[]{"월1"}, false},
            {new String[]{}, new String[]{}, false}, {new String[]{"월1", "수3"}, new String[]{"화1", "수3"}, true}, {new String[]{"월1", "수3"}, new String[]{"화1", "수4"}, false},
            {new String[]{"목4-5", "금3"}, new String[]{"금3-4"}, true}, {new String[]{"목4-5", "금3"}, new String[]{"금4"}, false}, {new String[]{"토1-2"}, new String[]{"일1-2"}, false},
            {new String[]{"월1-14"}, new String[]{"월14"}, true}, {new String[]{"월1", "화1", "수1", "목1", "금1"}, new String[]{"금1"}, true}, {new String[]{"월5", "수7-8"}, new String[]{"수5-6"}, false},
        };
        for (Object[] o : over) t("과목 겹침 (양방향): " + Arrays.toString((String[]) o[0]) + " / " + Arrays.toString((String[]) o[1]) + " -> " + o[2], () -> {
            Course a = course("10001001", "가", CourseType.MAJOR, 3, (String[]) o[0]), b = course("10002001", "나", CourseType.MAJOR, 3, (String[]) o[1]);
            eq(o[2], a.overlaps(b), "a-b"); eq(o[2], b.overlaps(a), "b-a"); });
        for (CourseType type : CourseType.values()) t("교양 여부: " + type + " -> " + (type == CourseType.GENERAL), () -> eq(type == CourseType.GENERAL, course("20001001", "가", type, 2).isGeneral(), "isGeneral"));
        t("시간 목록은 생성 뒤 원본을 바꿔도 그대로", () -> { List<TimeSlot> src = new ArrayList<>(); src.add(TimeSlot.parse("월1"));
            Course c = new Course("1", "가", "001", CourseType.GENERAL, src); src.add(TimeSlot.parse("화1")); src.clear(); eq(1, c.getTimes().size(), "시간 수"); });
        t("시간 목록에 직접 추가·삭제 불가", () -> { Course c = course("20001001", "가", CourseType.GENERAL, 2, "월1");
            try { c.getTimes().clear(); } catch (UnsupportedOperationException e) { return; } throw new AssertionError("비워짐"); });
        t("짧은 생성자의 기본값: 교수·강의실 빈 문자열, 학점·정원·수강인원 0", () -> { Course c = new Course("1", "가", "001", CourseType.GENERAL, Collections.emptyList());
            eq("", c.getProfessor(), "교수"); eq("", c.getRoom(), "강의실"); eq(0, c.getCredits(), "학점"); eq(0, c.getCapacity(), "정원"); eq(0, c.getBaseEnrolled(), "수강인원"); });
        t("toString: 시간이 없으면 빈 괄호", () -> eq("20006001 온라인교양 001 ()", course("20006001", "온라인교양", CourseType.GENERAL, 2).toString(), "표기"));
        t("자기 자신과는 같은 과목이고 (시간이 있으면) 겹친다", () -> { Course c = course("20001001", "가", CourseType.GENERAL, 2, "월1"); ok(c.isSameSubject(c), "같은 과목"); ok(c.overlaps(c), "겹침");
            Course n = course("20006001", "나", CourseType.GENERAL, 2); ok(!n.overlaps(n), "시간 없는 과목은 자기와도 안 겹침"); });
        done();
    }

    static void timetable() throws IOException {
        sec("5. 시간표 (Timetable)");
        W w = new W();
        t("빈 시간표: 0과목 0학점, 무엇과도 안 겹침", () -> { Timetable tt = new Timetable(); eq(0, tt.size(), "과목"); eq(0, tt.getTotalCredits(), "학점");
            eq(Collections.emptyList(), tt.findConflicts(w.c(M1), null), "겹침"); ok(tt.findConflict(w.c(M1), null).isEmpty(), "Optional"); ok(!tt.contains(M1), "contains"); ok(tt.findByCode(M1).isEmpty(), "findByCode"); });
        for (Course c : w.courses) t("빈 시간표에 " + c.getCode() + " " + c.getName() + " 하나는 언제나 들어간다", () -> { Timetable tt = new Timetable(); ok(tt.add(c), "add"); eq(1, tt.size(), "과목 수"); eq(c.getCredits(), tt.getTotalCredits(), "학점"); ok(tt.contains(c.getCode()), "contains"); });
        for (Course c : w.courses) t(c.getCode() + " 를 두 번 넣으면 두 번째는 거절", () -> { Timetable tt = table(c); ok(!tt.add(c), "두 번째 add"); ok(!tt.restore(c), "두 번째 restore"); eq(1, tt.size(), "과목 수"); });
        String[][] addCases = {
            {M1, LF2, "n", "월1-3 과 월2-3"}, {M2, WIDE, "n", "화1-3 과 화3-8"}, {M2, ADJ, "y", "화1-3 다음 화4"}, {ADJ, FR1, "y", "화4 다음 화5-6"},
            {FR1, FR2, "n", "같은 과목 다른 분반 (시간은 안 겹침)"}, {FR1, LF3, "n", "화5-6 끼리"}, {ON1, ON2, "n", "시간 없는 같은 과목"}, {ON1, ONX, "y", "시간 없는 다른 과목"},
            {DBG, DBM, "y", "이름만 같은 교양·전공"}, {MIL, SAT, "y", "토1-2 와 토3-4"}, {MS, LF1, "y", "월5;수7-8 과 수5-6"}, {MS, CF, "y", "수7-8 과 수9-10"},
            {BIG, JP2, "y", "금7-9 와 금5-6"}, {BIG, CN, "y", "금7-9 와 금10-11"}, {CN, COMMA, "y", "금10-11 과 금12-13"}, {LF1, LF4, "n", "생명 두 분반"},
            {JP1, JP2, "n", "일본어 두 분반"}, {M4, JP1, "y", "목1-3 과 목5-6"}, {WIDE, FR2, "n", "화3-8 안의 화7-8"}, {WIDE, ADJ, "n", "화3-8 안의 화4"},
            {M1, ONE, "y", "월1-3 과 월9"}, {TCH, ONE, "y", "월7-8 과 월9"}, {M1, MS, "y", "월1-3 과 월5"}, {JAVA, SAT, "y", "일1-2 와 토3-4"},
        };
        for (String[] a : addCases) t("추가: " + a[0] + " 뒤에 " + a[1] + " (" + a[3] + ") -> " + (a[2].equals("y") ? "들어감" : "거절") + ", 순서를 바꿔도 같음", () -> {
            boolean expect = a[2].equals("y");
            Timetable x = table(w.c(a[0])); eq(expect, x.add(w.c(a[1])), "앞->뒤"); eq(expect ? 2 : 1, x.size(), "과목 수");
            Timetable y = table(w.c(a[1])); eq(expect, y.add(w.c(a[0])), "뒤->앞"); eq(expect ? 2 : 1, y.size(), "과목 수"); });
        t("18학점 딱 맞게는 들어가고 1학점이라도 넘으면 거절", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5)); eq(15, tt.getTotalCredits(), "15");
            ok(tt.add(w.c(JP1)), "18학점"); eq(18, tt.getTotalCredits(), "18"); ok(!tt.add(w.c(ONE)), "19학점"); ok(!tt.add(w.c(ON1)), "20학점"); eq(6, tt.size(), "과목 수"); });
        t("17학점에서 2학점 과목은 거절, 1학점 과목은 들어감", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(FR1)); eq(17, tt.getTotalCredits(), "17");
            ok(!tt.add(w.c(LF1)), "19학점"); ok(tt.add(w.c(ONE)), "18학점"); });
        t("학점 0짜리 과목은 18학점에서도 들어간다", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(JP1));
            ok(tt.add(course("90000001", "영학점", CourseType.GENERAL, 0, "토9")), "0학점"); eq(18, tt.getTotalCredits(), "학점"); });
        t("복원(restore)은 학점·중복 수강을 보지 않는다", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(JP1));
            ok(tt.restore(w.c(BIG)), "21학점"); ok(tt.restore(w.c(FR1)), "프랑스어 001"); ok(tt.restore(w.c(FR2)), "프랑스어 002"); eq(25, tt.getTotalCredits(), "학점"); });
        t("복원(restore)도 시간 겹침과 같은 분반은 거절", () -> { Timetable tt = table(w.c(M1)); ok(!tt.restore(w.c(LF2)), "겹침"); ok(!tt.restore(w.c(M1)), "같은 분반"); eq(1, tt.size(), "과목 수"); });
        t("18학점을 넘긴 시간표에는 아무것도 못 넣지만 빼기는 된다", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(JP1), w.c(BIG)); eq(21, tt.getTotalCredits(), "21");
            ok(!tt.add(w.c(ONE)), "추가"); ok(!tt.replace(Collections.singletonList(w.c(BIG)), w.c(MOV)), "3학점 빼고 2학점 넣어도 20");
            ok(tt.remove(w.c(BIG)), "빼기"); eq(18, tt.getTotalCredits(), "18"); });
        t("교체: 같은 학점끼리는 18학점에서도 된다", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(JP1));
            ok(tt.replace(Collections.singletonList(w.c(JP1)), w.c(BIG)), "교체"); eq(18, tt.getTotalCredits(), "학점"); ok(!tt.contains(JP1), "빠짐"); ok(tt.contains(BIG), "들어옴"); });
        t("교체: 겹치는 과목이 뺄 목록에 없으면 거절, 시간표 그대로", () -> { Timetable tt = table(w.c(M1), w.c(FR1));
            ok(!tt.replace(Collections.singletonList(w.c(FR1)), w.c(LF2)), "전공A가 남아 겹침"); eq(Arrays.asList(M1, FR1), codes(tt), "그대로"); });
        t("교체: 겹치지 않는 과목도 뺄 목록에 있으면 같이 빠진다", () -> { Timetable tt = table(w.c(M1), w.c(FR1), w.c(JP1));
            ok(tt.replace(Arrays.asList(w.c(M1), w.c(JP1)), w.c(LF2)), "교체"); eq(Arrays.asList(FR1, LF2), codes(tt), "결과"); });
        t("교체: 뺄 목록에 시간표에 없는 과목이 있어도 무시한다", () -> { Timetable tt = table(w.c(M1));
            ok(tt.replace(Arrays.asList(w.c(JP1), w.c(BIG)), w.c(FR1)), "교체"); eq(Arrays.asList(M1, FR1), codes(tt), "결과"); eq(5, tt.getTotalCredits(), "학점"); });
        t("교체: 같은 과목의 다른 분반은 그 분반을 뺄 때만 들어간다", () -> { Timetable tt = table(w.c(FR1), w.c(M1));
            ok(!tt.replace(Collections.singletonList(w.c(M1)), w.c(FR2)), "프랑스어 001이 남음"); ok(tt.replace(Collections.singletonList(w.c(FR1)), w.c(FR2)), "분반 교체"); eq(Arrays.asList(M1, FR2), codes(tt), "결과"); });
        t("교체: 자기 자신으로 교체해도 한 번만 남는다", () -> { Timetable tt = table(w.c(M1), w.c(FR1)); ok(tt.replace(Collections.singletonList(w.c(FR1)), w.c(FR1)), "교체"); eq(Arrays.asList(M1, FR1), codes(tt), "결과"); });
        t("교체: 뺄 목록이 비면 add와 같다", () -> { Timetable tt = table(w.c(M1)); ok(tt.replace(Collections.emptyList(), w.c(FR1)), "추가"); ok(!tt.replace(Collections.emptyList(), w.c(LF2)), "겹침"); eq(2, tt.size(), "과목 수"); });
        t("교체: 여러 과목과 겹치면 전부 빼야 한다 (넓은 화3-8)", () -> { Timetable tt = table(w.c(M2), w.c(ADJ), w.c(FR1), w.c(LF4));
            eq(Arrays.asList(M2, ADJ, FR1), codesOf(tt.findConflicts(w.c(WIDE), null)), "겹치는 과목");
            ok(!tt.replace(Arrays.asList(w.c(M2), w.c(ADJ)), w.c(WIDE)), "하나 남김"); ok(tt.replace(Arrays.asList(w.c(M2), w.c(ADJ), w.c(FR1)), w.c(WIDE)), "전부 뺌"); eq(Arrays.asList(LF4, WIDE), codes(tt), "결과"); });
        t("교체로 학점이 넘으면 거절 (2학점 빼고 3학점 넣기, 17학점)", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(LF4)); eq(17, tt.getTotalCredits(), "17");
            ok(tt.replace(Collections.singletonList(w.c(LF4)), w.c(JP1)), "18학점은 됨"); ok(!tt.replace(Collections.emptyList(), w.c(ONE)), "19학점"); });
        t("빼기: 있는 과목은 true, 없는 과목·두 번째는 false", () -> { Timetable tt = table(w.c(M1), w.c(FR1)); ok(tt.remove(w.c(FR1)), "빼기"); ok(!tt.remove(w.c(FR1)), "두 번째"); ok(!tt.remove(w.c(JP1)), "없는 과목"); eq(1, tt.size(), "과목 수"); });
        t("빼고 나면 같은 자리에 다시 넣을 수 있다", () -> { Timetable tt = table(w.c(M1)); ok(!tt.add(w.c(LF2)), "겹침"); tt.remove(w.c(M1)); ok(tt.add(w.c(LF2)), "빈 자리"); ok(!tt.add(w.c(M1)), "이제 반대로 겹침"); });
        t("겹침 목록은 시간표에 넣은 순서", () -> { Timetable tt = table(w.c(FR2), w.c(M2), w.c(FR1), w.c(ADJ)); eq(Arrays.asList(FR2, M2, FR1, ADJ), codesOf(tt.findConflicts(w.c(WIDE), null)), "순서");
            eq(FR2, tt.findConflict(w.c(WIDE), null).get().getCode(), "첫 번째"); });
        t("겹침 검사에서 내놓는 과목은 빠진다", () -> { Timetable tt = table(w.c(FR1), w.c(M2)); eq(Collections.singletonList(FR1), codesOf(tt.findConflicts(w.c(LF3), null)), "그냥");
            eq(Collections.emptyList(), tt.findConflicts(w.c(LF3), w.c(FR1)), "프랑스어를 내놓으면"); eq(Collections.singletonList(FR1), codesOf(tt.findConflicts(w.c(LF3), w.c(M2))), "다른 과목을 내놓으면"); });
        t("내놓는 과목이 시간표에 없어도 겹침 검사는 그대로", () -> { Timetable tt = table(w.c(M1)); eq(Collections.singletonList(M1), codesOf(tt.findConflicts(w.c(LF2), w.c(JP1))), "겹침"); });
        t("겹침 검사는 시간표를 바꾸지 않는다", () -> { Timetable tt = table(w.c(M1), w.c(FR1)); tt.findConflicts(w.c(LF2), w.c(FR1)); tt.findSameSubject(w.c(FR2), Collections.emptyList()); tt.exceedsLimit(w.c(BIG), Collections.emptyList()); eq(Arrays.asList(M1, FR1), codes(tt), "그대로"); });
        t("같은 과목 찾기: 뺄 목록에 든 분반은 건너뛴다", () -> { Timetable tt = table(w.c(FR1), w.c(M1)); eq(FR1, tt.findSameSubject(w.c(FR2), Collections.emptyList()).get().getCode(), "찾음");
            ok(tt.findSameSubject(w.c(FR2), Collections.singletonList(w.c(FR1))).isEmpty(), "빼면 없음"); ok(tt.findSameSubject(w.c(LF1), Collections.emptyList()).isEmpty(), "다른 과목"); eq(FR1, tt.findSameSubject(w.c(FR1), Collections.emptyList()).get().getCode(), "같은 분반도 같은 과목"); });
        t("이름만 같고 학수번호 앞부분이 다르면 다른 과목 (교양·전공 데이터베이스)", () -> { Timetable tt = table(w.c(DBM)); ok(tt.findSameSubject(w.c(DBG), Collections.emptyList()).isEmpty(), "다른 과목"); ok(tt.add(w.c(DBG)), "둘 다 수강"); });
        t("넣은 뒤 학점 계산: 뺄 목록에 같은 과목이 두 번 있어도 한 번만 뺀다", () -> { Timetable tt = table(w.c(M1), w.c(JP1)); eq(6, tt.getTotalCredits(), "현재");
            eq(5, tt.creditsAfter(w.c(FR1), Collections.singletonList(w.c(JP1))), "한 번"); eq(5, tt.creditsAfter(w.c(FR1), Arrays.asList(w.c(JP1), w.c(JP1))), "두 번 적어도"); });
        t("넣은 뒤 학점 계산: 시간표에 없는 과목은 빼지 않는다", () -> { Timetable tt = table(w.c(M1)); eq(5, tt.creditsAfter(w.c(FR1), Arrays.asList(w.c(JP1), w.c(BIG))), "그대로 더함"); });
        t("뺄 목록에 같은 과목을 여러 번 적어 학점 제한을 피할 수 없다", () -> { Timetable tt = table(w.c(M1), w.c(M2), w.c(M3), w.c(M4), w.c(M5), w.c(FR1), w.c(ONE)); eq(18, tt.getTotalCredits(), "18");
            List<Course> thrice = Arrays.asList(w.c(FR1), w.c(FR1), w.c(FR1)); eq(19, tt.creditsAfter(w.c(BIG), thrice), "18-2+3"); ok(tt.exceedsLimit(w.c(BIG), thrice), "학점 초과"); ok(!tt.replace(thrice, w.c(BIG)), "교체가 됨"); eq(18, tt.getTotalCredits(), "그대로"); });
        t("과목 목록은 밖에서 못 바꾼다", () -> { Timetable tt = table(w.c(M1));
            try { tt.getCourses().add(w.c(FR1)); } catch (UnsupportedOperationException e) { try { tt.getCourses().clear(); } catch (UnsupportedOperationException e2) { return; } }
            throw new AssertionError("목록이 바뀜"); });
        t("과목 목록은 넣은 순서, 교체로 들어온 과목은 맨 뒤", () -> { Timetable tt = table(w.c(M3), w.c(FR1), w.c(M1)); tt.replace(Collections.singletonList(w.c(FR1)), w.c(LF3)); eq(Arrays.asList(M3, M1, LF3), codes(tt), "순서"); });
        t("코드로 찾기: 정확히 같은 코드만", () -> { Timetable tt = table(w.c(M1)); ok(tt.contains(M1), "있음"); ok(!tt.contains("1000100"), "앞부분"); ok(!tt.contains(M1 + " "), "뒤 공백"); ok(!tt.contains(""), "빈 문자열"); eq(M1, tt.findByCode(M1).get().getCode(), "찾기"); });
        t("최대 학점 상수는 18", () -> eq(18, Timetable.MAX_CREDITS, "MAX_CREDITS"));
        for (int seed = 1; seed <= 30; seed++) { final int fseed = seed;
            t("무작위 추가·교체·빼기 300번 뒤에도 겹침·중복 수강·학점 초과 없음 (seed " + seed + ")", () -> { Random rnd = new Random(fseed); Timetable tt = new Timetable();
                for (int step = 0; step < 300; step++) { Course c = w.courses.get(rnd.nextInt(w.courses.size()));
                    switch (rnd.nextInt(4)) {
                        case 0 -> tt.add(c);
                        case 1 -> tt.replace(tt.findConflicts(c, null), c);
                        case 2 -> { List<Course> some = new ArrayList<>(); for (Course x : tt.getCourses()) if (rnd.nextBoolean()) some.add(x); tt.replace(some, c); }
                        default -> { if (tt.size() > 0) tt.remove(tt.getCourses().get(rnd.nextInt(tt.size()))); }
                    }
                    List<Course> cs = tt.getCourses(); int credits = 0;
                    for (int i = 0; i < cs.size(); i++) { credits += cs.get(i).getCredits();
                        for (int j = i + 1; j < cs.size(); j++) { ok(!clash(cs.get(i), cs.get(j)), "step " + step + " 겹침 " + cs.get(i) + " / " + cs.get(j)); ok(!subject(cs.get(i)).equals(subject(cs.get(j))), "step " + step + " 중복 수강 " + cs.get(i) + " / " + cs.get(j)); } }
                    ok(credits <= 18, "step " + step + " " + credits + "학점"); eq(credits, tt.getTotalCredits(), "학점 합계"); } }); }
        done();
    }
    static List<String> codesOf(List<Course> list) {
        List<String> out = new ArrayList<>();
        for (Course c : list) out.add(c.getCode());
        return out;
    }

    static boolean wellFormed(String s) { return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(s); }

    static void textUtil() {
        sec("6. 화면 폭 계산 (TextUtil)");
        Object[][] widths = {
            {"", 0}, {"a", 1}, {"abc", 3}, {"한", 2}, {"한글", 4}, {"a한b", 4}, {"求", 2}, {"求교환", 6}, {" ", 1}, {"  ", 2},
            {"프랑스어1", 9}, {"생명과 나눔", 11}, {"·", 1}, {"Ⅱ", 1}, {"ㅋ", 2}, {"ㅋㅋㅋ", 6}, {"ㅏ", 2}, {"あ", 2}, {"カ", 2}, {"Ａ", 2},
            {"１", 2}, {"，", 2}, {"。", 2}, {"　", 2}, {"😀", 2}, {"가😀", 4}, {"é", 1}, {"-", 1}, {"ｱ", 1}, {"￦", 2},
            {"㈜", 2}, {"Java프로그래밍", 14}, {"P-실무프로젝트 (4주/융합,메이커스)", 34}, {"12345678", 8}, {"[거절]", 6}, {"(화 3-4)", 8}, {"가나다라마바사", 14}, {"ㄱㄴㄷ", 6}, {"A가B나C", 7}, {"一鿿", 4},
        };
        for (Object[] c : widths) t("폭: '" + show((String) c[0]) + "' = " + c[1] + "칸", () -> eq(c[1], TextUtil.displayWidth((String) c[0]), "폭"));
        String[] samples = {"", "a", "abc", "한", "한글", "프랑스어1", "생명과 나눔", "대학생을 위한 학술적글쓰기", "Java프로그래밍", "abcdefghijklmnopqrstuvwxyz", "가나다라마바사아자차카타파하",
            "ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ", "a😀b😀c😀d😀e", "😀😀😀😀😀😀😀😀", "P-실무프로젝트 (4주/융합,메이커스)", "ＡＢＣＤＥＦＧＨＩ", "a가a가a가a가a가a가", "가a가a가a가a가a가a", " ", "화학및실험1 (공학인증)(Flipped Learning)"};
        for (String s : samples) t("padRight '" + show(s) + "': 폭 0~30 모두에서 max(폭, 원래 폭)이 되고 원문이 앞에 그대로", () -> { for (int wd = 0; wd <= 30; wd++) { String r = TextUtil.padRight(s, wd);
            eq(Math.max(wd, TextUtil.displayWidth(s)), TextUtil.displayWidth(r), "폭 " + wd); ok(r.startsWith(s), "원문"); ok(r.substring(s.length()).trim().isEmpty(), "채움은 공백만"); } });
        for (String s : samples) t("cut '" + show(s) + "': 폭 2~40 모두에서 넘지 않고, 들어가면 원문 그대로, 넘으면 ..로 끝나고, 글자가 깨지지 않음", () -> { for (int wd = 2; wd <= 40; wd++) { String r = TextUtil.cut(s, wd);
            ok(TextUtil.displayWidth(r) <= wd, "폭 " + wd + " 에서 " + TextUtil.displayWidth(r) + "칸: " + show(r)); ok(wellFormed(r), "폭 " + wd + " 에서 글자가 깨짐");
            if (TextUtil.displayWidth(s) <= wd) eq(s, r, "폭 " + wd + " 원문 그대로"); else { ok(r.endsWith(".."), "폭 " + wd + " .. 없음"); ok(s.startsWith(r.substring(0, r.length() - 2)), "폭 " + wd + " 앞부분이 아님"); } } });
        for (String s : samples) t("center '" + show(s) + "': 폭 0~30 모두에서 max(폭, 원래 폭), 왼쪽 여백 <= 오른쪽 여백 <= 왼쪽 + 1", () -> { for (int wd = 0; wd <= 30; wd++) { String r = TextUtil.center(s, wd);
            eq(Math.max(wd, TextUtil.displayWidth(s)), TextUtil.displayWidth(r), "폭 " + wd); int total = Math.max(0, wd - TextUtil.displayWidth(s)); int left = total / 2;
            eq(" ".repeat(left) + s + " ".repeat(total - left), r, "폭 " + wd); } });
        String[][] cuts = {{"abc", "3", "abc"}, {"abcd", "3", "a.."}, {"abcd", "4", "abcd"}, {"한글날", "4", "한.."}, {"한글날", "5", "한.."}, {"한글날", "6", "한글날"}, {"a한글날", "6", "a한.."},
            {"대학생을 위한 학술적글쓰기", "14", "대학생을 위.."}, {"일이삼사오육칠팔", "14", "일이삼사오육.."}, {"abcdefghijklmnop", "14", "abcdefghijkl.."}, {"a😀b😀c", "4", "a.."}, {"a😀b😀c", "5", "a😀.."},
            {"ㅋㅋㅋㅋ", "6", "ㅋㅋ.."}, {"", "5", ""}, {"..", "2", ".."}, {"...", "2", ".."}};
        for (String[] c : cuts) t("cut('" + show(c[0]) + "', " + c[1] + ") = '" + show(c[2]) + "'", () -> eq(c[2], TextUtil.cut(c[0], Integer.parseInt(c[1])), "결과"));
        String[][] pads = {{"", "3", "   "}, {"a", "0", "a"}, {"a", "1", "a"}, {"a", "3", "a  "}, {"한글", "4", "한글"}, {"한글", "5", "한글 "}, {"한글", "8", "한글    "}, {"ㅋ", "3", "ㅋ "}, {"😀", "4", "😀  "}, {"a", "-5", "a"}};
        for (String[] c : pads) t("padRight('" + show(c[0]) + "', " + c[1] + ") = '" + show(c[2]) + "'", () -> eq(c[2], TextUtil.padRight(c[0], Integer.parseInt(c[1])), "결과"));
        String[][] centers = {{"", "4", "    "}, {"a", "1", "a"}, {"a", "2", "a "}, {"a", "3", " a "}, {"월", "14", "      월      "}, {"가나", "3", "가나"}, {"ab", "0", "ab"}, {"ab", "-3", "ab"}, {"ab", "5", " ab  "}, {"토", "5", " 토  "}};
        for (String[] c : centers) t("center('" + show(c[0]) + "', " + c[1] + ") = '" + show(c[2]) + "'", () -> eq(c[2], TextUtil.center(c[0], Integer.parseInt(c[1])), "결과"));
        done();
    }

    static void gridShape(String out, int days) {
        String[] lines = out.split("\n");
        int expect = 5 + 15 * days;
        for (int i = 0; i < lines.length - 1; i++) eq(expect, TextUtil.displayWidth(lines[i]), (i + 1) + "번째 줄 폭 [" + lines[i] + "]");
        hasNot(out, "\u001B");
        ok(lines[lines.length - 1].startsWith(" 전공: "), "요약 줄: " + lines[lines.length - 1]);
    }
    static int rows(String out) { int n = 0; for (String l : out.split("\n")) if (l.matches(" \\d+ *\\|.*")) n++; return n; }

    static void timetableGrid() {
        sec("7. 시간표 격자 (TimetableGrid)");
        t("빈 시간표: 5열 9행, 요약은 없음/없음", () -> { String out = grid(new Timetable()); gridShape(out, 5); eq(9, rows(out), "행"); has(out, " 전공: 없음   교양: 없음"); });
        for (int p = 1; p <= 14; p++) { final int fp = p; t("한 교시짜리 수업이 " + p + "교시에 있을 때: 행 수 " + Math.max(9, p) + ", 그 줄에 과목명", () -> {
            String out = grid(table(course("20001001", "가나다", CourseType.GENERAL, 2, "수" + fp))); gridShape(out, 5); eq(Math.max(9, fp), rows(out), "행");
            String row = null; for (String l : out.split("\n")) if (l.startsWith(String.format(" %-3d|", fp))) row = l; ok(row != null && row.contains("|가나다        |"), "과목명 줄: " + row); hasNot(out, "20001001      |"); }); }
        for (Day d : Day.values()) t(d.getLabel() + "요일 수업 하나: " + (d.ordinal() >= 5 ? "토·일 열이 생김 (7열)" : "평일 5열"), () -> {
            String out = grid(table(course("20001001", "가나다", CourseType.GENERAL, 2, d.getLabel() + "2-3"))); int days = d.ordinal() >= 5 ? 7 : 5; gridShape(out, days);
            String head = out.split("\n")[0]; eq(days >= 7, head.contains("토") && head.contains("일"), "주말 열"); has(out, "|가나다        |"); has(out, "|20001001      |"); });
        t("15교시 이후만 있는 수업: 죽지 않고 9행, 요약 줄에는 나온다", () -> { String out = grid(table(course("20001001", "심야수업", CourseType.GENERAL, 2, "월15-16"))); gridShape(out, 5); eq(9, rows(out), "행"); has(out, "교양: 심야수업"); });
        t("13~16교시에 걸친 수업: 14교시까지만 그린다", () -> { String out = grid(table(course("20001001", "긴수업", CourseType.GENERAL, 2, "월13-16"))); gridShape(out, 5); eq(14, rows(out), "행"); has(out, "|긴수업        |"); });
        t("99교시 수업도 죽지 않는다", () -> { String out = grid(table(course("20001001", "먼수업", CourseType.GENERAL, 2, "금1-99"))); gridShape(out, 5); eq(14, rows(out), "행"); });
        t("같은 과목의 이어진 두 구간(월1-2;월3-4)은 한 덩어리", () -> { String out = grid(table(course("20001001", "이어짐", CourseType.GENERAL, 2, "월1-2", "월3-4"))); gridShape(out, 5);
            eq(1, out.split("이어짐", -1).length - 1 - 1, "과목명은 격자에 한 번 (요약 줄 제외)"); eq(1, out.split("20001001", -1).length - 1, "학수번호도 한 번"); });
        t("같은 과목이 떨어진 두 구간(월1-2;월5-6)이면 덩어리마다 과목명·학수번호", () -> { String out = grid(table(course("20001001", "떨어짐", CourseType.GENERAL, 2, "월1-2", "월5-6"))); gridShape(out, 5);
            eq(2, out.split("\\|떨어짐", -1).length - 1, "과목명 두 번"); eq(2, out.split("20001001", -1).length - 1, "학수번호 두 번"); });
        t("서로 다른 과목이 맞닿으면 사이에 가로선", () -> { String out = grid(table(course("10001001", "위", CourseType.MAJOR, 3, "월1-2"), course("10002001", "아래", CourseType.MAJOR, 3, "월3-4"))); gridShape(out, 5);
            String[] lines = out.split("\n"); int two = -1; for (int i = 0; i < lines.length; i++) if (lines[i].startsWith(" 2  |")) two = i; ok(lines[two + 1].startsWith("    +--------------+"), "2교시 아래: " + lines[two + 1]); });
        t("과목명이 딱 14칸이면 그대로, 15칸이면 자른다", () -> { String a = grid(table(course("20001001", "가나다라마바사", CourseType.GENERAL, 2, "월1-2"))); has(a, "|가나다라마바사|"); gridShape(a, 5);
            String b = grid(table(course("20001001", "가나다라마바사a", CourseType.GENERAL, 2, "월1-2"))); has(b, "|가나다라마바..|"); gridShape(b, 5); });
        String[] oddNames = {"P-실무프로젝트 (4주/융합,메이커스)", "화학및실험1 (공학인증)(Flipped Learning)", "ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ", "😀😀😀😀😀😀😀😀😀",
            "ＡＢＣＤＥＦＧＨＩ", "", " ", "a", "abcdefghijklmn", "abcdefghijklmno", "가a나b다c라d마e바f", "求求求求求求求求", "이름|세로선", "이름+더하기-빼기"};
        for (String name : oddNames) t("이상한 과목명 '" + show(name) + "' 에도 모든 줄의 폭이 같다", () -> gridShape(grid(table(course("20001001", name, CourseType.GENERAL, 2, "화3-4", "목1"))), 5));
        String[] oddCodes = {"1", "12345678901234", "123456789012345", "12345678901234567890", "학수번호가아주긴과목", "A-B_C.D"};
        for (String code : oddCodes) t("이상한 학수번호 '" + code + "' 에도 모든 줄의 폭이 같다", () -> gridShape(grid(table(new Course(code, "과목", "001", CourseType.GENERAL, Arrays.asList(TimeSlot.parse("화3-5")), "", "", 2))), 5));
        t("요약 줄: 교직·군사는 있을 때만", () -> { String a = grid(table(course("30001001", "교직론", CourseType.TEACHING, 2, "월7-8"))); has(a, " 전공: 없음   교양: 없음   교직: 교직론"); hasNot(a, "군사:");
            String b = grid(table(course("40001001", "군사학", CourseType.MILITARY, 2, "토1-2"), course("30001001", "교직론", CourseType.TEACHING, 2, "월7-8"))); has(b, "교직: 교직론   군사: 군사학"); });
        t("시간 없는 과목만 있으면 격자는 비고 요약 줄에만 나온다", () -> { String out = grid(table(course("20006001", "온라인교양", CourseType.GENERAL, 2))); gridShape(out, 5); eq(9, rows(out), "행"); has(out, "교양: 온라인교양"); hasNot(out, "|온라인교양"); });
        t("월~금을 꽉 채운 시간표(1~9교시)도 폭이 맞는다", () -> { List<Course> cs = new ArrayList<>(); int n = 0;
            for (Day d : new Day[]{Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI}) for (int p = 1; p <= 9; p += 3) cs.add(course(String.format("2%04d001", ++n), "과목" + n, CourseType.GENERAL, 1, d.getLabel() + p + "-" + (p + 2)));
            String out = grid(table(cs.toArray(new Course[0]))); gridShape(out, 5); eq(9, rows(out), "행"); for (int i = 1; i <= 15; i++) has(out, "|과목" + i + " "); });
        t("교시 번호는 1부터 차례로", () -> { String out = grid(table(course("20001001", "가", CourseType.GENERAL, 2, "월14"))); for (int p = 1; p <= 14; p++) has(out, "\n" + String.format(" %-3d|", p)); });
        t("같은 시간표를 두 번 그리면 같은 출력", () -> { Timetable tt = table(course("20001001", "가", CourseType.GENERAL, 2, "월1-2"), course("10001001", "나", CourseType.MAJOR, 3, "화3")); eq(grid(tt), grid(tt), "출력"); eq(2, tt.size(), "그리기는 시간표를 바꾸지 않음"); });
        done();
    }

    static void catalogAndRegistry() throws IOException {
        sec("8. 과목 목록·학생 목록 (CourseCatalog, StudentRegistry, Repository)");
        W w = new W();
        Object[][] searches = {
            {"프랑스어", 2}, {"프랑", 2}, {"스어", 2}, {"생명", 4}, {"교양", 8}, {"2000", 17}, {"20001", 2}, {"20001001", 1}, {"0001001", 0}, {"1001", 1},
            {"없는과목", 0}, {"java", 1}, {"JAVA", 1}, {"Java", 1}, {"programming", 1}, {"jAvA pRoG", 1}, {"Java  Programming", 0}, {",", 1}, {"쉼표,과목", 1}, {".*", 0},
            {"(", 0}, {"[가-힣]", 0}, {"\\", 0}, {"%", 0}, {" ", 1}, {"데이터베이스", 2}, {"전공", 5}, {"1", 6}, {"3", 1}, {"4", 1},
            {"9", 0}, {"온라인", 3}, {"프랑스어 ", 0}, {"프랑스어​", 0}, {"001", 0},
        };
        for (Object[] s : searches) t("검색 '" + show((String) s[0]) + "' -> " + s[1] + "개", () -> eq(s[1], w.catalog.search((String) s[0]).size(), "결과 수"));
        t("검색 결과는 과목 목록의 순서", () -> eq(Arrays.asList(LF1, LF2, LF3, LF4), codesOf(w.catalog.search("생명")), "순서"));
        t("빈 검색어는 전체 (화면에서는 빈 줄이면 메뉴로 돌아가므로 쓰이지 않음)", () -> eq(w.courses.size(), w.catalog.search("").size(), "전체"));
        t("검색은 과목 목록을 바꾸지 않는다", () -> { int before = w.catalog.size(); w.catalog.search("생명").clear(); w.catalog.all().clear(); w.catalog.findByName("생명").clear(); eq(before, w.catalog.size(), "크기"); });
        String[][] resolves = {{"20001001", "프랑스어"}, {"20001002", "프랑스어"}, {"프랑스어", "프랑스어"}, {"10001001", "전공A"}, {"전공A", "전공A"}, {"20015001", "쉼표,과목"}, {"쉼표,과목", "쉼표,과목"}, {"Java Programming", "Java Programming"},
            {"20017001", "Java Programming"}, {"데이터베이스", "데이터베이스"}, {"10010001", "데이터베이스"}};
        for (String[] r : resolves) t("희망 과목 입력 '" + r[0] + "' -> '" + r[1] + "'", () -> eq(Optional.of(r[1]), w.catalog.resolveName(r[0]), "변환"));
        String[] unresolved = {"", " ", "프랑스", "프랑스어 ", " 프랑스어", "java programming", "2000100", "200010011", "99999999", "프랑스어1", "20001001 ", "생 명", "null", "프랑스어,생명", "프랑스어;생명"};
        for (String u : unresolved) t("희망 과목 입력 '" + show(u) + "' 은 코드도 과목명도 아님 (입력 그대로 비교)", () -> { ok(w.catalog.resolveName(u).isEmpty(), "변환됨: " + w.catalog.resolveName(u)); ok(!w.catalog.existsName(u), "existsName"); });
        t("같은 코드를 다시 넣으면 덮어쓰고 개수는 그대로", () -> { CourseCatalog c = new CourseCatalog(); c.add(course("20001001", "옛이름", CourseType.GENERAL, 2)); c.add(course("20001001", "새이름", CourseType.GENERAL, 2)); eq(1, c.size(), "크기"); eq("새이름", c.findByCode("20001001").get().getName(), "이름"); ok(!c.existsName("옛이름"), "옛이름"); });
        t("빈 과목 목록: 검색·찾기·변환 모두 비어 있음", () -> { CourseCatalog c = new CourseCatalog(); eq(0, c.size(), "크기"); eq(0, c.search("가").size(), "검색"); eq(0, c.search("").size(), "빈 검색"); ok(c.findByCode("1").isEmpty(), "코드"); ok(c.resolveName("가").isEmpty(), "변환"); ok(!c.existsName(""), "이름"); });
        t("Repository: 없는 키·null 키는 empty", () -> { Repository<String, String> r = new Repository<>(); ok(r.find("x").isEmpty(), "없는 키"); ok(r.find(null).isEmpty(), "null 키"); ok(!r.exists(null), "exists(null)"); });
        t("Repository: 입력 순서 유지, 덮어써도 자리 유지", () -> { Repository<String, Integer> r = new Repository<>(); r.put("b", 1); r.put("a", 2); r.put("c", 3); r.put("b", 9); eq(Arrays.asList(9, 2, 3), r.all(), "순서"); eq(3, r.size(), "크기"); });
        t("Repository: all()을 고쳐도 원본은 그대로", () -> { Repository<String, Integer> r = new Repository<>(); r.put("a", 1); List<Integer> all = r.all(); all.add(5); all.clear(); eq(1, r.size(), "크기"); eq(Collections.singletonList(1), r.all(), "내용"); });
        t("새 학생 등록: 빈 시간표, 편집 중", () -> { StudentRegistry reg = new StudentRegistry(); Student s = reg.register("1", "가"); eq(0, s.getTimetable().size(), "과목"); ok(!s.isTimetableConfirmed(), "편집 중"); eq("가", s.getName(), "이름"); eq("1", s.getId(), "학번"); });
        t("이미 있는 학번으로 다시 등록하면 거절하고 기존 학생(시간표)을 지킨다", () -> { StudentRegistry reg = new StudentRegistry(); Student s = reg.register("1", "가"); s.getTimetable().add(w.c(M1));
            throwsIAE(() -> reg.register("1", "나"), "중복 등록"); eq("가", reg.find("1").get().getName(), "이름"); eq(1, reg.find("1").get().getTimetable().size(), "시간표"); eq(1, reg.size(), "학생 수"); });
        t("시간표 확정: 한 번만 true, 없는 학번·null은 false", () -> { StudentRegistry reg = new StudentRegistry(); reg.register("1", "가"); ok(reg.confirmTimetable("1"), "첫 번째"); ok(!reg.confirmTimetable("1"), "두 번째"); ok(!reg.confirmTimetable("2"), "없는 학번"); ok(!reg.confirmTimetable(null), "null"); ok(!reg.confirmTimetable(""), "빈 문자열"); });
        t("학번은 문자열 그대로 비교한다 (앞의 0, 공백 구분)", () -> { StudentRegistry reg = new StudentRegistry(); reg.register("0123", "가"); ok(reg.find("123").isEmpty(), "123"); ok(reg.find("0123 ").isEmpty(), "뒤 공백"); ok(reg.find("0123").isPresent(), "0123"); });
        t("학생 목록은 등록 순서, all()을 고쳐도 그대로", () -> { StudentRegistry reg = new StudentRegistry(); reg.register("3", "다"); reg.register("1", "가"); reg.all().clear(); eq(2, reg.size(), "크기"); eq("3", reg.all().get(0).getId(), "첫 학생"); });
        done();
    }

    static void requestRules() throws IOException {
        sec("9. 교환 요청 등록 규칙 (RequestBoard)");
        String[][] gives = {{M1, "전공 과목은 교환 요청을 등록할 수 없습니다"}, {TCH, "교직 과목은 교환 요청을 등록할 수 없습니다"}, {MIL, "군사 과목은 교환 요청을 등록할 수 없습니다"}, {DBM, "전공 과목은 교환 요청을 등록할 수 없습니다"}, {LF1, "내 시간표에 없는 과목입니다"}, {M2, "내 시간표에 없는 과목입니다"}};
        for (String[] g : gives) t("내놓을 과목 " + g[0] + " 거절: " + g[1], () -> { W w = new W(); Student s = w.stu("1", true, M1, FR1, TCH, MIL, DBM);
            eq(g[1], rejected(() -> w.board.register(s, w.c(g[0]), "생명")), "사유"); eq(g[1], rejected(() -> w.board.validateGive(s, w.c(g[0]))), "validateGive"); eq(0, w.board.size(), "요청 수"); });
        for (String code : new String[]{M1, FR1, LF1}) t("시간표 확정 전에는 어떤 과목(" + code + ")도 요청 불가", () -> { W w = new W(); Student s = w.stu("1", false, M1, FR1);
            has(rejected(() -> w.board.register(s, w.c(code), "생명")), "확정"); eq(0, w.board.size(), "요청 수"); });
        String[][] wants = {
            {"생명", null}, {"일본어", null}, {"온라인교양", null}, {"쉼표,과목", null}, {"Java Programming", null}, {"마감교양", null}, {"꽉찬교양", null}, {"데이터베이스", null}, {"주말교양", null}, {"한학점", null},
            {"프랑스어", "내놓을 과목과 희망 과목이 같습니다"}, {"전공A", "교양 과목만 교환할 수 있습니다"}, {"전공B", "교양 과목만 교환할 수 있습니다"}, {"교직론", "교양 과목만 교환할 수 있습니다"}, {"군사학", "교양 과목만 교환할 수 있습니다"},
            {"없는과목", "과목 목록에 없습니다"}, {"", "과목 목록에 없습니다"}, {" 생명", "과목 목록에 없습니다"}, {"생명 ", "과목 목록에 없습니다"}, {"20002001", "과목 목록에 없습니다"}, {"java programming", "과목 목록에 없습니다"},
            {"생명;일본어", "과목 목록에 없습니다"}, {"생명,일본어", "과목 목록에 없습니다"}, {"프랑스어 ", "과목 목록에 없습니다"}, {"생 명", "과목 목록에 없습니다"},
        };
        for (String[] c : wants) t("희망 과목 '" + show(c[0]) + "' -> " + (c[1] == null ? "등록됨" : "거절: " + c[1]), () -> { W w = new W(); Student s = w.stu("1", true, M1, FR1);
            String reason = rejected(() -> w.board.register(s, w.c(FR1), c[0]));
            if (c[1] == null) { eq(null, reason, "거절됨"); eq(1, w.board.size(), "요청 수"); eq(c[0], w.board.all().get(0).getWantName(), "희망 과목"); }
            else { ok(reason != null, "등록됨"); has(reason, c[1]); eq(0, w.board.size(), "요청 수"); } });
        String[][] taking = {{LF2, "생명", "y"}, {LF4, "생명", "y"}, {DBM, "데이터베이스", "n"}, {DBG, "데이터베이스", "y"}, {ON1, "온라인교양", "y"}, {ON2, "온라인교양", "y"}, {ONX, "온라인교양", "n"}, {CN, "마감교양", "y"}, {JP2, "일본어", "y"}, {M1, "생명", "n"}};
        for (String[] c : taking) t("이미 " + c[0] + " 를 듣는 학생이 '" + c[1] + "' 희망 -> " + (c[2].equals("y") ? "거절 (이미 듣는 과목)" : "등록됨"), () -> { W w = new W(); Student s = w.stu("1", true, FR1, c[0]);
            String reason = rejected(() -> w.board.register(s, w.c(FR1), c[1])); if (c[2].equals("y")) eq("이미 듣고 있는 과목입니다", reason, "사유"); else eq(null, reason, "거절됨"); });
        for (Course c : new W().courses) { if (!c.isGeneral()) continue; String want = c.getName().equals("생명") ? "일본어" : "생명";
            t("교양 " + c.getCode() + " " + c.getName() + " 은 내놓을 수 있다 -> " + want, () -> { W w = new W(); Student s = w.stu("1", true, c.getCode());
                SwapRequest r = w.board.register(s, w.c(c.getCode()), want); eq(1, r.getId(), "번호"); eq(RequestStatus.WAITING, r.getStatus(), "상태"); eq(c.getCode(), r.getGiveCourse().getCode(), "내놓는 과목"); ok(w.board.isGiving("1", c.getCode()), "isGiving"); eq(1, s.getTimetable().size(), "시간표는 그대로"); }); }
        t("같은 내용의 대기 요청은 거절하고 번호를 알려 준다", () -> { W w = new W(); Student s = w.stu("1", true, FR1, JP1); w.board.register(s, w.c(JP1), "생명"); w.board.register(s, w.c(FR1), "생명");
            eq("같은 내용의 대기 요청이 이미 있습니다 (#2)", rejected(() -> w.board.register(s, w.c(FR1), "생명")), "사유"); eq(2, w.board.size(), "요청 수"); });
        t("같은 과목을 내놓고 희망만 다르면 등록된다", () -> { W w = new W(); Student s = w.stu("1", true, FR1); w.board.register(s, w.c(FR1), "생명"); w.board.register(s, w.c(FR1), "일본어"); w.board.register(s, w.c(FR1), "영화"); eq(3, w.board.waitingOf("1").size(), "대기"); });
        t("희망이 같고 내놓는 과목만 달라도 등록된다", () -> { W w = new W(); Student s = w.stu("1", true, FR1, JP1); w.board.register(s, w.c(FR1), "생명"); w.board.register(s, w.c(JP1), "생명"); eq(2, w.board.size(), "요청 수"); });
        t("다른 학생의 같은 내용 요청은 중복이 아니다", () -> { W w = new W(); Student a = w.stu("1", true, FR1), b = w.stu("2", true, FR1); w.board.register(a, w.c(FR1), "생명"); w.board.register(b, w.c(FR1), "생명"); eq(2, w.board.size(), "요청 수"); });
        for (RequestStatus st : new RequestStatus[]{RequestStatus.CANCELLED, RequestStatus.MATCHED}) t("같은 내용이라도 " + st.getLabel() + " 상태면 다시 등록할 수 있다", () -> { W w = new W(); Student s = w.stu("1", true, FR1); w.raw("1", FR1, "생명", st);
            eq(2, w.board.register(s, w.c(FR1), "생명").getId(), "번호"); });
        t("거절된 등록은 번호를 쓰지 않는다", () -> { W w = new W(); Student s = w.stu("1", true, M1, FR1); for (int i = 0; i < 5; i++) rejected(() -> w.board.register(s, w.c(M1), "생명")); eq(1, w.board.register(s, w.c(FR1), "생명").getId(), "번호"); });
        t("검사(validate)만으로는 아무것도 등록되지 않는다", () -> { W w = new W(); Student s = w.stu("1", true, FR1); w.board.validate(s, w.c(FR1), "생명"); w.board.validate(s, w.c(FR1), "생명"); eq(0, w.board.size(), "요청 수"); });
        t("번호는 복원한 가장 큰 번호 다음, 작은 번호를 나중에 넣어도 줄지 않는다", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            w.board.put(new SwapRequest(50, "9", w.c(LF1), "프랑스어", RequestStatus.CANCELLED)); w.board.put(new SwapRequest(3, "9", w.c(LF1), "일본어", RequestStatus.WAITING)); eq(51, w.board.register(s, w.c(FR1), "생명").getId(), "번호"); });
        t("요청 100건을 연달아 등록하면 번호가 1~100", () -> { W w = new W(); for (int i = 1; i <= 100; i++) { Student s = w.stu("s" + i, true, FR1); eq(i, w.board.register(s, w.c(FR1), "생명").getId(), "번호"); } eq(100, w.board.size(), "요청 수"); });
        for (int id : new int[]{0, -1, 99, Integer.MAX_VALUE, Integer.MIN_VALUE}) t("없는 요청 번호 " + id + ": 찾기 empty, 취소·확정 false", () -> { W w = new W(); w.stu("1", true, FR1); w.raw("1", FR1, "생명"); ok(w.board.find(id).isEmpty(), "find"); ok(!w.board.cancel(id), "cancel"); ok(!w.board.complete(id), "complete"); eq(RequestStatus.WAITING, w.board.all().get(0).getStatus(), "기존 요청"); });
        for (RequestStatus st : new RequestStatus[]{RequestStatus.CANCELLED, RequestStatus.MATCHED}) t(st.getLabel() + " 상태의 요청은 다시 취소·확정할 수 없다", () -> { W w = new W(); SwapRequest r = w.raw("1", FR1, "생명", st); ok(!w.board.cancel(r.getId()), "cancel"); ok(!w.board.complete(r.getId()), "complete"); eq(st, r.getStatus(), "상태"); ok(!w.board.isGiving("1", FR1), "isGiving"); eq(0, w.board.waitingOf("1").size(), "대기"); });
        t("waitingOf·waitingExcept: 모르는 학번·null 학번에도 죽지 않는다", () -> { W w = new W(); w.raw("1", FR1, "생명"); eq(0, w.board.waitingOf("없는학번").size(), "없는 학번"); eq(1, w.board.waitingExcept("없는학번").size(), "except"); eq(0, w.board.waitingOf(null).size(), "null"); ok(!w.board.isGiving(null, FR1), "isGiving(null)"); ok(!w.board.isGiving("1", null), "isGiving(코드 null)"); });
        t("all()을 고쳐도 요청 목록은 그대로", () -> { W w = new W(); w.raw("1", FR1, "생명"); w.board.all().clear(); w.board.waitingOf("1").clear(); eq(1, w.board.size(), "요청 수"); });
        t("거절 예외에는 대상과 사유가 나뉘어 들어 있다", () -> { W w = new W(); Student s = w.stu("1", true, M1);
            try { w.board.register(s, w.c(M1), "생명"); } catch (RequestRejectedException e) { eq("전공A", e.getTarget(), "대상"); eq("전공A: " + e.getReason(), e.getMessage(), "메시지"); return; } throw new AssertionError("등록됨"); });
        done();
    }

    static W randomWorld(int seed, int students) throws IOException {
        Random rnd = new Random(seed);
        W w = new W();
        String[] hot = {"프랑스어", "생명", "일본어", "온라인교양", "마감교양"};
        for (int i = 0; i < students; i++) {
            Student s = w.registry.register("7" + String.format("%02d", i), "무작위" + i);
            for (int k = 0; k < 12; k++) s.getTimetable().add(w.courses.get(rnd.nextInt(w.courses.size())));
            w.registry.confirmTimetable(s.getId());
            for (int k = 0; k < 6; k++) {
                if (s.getTimetable().size() == 0) break;
                Course give = s.getTimetable().getCourses().get(rnd.nextInt(s.getTimetable().size()));
                try { w.board.register(s, give, hot[rnd.nextInt(hot.length)]); } catch (RequestRejectedException e) { }
            }
        }
        return w;
    }

    static void matching() throws IOException {
        sec("10. 매칭 (MatchService)");
        t("다른 요청이 하나도 없으면 후보 없음", () -> { W w = new W(); w.stu("1", true, FR1); eq(0, w.match(w.raw("1", FR1, "생명")).size(), "후보"); });
        for (RequestStatus st : new RequestStatus[]{RequestStatus.CANCELLED, RequestStatus.MATCHED}) t(st.getLabel() + " 상태의 상대 요청은 후보가 아니다", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1); w.raw("2", LF1, "프랑스어", st); eq(0, w.match(w.raw("1", FR1, "생명")).size(), "후보"); });
        t("내 다른 요청은 조건이 맞아도 후보가 아니다", () -> { W w = new W(); w.stu("1", true, FR1, LF1); w.raw("1", LF1, "프랑스어"); eq(0, w.match(w.raw("1", FR1, "생명")).size(), "후보"); });
        String[][] cross = {{LF1, "프랑스어", "y"}, {LF1, "일본어", "n"}, {JP1, "프랑스어", "n"}, {LF2, "프랑스어", "y"}, {LF1, "프랑스어 ", "n"}, {LF1, "생명", "n"}, {JP1, "생명", "n"}, {ON1, "프랑스어", "n"}};
        for (String[] c : cross) t("교차 일치: 상대가 " + c[0] + " 를 내놓고 '" + show(c[1]) + "' 희망 -> " + (c[2].equals("y") ? "후보" : "후보 아님"), () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, c[0]); w.raw("2", c[0], c[1]);
            eq(c[2].equals("y") ? 1 : 0, w.match(w.raw("1", FR1, "생명")).size(), "후보 수"); });
        t("가능: 겹침 없음", () -> { W w = new W(); w.stu("1", true, M1, FR1); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); ok(r.isPossible(), r.getReason()); eq("겹침 없음", r.getReason(), "사유"); eq(LF1, r.getReceiveCourse().getCode(), "받을 과목"); ok(!r.isResolvable(), "뺄 과목 없음"); });
        t("가능: 받을 과목이 내놓는 과목 자리와 겹치면 '자리' 표시", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF3); SwapRequest o = w.raw("2", LF3, "프랑스어"); eq("겹침 없음 (프랑스어 자리)", w.resultFor(w.raw("1", FR1, "생명"), o).getReason(), "사유"); });
        t("가능: 상대 시간표를 모르면 '미확인'", () -> { W w = new W(); w.stu("1", true, FR1); SwapRequest o = w.raw("999", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); ok(r.isPossible(), r.getReason()); eq("겹침 없음 (상대 시간표 미확인)", r.getReason(), "사유"); });
        t("가능: 자리 + 미확인이 함께", () -> { W w = new W(); w.stu("1", true, FR1); SwapRequest o = w.raw("999", LF3, "프랑스어"); eq("겹침 없음 (프랑스어 자리, 상대 시간표 미확인)", w.resultFor(w.raw("1", FR1, "생명"), o).getReason(), "사유"); });
        t("가능: 시간 없는 과목끼리", () -> { W w = new W(); w.stu("1", true, ON1); w.stu("2", true, ONX); SwapRequest o = w.raw("2", ONX, "온라인교양"); ok(w.resultFor(w.raw("1", ON1, "온라인둘"), o).isPossible(), "가능"); });
        t("가능: 주말 과목", () -> { W w = new W(); w.stu("1", true, FR1, MIL); w.stu("2", true, SAT); SwapRequest o = w.raw("2", SAT, "프랑스어"); ok(w.resultFor(w.raw("1", FR1, "주말교양"), o).isPossible(), "가능"); });
        t("제외(내 쪽): 내놓는 과목이 내 시간표에 없음", () -> { W w = new W(); w.stu("1", true, M1); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); eq("내놓는 과목이 내 시간표에 없음", r.getReason(), "사유"); });
        t("제외(상대 쪽): 상대가 내놓는 과목이 상대 시간표에 없음", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, M1); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); eq("상대가 내놓는 과목이 상대 시간표에 없음", r.getReason(), "사유"); });
        t("제외(내 쪽): 내가 이미 듣는 과목 (다른 분반)", () -> { W w = new W(); w.stu("1", true, FR1, LF4); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); eq("내가 이미 듣는 과목: 생명 004", r.getReason(), "사유"); ok(!r.isResolvable(), "빼고 교환 불가"); });
        t("제외(상대 쪽): 상대가 이미 듣는 과목 (다른 분반)", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1, FR2); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); eq("상대가 이미 듣는 과목: 프랑스어 002", r.getReason(), "사유"); });
        t("제외(내 쪽): 내 시간표 겹침 - 빼면 가능 (resolvable)", () -> { W w = new W(); w.stu("1", true, M1, FR1); w.stu("2", true, LF2); SwapRequest o = w.raw("2", LF2, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o);
            eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); eq("내 시간표 겹침: 전공A", r.getReason(), "사유"); ok(r.isResolvable(), "뺄 수 있음"); eq(Collections.singletonList(M1), codesOf(r.getDroppable()), "뺄 과목"); ok(!r.isPossible(), "가능은 아님"); });
        t("제외(내 쪽): 겹치는 과목이 내 다른 요청의 내놓는 과목이면 빼고 교환 불가", () -> { W w = new W(); w.stu("1", true, JP1, FR1, ADJ); w.stu("2", true, WIDE);
            w.raw("1", ADJ, "영화"); SwapRequest o = w.raw("2", WIDE, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "넓은"), o); eq("내 시간표 겹침: 인접", r.getReason(), "사유"); ok(!r.isResolvable(), "뺄 수 없음"); });
        t("제외(내 쪽): 겹치는 과목을 빼도 학점이 넘으면 빼고 교환 불가", () -> { W w = new W(); w.stu("1", true, M1, M2, M3, M4, M5, FR1, ONE); w.stu("2", true, BIG); Timetable tt = w.get("1").getTimetable(); eq(18, tt.getTotalCredits(), "18");
            Course fat = new Course("90001001", "큰교양", "001", CourseType.GENERAL, Arrays.asList(TimeSlot.parse("월9")), "", "", 5); w.catalog.add(fat); w.get("2").getTimetable().restore(fat);
            SwapRequest o = w.raw("2", "90001001", "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "큰교양"), o); eq("내 시간표 겹침: 한학점", r.getReason(), "사유"); ok(!r.isResolvable(), "18-2-1+5=20학점이라 뺄 수 없음"); });
        t("제외(내 쪽): 겹침은 없지만 학점 초과", () -> { W w = new W(); w.stu("1", true, M1, M2, M3, M4, M5, FR1, ONE); w.stu("2", true, JP1); SwapRequest o = w.raw("2", JP1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "일본어"), o); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); eq("내 학점 초과: 교환 후 19학점 (최대 18)", r.getReason(), "사유"); });
        t("제외(상대 쪽): 상대 학점 초과", () -> { W w = new W(); w.stu("1", true, JP1); w.stu("2", true, M1, M2, M3, M4, M5, FR1, ONE); SwapRequest o = w.raw("2", FR1, "일본어"); MatchResult r = w.resultFor(w.raw("1", JP1, "프랑스어"), o); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); eq("상대 학점 초과: 교환 후 19학점 (최대 18)", r.getReason(), "사유"); });
        t("제외(상대 쪽): 상대 시간표 겹침", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1, WIDE); SwapRequest o = w.raw("2", LF1, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); eq("상대 시간표 겹침: 넓은", r.getReason(), "사유"); });
        t("내 쪽과 상대 쪽 문제가 함께 있으면 내 쪽 사유가 먼저, 빼고 교환도 불가", () -> { W w = new W(); w.stu("1", true, M1, FR1); w.stu("2", true, LF2, WIDE); SwapRequest o = w.raw("2", LF2, "프랑스어"); MatchResult r = w.resultFor(w.raw("1", FR1, "생명"), o); eq("내 시간표 겹침: 전공A", r.getReason(), "사유"); ok(!r.isResolvable(), "상대 쪽 문제는 내가 못 고침"); });
        t("같은 과목을 내놓는 상대가 여럿이면 요청 번호 순서로 전부", () -> { W w = new W(); w.stu("1", true, M1, FR1); List<Integer> expect = new ArrayList<>();
            String[] secs = {LF1, LF2, LF3, LF4, LF1, LF4}; for (int i = 0; i < secs.length; i++) { w.stu("p" + i, true, secs[i]); expect.add(w.raw("p" + i, secs[i], "프랑스어").getId()); }
            List<Integer> got = new ArrayList<>(); List<Boolean> possible = new ArrayList<>(); for (MatchResult r : w.match(w.raw("1", FR1, "생명"))) { got.add(r.getRequest().getId()); possible.add(r.isPossible()); }
            eq(expect, got, "순서"); eq(Arrays.asList(true, false, true, true, true, true), possible, "판정"); });
        t("대기 상태가 아닌 내 요청으로 돌려도 죽지 않는다", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1); w.raw("2", LF1, "프랑스어"); eq(1, w.match(w.raw("1", FR1, "생명", RequestStatus.CANCELLED)).size(), "후보"); });
        t("빈 시간표로 돌려도 죽지 않는다", () -> { W w = new W(); w.stu("1", true); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"); eq("내놓는 과목이 내 시간표에 없음", w.resultFor(w.raw("1", FR1, "생명"), o).getReason(), "사유"); });
        t("매칭은 아무 상태도 바꾸지 않는다", () -> { W w = randomWorld(3, 12); String before = w.snapshot(); for (SwapRequest r : w.board.all()) w.match(r); eq(before, w.snapshot(), "상태"); });
        int[] total = {0, 0};
        for (int seed = 1; seed <= 60; seed++) { final int fseed = seed;
            t("무작위 세계 (seed " + seed + "): 판정은 양쪽에서 봐도 같고, '가능'이면 실제로 바꿔도 위반이 없다", () -> { W w = randomWorld(fseed, 12);
                eq(Collections.emptyList(), violations(w), "준비 상태 위반");
                for (SwapRequest m : w.board.all()) { if (!m.isWaiting()) continue;
                    for (MatchResult r : w.match(m)) { total[0]++; SwapRequest o = r.getRequest();
                        ok(o.isWaiting() && !o.getStudentId().equals(m.getStudentId()), "후보 조건"); eq(m.getWantName(), o.getGiveCourse().getName(), "교차 일치 1"); eq(m.getGiveCourse().getName(), o.getWantName(), "교차 일치 2");
                        MatchResult back = w.resultFor(o, m); ok(back != null, "#" + o.getId() + " 쪽에서는 #" + m.getId() + " 이 후보가 아님"); eq(r.isPossible(), back.isPossible(), "#" + m.getId() + " <-> #" + o.getId() + " 양쪽 판정 (" + r.getReason() + " / " + back.getReason() + ")");
                        ok(r.getReason() != null && !r.getReason().isEmpty(), "사유가 비어 있음");
                        if (r.isPossible()) { total[1]++; Timetable a = copy(w.get(m.getStudentId()).getTimetable()), b = copy(w.get(o.getStudentId()).getTimetable());
                            ok(a.replace(Collections.singletonList(m.getGiveCourse()), o.getGiveCourse()), "내 시간표 교체 실패"); ok(b.replace(Collections.singletonList(o.getGiveCourse()), m.getGiveCourse()), "상대 시간표 교체 실패"); }
                        if (r.isResolvable()) { Timetable a = copy(w.get(m.getStudentId()).getTimetable()); List<Course> leaving = new ArrayList<>(r.getDroppable()); leaving.add(m.getGiveCourse()); ok(a.replace(leaving, o.getGiveCourse()), "빼고 교환이 된다고 했는데 교체 실패"); } } } }); }
        t("무작위 세계 전체에서 후보와 '가능'이 충분히 나왔다 (테스트가 약하지 않음)", () -> { ok(total[0] > 200, "후보 " + total[0]); ok(total[1] > 20, "가능 " + total[1]); System.out.println("       후보 " + total[0] + "쌍, 가능 " + total[1] + "쌍"); });
        done();
    }
    static Timetable copy(Timetable src) {
        Timetable tt = new Timetable();
        for (Course c : src.getCourses()) tt.restore(c);
        return tt;
    }

    static void exchange() throws IOException {
        sec("11. 교환 확정 (ExchangeService)");
        t("확정: 두 시간표가 맞바뀌고 두 요청이 매칭완료", () -> { W w = new W(); w.stu("1", true, M1, FR1); w.stu("2", true, LF1, M2); SwapRequest o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            eq(Collections.emptyList(), w.exchange.confirm(m, o), "자동 취소"); eq(Arrays.asList(M1, LF1), w.codes("1"), "내 시간표"); eq(Arrays.asList(M2, FR1), w.codes("2"), "상대 시간표"); eq(RequestStatus.MATCHED, m.getStatus(), "내 요청"); eq(RequestStatus.MATCHED, o.getStatus(), "상대 요청"); });
        t("확정: 어느 쪽에서 눌러도 결과가 같다", () -> { W a = new W(); a.stu("1", true, M1, FR1); a.stu("2", true, LF1, M2); SwapRequest ao = a.raw("2", LF1, "프랑스어"), am = a.raw("1", FR1, "생명"); a.exchange.confirm(am, ao);
            W b = new W(); b.stu("1", true, M1, FR1); b.stu("2", true, LF1, M2); SwapRequest bo = b.raw("2", LF1, "프랑스어"), bm = b.raw("1", FR1, "생명"); b.exchange.confirm(bo, bm); eq(a.snapshot(), b.snapshot(), "상태"); });
        t("확정: 같은 쌍을 두 번 확정할 수 없다", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명"); w.exchange.confirm(m, o); String after = w.snapshot();
            eq("대기 상태가 아닌 요청은 교환할 수 없습니다", rejected(() -> w.exchange.confirm(m, o)), "사유"); eq(after, w.snapshot(), "상태"); });
        for (int which = 0; which < 4; which++) { final int fw = which; String[] label = {"내 요청이 취소됨", "상대 요청이 취소됨", "내 요청이 매칭완료", "상대 요청이 매칭완료"};
            t("거절: " + label[which] + " -> 아무것도 안 바뀜", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1);
                SwapRequest o = w.raw("2", LF1, "프랑스어", fw == 1 ? RequestStatus.CANCELLED : fw == 3 ? RequestStatus.MATCHED : RequestStatus.WAITING), m = w.raw("1", FR1, "생명", fw == 0 ? RequestStatus.CANCELLED : fw == 2 ? RequestStatus.MATCHED : RequestStatus.WAITING);
                String before = w.snapshot(); eq("대기 상태가 아닌 요청은 교환할 수 없습니다", rejected(() -> w.exchange.confirm(m, o)), "사유"); eq(before, w.snapshot(), "상태"); }); }
        t("거절: 요청자의 시간표를 모르면", () -> { W w = new W(); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"), m = w.raw("999", FR1, "생명"); String before = w.snapshot(); eq("요청자의 시간표를 찾을 수 없습니다", rejected(() -> w.exchange.confirm(m, o)), "사유"); eq(before, w.snapshot(), "상태"); });
        t("거절: 조건이 안 맞는 요청과는", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, JP1); SwapRequest o = w.raw("2", JP1, "프랑스어"), m = w.raw("1", FR1, "생명"); String before = w.snapshot(); eq("내 요청과 조건이 맞지 않습니다", rejected(() -> w.exchange.confirm(m, o)), "사유"); eq(before, w.snapshot(), "상태"); });
        t("거절: 자기 요청 둘끼리는", () -> { W w = new W(); w.stu("1", true, FR1, LF1); SwapRequest a = w.raw("1", LF1, "프랑스어"), b = w.raw("1", FR1, "생명"); String before = w.snapshot(); eq("내 요청과 조건이 맞지 않습니다", rejected(() -> w.exchange.confirm(a, b)), "사유"); eq(before, w.snapshot(), "상태"); });
        t("거절: 같은 요청을 양쪽에 넣으면", () -> { W w = new W(); w.stu("1", true, FR1); SwapRequest m = w.raw("1", FR1, "생명"); String before = w.snapshot(); ok(rejected(() -> w.exchange.confirm(m, m)) != null, "확정됨"); eq(before, w.snapshot(), "상태"); });
        String[][] excluded = {
            {"M1,FR1", "LF2", "LF2", "내 시간표 겹침: 전공A"}, {"FR1,LF4", "LF1", "LF1", "내가 이미 듣는 과목: 생명 004"}, {"FR1", "LF1,FR2", "LF1", "상대가 이미 듣는 과목: 프랑스어 002"},
            {"FR1", "LF1,WIDE", "LF1", "상대 시간표 겹침: 넓은"}, {"M1", "LF1", "LF1", "내놓는 과목이 내 시간표에 없음"}, {"FR1", "M1", "LF1", "상대가 내놓는 과목이 상대 시간표에 없음"},
        };
        for (String[] c : excluded) for (boolean drop : new boolean[]{false, true}) { if (drop && c[3].startsWith("내 시간표 겹침")) continue;
            t("거절: " + c[3] + (drop ? " (빼고 교환을 골라도)" : "") + " -> 아무것도 안 바뀜", () -> { W w = new W(); w.stu("1", true, codesBy(c[0])); w.stu("2", true, codesBy(c[1]));
                SwapRequest o = w.raw("2", codeBy(c[2]), "프랑스어"), m = w.raw("1", FR1, "생명"); String before = w.snapshot(); eq(c[3], rejected(() -> w.exchange.confirm(m, o, drop)), "사유"); eq(before, w.snapshot(), "상태"); }); }
        t("빼고 교환: 동의하면 겹치는 과목까지 빠진다", () -> { W w = new W(); w.stu("1", true, M1, FR1, M3); w.stu("2", true, LF2); SwapRequest o = w.raw("2", LF2, "프랑스어"), m = w.raw("1", FR1, "생명");
            w.exchange.confirm(m, o, true); eq(Arrays.asList(M3, LF2), w.codes("1"), "내 시간표"); eq(Collections.singletonList(FR1), w.codes("2"), "상대 시간표"); eq(Collections.emptyList(), violations(w), "위반"); });
        t("빼고 교환: 겹침이 없는 '가능'에 동의해도 다른 과목은 안 빠진다", () -> { W w = new W(); w.stu("1", true, M1, FR1, M3); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            w.exchange.confirm(m, o, true); eq(Arrays.asList(M1, M3, LF1), w.codes("1"), "내 시간표"); });
        t("빼고 교환: 여러 과목과 겹치면 전부 빠진다 (넓은 화3-8)", () -> { W w = new W(); w.stu("1", true, M2, ADJ, FR1, LF4, JP2); w.stu("2", true, WIDE); SwapRequest o = w.raw("2", WIDE, "일본어"), m = w.raw("1", JP2, "넓은");
            MatchResult r = w.resultFor(m, o); ok(r.isResolvable(), r.getReason()); eq(Arrays.asList(M2, ADJ, FR1), codesOf(r.getDroppable()), "뺄 과목"); w.exchange.confirm(m, o, true); eq(Arrays.asList(LF4, WIDE), w.codes("1"), "내 시간표"); eq(Collections.singletonList(JP2), w.codes("2"), "상대 시간표"); });
        t("자동 취소: 같은 과목을 내놓던 내 다른 대기 요청", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1); SwapRequest extra = w.raw("1", FR1, "일본어"), o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            eq(Collections.singletonList(extra), w.exchange.confirm(m, o), "취소된 요청"); eq(RequestStatus.CANCELLED, extra.getStatus(), "상태"); });
        t("자동 취소: 방금 얻은 과목을 희망하던 내 다른 대기 요청", () -> { W w = new W(); w.stu("1", true, FR1, JP1); w.stu("2", true, LF1); SwapRequest extra = w.raw("1", JP1, "생명"), o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            eq(Collections.singletonList(extra), w.exchange.confirm(m, o), "취소된 요청"); eq(RequestStatus.CANCELLED, extra.getStatus(), "상태"); ok(w.get("1").getTimetable().contains(JP1), "일본어는 그대로"); });
        t("자동 취소: 상대 쪽의 낡은 요청도 함께", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1, JP1); SwapRequest e1 = w.raw("2", LF1, "영화"), e2 = w.raw("2", JP1, "프랑스어"), o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            eq(Arrays.asList(e1, e2), w.exchange.confirm(m, o), "취소된 요청"); eq(RequestStatus.CANCELLED, e1.getStatus(), "내놓던 과목이 없어진 요청"); eq(RequestStatus.CANCELLED, e2.getStatus(), "얻은 과목을 희망하던 요청"); });
        t("자동 취소되지 않는 것: 상관없는 내 요청, 다른 학생의 요청, 이미 끝난 요청", () -> { W w = new W(); w.stu("1", true, FR1, JP1); w.stu("2", true, LF1); w.stu("3", true, LF4);
            SwapRequest keep1 = w.raw("1", JP1, "영화"), keep2 = w.raw("3", LF4, "프랑스어"), old = w.raw("1", FR1, "일본어", RequestStatus.CANCELLED), o = w.raw("2", LF1, "프랑스어"), m = w.raw("1", FR1, "생명");
            eq(Collections.emptyList(), w.exchange.confirm(m, o), "취소된 요청"); eq(RequestStatus.WAITING, keep1.getStatus(), "상관없는 내 요청"); eq(RequestStatus.WAITING, keep2.getStatus(), "다른 학생"); eq(RequestStatus.CANCELLED, old.getStatus(), "이미 취소"); });
        t("상대 시간표를 모르면 내 시간표만 바뀌고 두 요청은 매칭완료", () -> { W w = new W(); w.stu("1", true, FR1); SwapRequest o = w.raw("999", LF1, "프랑스어"), m = w.raw("1", FR1, "생명"); w.exchange.confirm(m, o);
            eq(Collections.singletonList(LF1), w.codes("1"), "내 시간표"); eq(RequestStatus.MATCHED, o.getStatus(), "상대 요청"); eq(1, w.registry.size(), "학생이 새로 생기지 않음"); });
        t("확정 뒤에는 남은 후보들과 더는 교환되지 않는다", () -> { W w = new W(); w.stu("1", true, FR1); w.stu("2", true, LF1); w.stu("3", true, LF4); SwapRequest o2 = w.raw("2", LF1, "프랑스어"), o3 = w.raw("3", LF4, "프랑스어"), m = w.raw("1", FR1, "생명");
            w.exchange.confirm(m, o2); ok(rejected(() -> w.exchange.confirm(m, o3)) != null, "두 번째 교환이 됨"); eq(Collections.singletonList(LF4), w.codes("3"), "셋째 학생 시간표"); eq(RequestStatus.WAITING, o3.getStatus(), "셋째 요청은 대기"); });
        t("세 사람 연쇄: 1<->2 뒤에 2가 새 요청으로 3과 교환", () -> { W w = new W(); Student s1 = w.stu("1", true, FR1), s2 = w.stu("2", true, LF1), s3 = w.stu("3", true, JP1);
            w.exchange.confirm(w.board.register(s1, w.c(FR1), "생명"), w.board.register(s2, w.c(LF1), "프랑스어"));
            SwapRequest a = w.board.register(s2, w.c(FR1), "일본어"), b = w.board.register(s3, w.c(JP1), "프랑스어"); w.exchange.confirm(a, b);
            eq(Collections.singletonList(LF1), w.codes("1"), "1"); eq(Collections.singletonList(JP1), w.codes("2"), "2"); eq(Collections.singletonList(FR1), w.codes("3"), "3"); eq(Collections.emptyList(), violations(w), "위반"); });
        t("정원이 찬 과목끼리도 교환은 된다 (1:1이라 인원이 그대로)", () -> { W w = new W(); w.stu("1", true, CF); w.stu("2", true, FULL); SwapRequest o = w.raw("2", FULL, "마감교양"), m = w.raw("1", CF, "꽉찬교양");
            int cf = w.enroll.enrolledOf(w.c(CF)), full = w.enroll.enrolledOf(w.c(FULL)); w.exchange.confirm(m, o); eq(cf, w.enroll.enrolledOf(w.c(CF)), "마감교양 인원"); eq(full, w.enroll.enrolledOf(w.c(FULL)), "꽉찬교양 인원"); });
        for (int seed = 1; seed <= 40; seed++) { final int fseed = seed;
            t("무작위 세계 (seed " + seed + "): 가능한 쌍을 차례로 확정, 거절은 상태를 안 바꾸고 확정 뒤에도 위반이 없다", () -> { W w = randomWorld(100 + fseed, 14); Random rnd = new Random(fseed); int confirmed = 0;
                for (SwapRequest m : new ArrayList<>(w.board.all())) { if (!m.isWaiting()) continue;
                    for (MatchResult r : w.match(m)) { boolean drop = rnd.nextBoolean(); boolean expect = r.isPossible() || (drop && r.isResolvable()); String before = w.snapshot();
                        try { List<SwapRequest> cancelled = w.exchange.confirm(m, r.getRequest(), drop); ok(expect, "제외(" + r.getReason() + ")인데 확정됨"); confirmed++;
                            eq(RequestStatus.MATCHED, m.getStatus(), "내 요청"); eq(RequestStatus.MATCHED, r.getRequest().getStatus(), "상대 요청"); for (SwapRequest c : cancelled) eq(RequestStatus.CANCELLED, c.getStatus(), "자동 취소 #" + c.getId());
                            ok(w.get(m.getStudentId()).getTimetable().contains(r.getReceiveCourse().getCode()), "받은 과목이 없음"); ok(!w.get(m.getStudentId()).getTimetable().contains(m.getGiveCourse().getCode()), "내놓은 과목이 남음"); }
                        catch (RequestRejectedException e) { ok(!expect, "확정돼야 하는데 거절: " + e.getReason()); eq(before, w.snapshot(), "거절인데 상태가 바뀜"); }
                        eq(Collections.emptyList(), violations(w), "위반"); if (!m.isWaiting()) break; } }
                Path dir = writeCourses("ex-" + fseed); w.save(dir); eq(w.snapshot(), load(dir).snapshot(), "저장 -> 복원"); }); }
        done();
    }
    static String codeBy(String name) {
        try { return (String) EdgeCaseTest.class.getDeclaredField(name).get(null); } catch (ReflectiveOperationException e) { throw new AssertionError("상수 없음 " + name); }
    }
    static String[] codesBy(String names) {
        String[] parts = names.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = codeBy(parts[i]);
        return parts;
    }

    static void enrollment() throws IOException {
        sec("12. 정원·빈자리 (EnrollmentService)");
        int[][] cases = {{0, 0, 0}, {0, 50, 0}, {2, 2, 0}, {2, 1, 0}, {2, 0, 0}, {2, 5, 0}, {1, 0, 1}, {40, 39, 0}, {40, 0, 0}, {40, 39, 1}, {40, 38, 3}, {1, 0, 0}, {0, 0, 3}, {3, 0, 3}, {3, 0, 2}};
        String[] texts = {"제한 없음", "제한 없음", "마감 (2/2)", "빈자리 1 (1/2)", "빈자리 2 (0/2)", "마감 (2/2)", "마감 (1/1)", "빈자리 1 (39/40)", "빈자리 40 (0/40)", "마감 (40/40)", "마감 (40/40)", "빈자리 1 (0/1)", "제한 없음", "마감 (3/3)", "빈자리 1 (2/3)"};
        for (int i = 0; i < cases.length; i++) { final int at = i; int[] c = cases[i];
            t("정원 " + c[0] + ", 파일 수강인원 " + c[1] + ", 이 프로그램 수강생 " + c[2] + "명 -> '" + texts[i] + "'", () -> { StudentRegistry reg = new StudentRegistry(); EnrollmentService es = new EnrollmentService(reg);
                Course course = new Course("20001001", "과목", "001", CourseType.GENERAL, Collections.emptyList(), "", "", 2, cases[at][0], cases[at][1]);
                for (int k = 0; k < cases[at][2]; k++) reg.register("s" + k, "학생").getTimetable().add(course);
                eq(texts[at], es.describe(course), "표시"); boolean full = texts[at].startsWith("마감"); eq(full, es.isFull(course), "마감 여부"); eq(cases[at][0] > 0, es.hasLimit(course), "제한 여부");
                ok(es.vacancyOf(course) >= 0, "빈자리는 음수가 아님"); eq(cases[at][1] + cases[at][2], es.enrolledOf(course), "수강인원");
                Student confirmedStudent = new Student("c", "확정", new Timetable(), true), editing = new Student("e", "편집", new Timetable(), false);
                es.validateEnroll(editing, course); String reason = rejected(() -> es.validateEnroll(confirmedStudent, course)); if (full) has(reason, "정원이 마감되었습니다 (" + cases[at][0] + "명)"); else eq(null, reason, "확정 학생 수강신청"); }); }
        t("마지막 자리를 누가 차지하면 다음 확정 학생은 거절, 빠지면 다시 가능", () -> { W w = new W(); Student a = w.stu("1", true), b = w.stu("2", true); Course co = w.c(CO); eq(1, w.enroll.vacancyOf(co), "빈자리");
            w.enroll.validateEnroll(a, co); a.getTimetable().add(co); ok(w.enroll.isFull(co), "마감"); ok(rejected(() -> w.enroll.validateEnroll(b, co)) != null, "두 번째 학생"); a.getTimetable().remove(co); w.enroll.validateEnroll(b, co); });
        t("편집 중인 학생은 마감 과목도 옮겨 적을 수 있고, 그만큼 인원이 는다", () -> { W w = new W(); Student a = w.stu("1", false); Course cf = w.c(CF); w.enroll.validateEnroll(a, cf); a.getTimetable().add(cf); eq(3, w.enroll.enrolledOf(cf), "인원"); eq("마감 (2/2)", w.enroll.describe(cf), "표시는 정원을 넘지 않음"); eq(0, w.enroll.vacancyOf(cf), "빈자리"); });
        t("거절 사유에 교환 요청 안내가 들어 있다", () -> { W w = new W(); Student a = w.stu("1", true); try { w.enroll.validateEnroll(a, w.c(CF)); } catch (RequestRejectedException e) { eq("마감교양 001", e.getTarget(), "대상"); has(e.getReason(), "교환 요청"); return; } throw new AssertionError("거절 안 됨"); });
        for (Course c : new W().courses) t(c.getCode() + " " + c.getName() + ": 듣던 학생이 삭제하면 수강인원이 1 줄고, 정원이 있으면 빈자리가 1 는다 (마감을 넘겨 적은 경우는 제외)", () -> { W w = new W(); Student s = w.stu("1", true, c.getCode()); Course course = w.c(c.getCode());
            int enrolled = w.enroll.enrolledOf(course), vacancy = w.enroll.vacancyOf(course); boolean over = course.getCapacity() > 0 && enrolled > course.getCapacity(); ok(s.getTimetable().remove(course), "삭제");
            eq(enrolled - 1, w.enroll.enrolledOf(course), "수강인원"); if (course.getCapacity() > 0 && !over) { eq(vacancy + 1, w.enroll.vacancyOf(course), "빈자리"); ok(!w.enroll.isFull(course), "삭제 뒤에는 마감이 아님"); has(w.enroll.seatNote(course), "빈자리"); } else if (course.getCapacity() == 0) eq("", w.enroll.seatNote(course), "제한 없는 과목의 안내"); });
        t("학생이 없어도, 과목을 아무도 안 들어도 죽지 않는다", () -> { EnrollmentService es = new EnrollmentService(new StudentRegistry()); Course c = new Course("1", "가", "001", CourseType.GENERAL, Collections.emptyList(), "", "", 2, 10, 0); eq(0, es.enrolledOf(c), "인원"); eq(10, es.vacancyOf(c), "빈자리"); });
        done();
    }

    static int fileNo = 0;
    static String loadError(Path dir) {
        try { load(dir); } catch (IOException e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }
        catch (RuntimeException e) { throw new AssertionError("IOException이 아닌 예외로 죽음: " + e); }
        return null;
    }
    static void badFile(String label, String file, String content, String expect) {
        t(file + " 거절: " + label, () -> { Path dir = writeCourses("bad-" + (++fileNo)); Files.write(dir.resolve(file), content.getBytes(StandardCharsets.UTF_8));
            String error = loadError(dir); ok(error != null, "그대로 읽힘"); has(error, expect); ok(error.startsWith(file), "어느 파일인지 알려 주지 않음: " + error); });
    }
    static void goodFile(String label, String file, String content, java.util.function.Consumer<W> check) {
        t(file + " 읽힘: " + label, () -> { Path dir = writeCourses("good-" + (++fileNo)); Files.write(dir.resolve(file), content.getBytes(StandardCharsets.UTF_8));
            W w; try { w = load(dir); } catch (IOException e) { throw new AssertionError("거절됨: " + e.getMessage()); } check.accept(w); });
    }
    static final String ROW = "20001001,프랑스어,001,GENERAL,화5-6,교수,강의실,2,0,0";

    static void storageCourses() {
        sec("13. courses.csv 읽기 (FileStorage.loadCourses)");
        String h = HEAD_C + "\n";
        String[][] bad = {
            {"과목 코드가 빈 줄", h + ",가,001,GENERAL,월1,교수,방,2,0,0\n", "과목 코드가 비어"}, {"쉼표만 있는 줄", h + ",,,,\n", "과목 코드가 비어"}, {"과목명이 빈 줄", h + "1,,001,GENERAL,월1,교수,방,2,0,0\n", "과목명이 비어"},
            {"과목명이 공백뿐인 줄", h + "1,   ,001,GENERAL,월1,교수,방,2,0,0\n", "과목명이 비어"}, {"과목 코드 중복 (바로 다음 줄)", h + ROW + "\n" + ROW + "\n", "중복"}, {"과목 코드 중복 (떨어진 줄, 줄 번호)", h + ROW + "\n10001001,전공A,001,MAJOR,월1-3\n" + ROW + "\n", "4번째 줄"},
            {"구분 소문자", h + "1,가,001,general,월1\n", "구분"}, {"구분 한글", h + "1,가,001,교양,월1\n", "구분"}, {"구분 빈 칸", h + "1,가,001,,월1\n", "구분"}, {"구분 두 개", h + "1,가,001,MAJOR;GENERAL,월1\n", "구분"}, {"모르는 구분", h + "1,가,001,ETC,월1\n", "구분"},
            {"시간 '월-'", h + "1,가,001,GENERAL,월-\n", "시간 형식"}, {"모르는 요일 X3", h + "1,가,001,GENERAL,X3\n", "요일"}, {"거꾸로 된 교시", h + "1,가,001,GENERAL,월5-3\n", "교시"}, {"0교시", h + "1,가,001,GENERAL,월0\n", "교시"},
            {"물결표 구간", h + "1,가,001,GENERAL,월3~4\n", "시간 형식"}, {"요일과 교시 사이 공백", h + "1,가,001,GENERAL,월 3-4\n", "시간 형식"}, {"영문 요일", h + "1,가,001,GENERAL,MON3\n", "시간 형식"}, {"구간 세 개", h + "1,가,001,GENERAL,월3-4-5\n", "시간 형식"},
            {"요일 없는 시간", h + "1,가,001,GENERAL,3-4\n", "시간 형식"}, {"'월요일3'", h + "1,가,001,GENERAL,월요일3\n", "시간 형식"}, {"세 자리 교시", h + "1,가,001,GENERAL,월100\n", "시간 형식"}, {"둘째 시간이 잘못됨", h + "1,가,001,GENERAL,월1;화\n", "시간 형식"},
            {"세미콜론 대신 쉼표로 나눈 시간 (칸이 밀림)", h + "1,가,001,GENERAL,월1,화2,교수,방,2,0,0\n", "칸 수"}, {"학점 글자", h + "1,가,001,GENERAL,월1,교수,방,abc\n", "학점"}, {"학점 음수", h + "1,가,001,GENERAL,월1,교수,방,-1\n", "학점"},
            {"학점 소수", h + "1,가,001,GENERAL,월1,교수,방,1.5\n", "학점"}, {"학점 빈 칸", h + "1,가,001,GENERAL,월1,교수,방,\n", "학점"}, {"학점에 단위", h + "1,가,001,GENERAL,월1,교수,방,2학점\n", "학점"}, {"학점이 너무 큼", h + "1,가,001,GENERAL,월1,교수,방,99999999999\n", "학점"},
            {"정원 글자", h + "1,가,001,GENERAL,월1,교수,방,2,x\n", "정원"}, {"정원 음수", h + "1,가,001,GENERAL,월1,교수,방,2,-5\n", "정원"}, {"정원 빈 칸", h + "1,가,001,GENERAL,월1,교수,방,2,,0\n", "정원"}, {"수강인원 글자", h + "1,가,001,GENERAL,월1,교수,방,2,0,x\n", "수강인원"},
            {"수강인원 음수", h + "1,가,001,GENERAL,월1,교수,방,2,0,-1\n", "수강인원"}, {"네 칸", h + "1,가,001,GENERAL\n", "칸 수"}, {"세 칸", h + "1,가,001\n", "칸 수"}, {"한 칸", h + "그냥글자\n", "칸 수"}, {"열한 칸", h + ROW + ",덤\n", "칸 수"},
            {"닫히지 않은 따옴표", h + "1,\"가,001,GENERAL,월1,교수,방,2,0,0\n", "칸 수"}, {"헤더가 없는 파일 (첫 줄부터 과목)", ROW + "\n10001001,전공A,001,MAJOR,월1-3\n", "헤더"}, {"다른 파일의 헤더", HEAD_S + "\n" + ROW + "\n", "헤더"},
            {"첫 줄이 빈 줄", "\n" + ROW + "\n", "헤더"}, {"100번째 줄의 오류 위치", h + manyRows(98) + "1,가,001,GENERAL,월0\n", "100번째 줄"},
        };
        for (String[] b : bad) t("courses.csv 거절: " + b[0], () -> { Path dir = dirOf("cbad-" + (++fileNo)); Files.write(dir.resolve("courses.csv"), b[1].getBytes(StandardCharsets.UTF_8));
            try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { has(e.getMessage(), b[2]); ok(e.getMessage().startsWith("courses.csv"), "파일 이름 없음: " + e.getMessage()); return; }
            catch (RuntimeException e) { throw new AssertionError("IOException이 아닌 예외로 죽음: " + e); } throw new AssertionError("그대로 읽힘"); });
        Object[][] good = {
            {"다섯 칸 (옛 형식)", h + "1,가,001,GENERAL,월1\n", 1}, {"여섯 칸", h + "1,가,001,GENERAL,월1,교수\n", 1}, {"일곱 칸", h + "1,가,001,GENERAL,월1,교수,방\n", 1}, {"여덟 칸", h + "1,가,001,GENERAL,월1,교수,방,2\n", 1},
            {"아홉 칸", h + "1,가,001,GENERAL,월1,교수,방,2,30\n", 1}, {"열 칸", h + "1,가,001,GENERAL,월1,교수,방,2,30,29\n", 1}, {"윈도우 줄바꿈 (CRLF)", (h + ROW + "\n10001001,전공A,001,MAJOR,월1-3\n").replace("\n", "\r\n"), 2},
            {"BOM으로 시작 (메모장·엑셀 저장)", "﻿" + h + ROW + "\n", 1}, {"마지막 줄에 줄바꿈 없음", h + ROW, 1}, {"사이사이 빈 줄", h + "\n" + ROW + "\n\n\n10001001,전공A,001,MAJOR,월1-3\n\n", 2}, {"공백뿐인 줄", h + "   \n" + ROW + "\n\t\n", 1},
            {"칸 앞뒤 공백", h + " 1 , 가 , 001 , GENERAL , 월1 , 교수 , 방 , 2 , 0 , 0 \n", 1}, {"따옴표로 감싼 과목명 (쉼표 포함)", h + "1,\"가,나\",001,GENERAL,월1\n", 1}, {"따옴표 안의 따옴표", h + "1,\"가\"\"나\"\"\",001,GENERAL,월1\n", 1},
            {"시간 빈 칸", h + "1,가,001,GENERAL,\n", 1}, {"시간 ';;'", h + "1,가,001,GENERAL,;;\n", 1}, {"시간 '월1;;화2'", h + "1,가,001,GENERAL,월1;;화2\n", 1}, {"시간 '월1; 화2'", h + "1,가,001,GENERAL,월1; 화2\n", 1}, {"시간 '월1;'", h + "1,가,001,GENERAL,월1;\n", 1},
            {"같은 시간 두 번", h + "1,가,001,GENERAL,월1;월1\n", 1}, {"학점 0", h + "1,가,001,GENERAL,월1,교수,방,0\n", 1}, {"학점 앞에 0", h + "1,가,001,GENERAL,월1,교수,방,03\n", 1}, {"수강인원이 정원보다 많음", h + "1,가,001,GENERAL,월1,교수,방,2,10,99\n", 1},
            {"헤더만 있는 파일", h, 0}, {"빈 파일 (0바이트)", "", 0}, {"분반이 코드 끝과 다름", h + "1,가,XYZ,GENERAL,월1\n", 1}, {"분반 빈 칸", h + "1,가,,GENERAL,월1\n", 1}, {"아주 긴 과목명 (500자)", h + "1," + "가".repeat(500) + ",001,GENERAL,월1\n", 1},
            {"이름이 같은 다른 코드", h + "1,가,001,GENERAL,월1\n2,가,001,MAJOR,월1\n", 2}, {"교수·강의실 빈 칸", h + "1,가,001,GENERAL,월1,,,2,0,0\n", 1}, {"15교시 이후 수업", h + "1,가,001,GENERAL,월15-16\n", 1}, {"헤더에 칸이 더 있음", h.trim() + ",비고,메모\n" + ROW + "\n", 1},
            {"과목 1만 개", h + manyRows(10000), 10000},
        };
        for (Object[] g : good) t("courses.csv 읽힘: " + g[0] + " -> " + g[2] + "개", () -> { Path dir = dirOf("cgood-" + (++fileNo)); Files.write(dir.resolve("courses.csv"), ((String) g[1]).getBytes(StandardCharsets.UTF_8));
            List<Course> cs; try { cs = new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { throw new AssertionError("거절됨: " + e.getMessage()); } eq(g[2], cs.size(), "과목 수");
            for (Course c : cs) { ok(!c.getCode().isEmpty() && !c.getName().isEmpty(), "빈 코드·이름"); ok(c.getCode().equals(c.getCode().trim()) && c.getName().equals(c.getName().trim()), "앞뒤 공백이 남음"); ok(!c.getCode().startsWith("﻿"), "BOM이 남음"); } });
        t("칸 값: 따옴표·공백·시간이 제대로 풀린다", () -> { Path dir = dirOf("cval"); put(dir, "courses.csv", HEAD_C, " 1 ,\"가,\"\"나\"\"\", 001 , MAJOR , 목4-5 ; 금3 , 김 교수 ,\"A-1,B-2\", 3 , 40 , 39 ");
            Course c = new FileStorage(dir.toString()).loadCourses().get(0); eq("1", c.getCode(), "코드"); eq("가,\"나\"", c.getName(), "과목명"); eq("001", c.getSection(), "분반"); eq(CourseType.MAJOR, c.getType(), "구분"); eq("목 4-5, 금 3", c.timesToString(), "시간");
            eq("김 교수", c.getProfessor(), "교수"); eq("A-1,B-2", c.getRoom(), "강의실"); eq(3, c.getCredits(), "학점"); eq(40, c.getCapacity(), "정원"); eq(39, c.getBaseEnrolled(), "수강인원"); });
        t("UTF-8이 아닌 파일(EUC-KR)은 인코딩 안내와 함께 거절", () -> { Path dir = dirOf("ceuc"); Files.write(dir.resolve("courses.csv"), (h + ROW + "\n").getBytes(Charset.forName("EUC-KR")));
            try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { has(e.getMessage(), "UTF-8"); return; } throw new AssertionError("그대로 읽힘"); });
        t("courses.csv가 없으면 NoSuchFileException", () -> { Path dir = dirOf("cnone"); try { new FileStorage(dir.toString()).loadCourses(); } catch (NoSuchFileException e) { return; } throw new AssertionError("예외 없음"); });
        t("데이터 폴더가 없어도 NoSuchFileException (폴더를 만들지 않는다)", () -> { Path dir = base.resolve("no-such-dir"); try { new FileStorage(dir.toString()).loadCourses(); } catch (NoSuchFileException e) { ok(!Files.exists(dir), "폴더가 생김"); return; } throw new AssertionError("예외 없음"); });
        t("courses.csv가 폴더이면 IOException", () -> { Path dir = dirOf("cdir"); Files.createDirectories(dir.resolve("courses.csv")); try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { return; } throw new AssertionError("예외 없음"); });
        t("읽을 권한이 없으면 안내와 함께 IOException", () -> { Path dir = dirOf("cperm"); put(dir, "courses.csv", HEAD_C, ROW); java.io.File f = dir.resolve("courses.csv").toFile();
            if (!f.setReadable(false) || f.canRead()) return; try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { has(e.getMessage(), "읽을 권한"); return; } finally { f.setReadable(true); } throw new AssertionError("그대로 읽힘"); });
        t("읽기는 파일을 바꾸지 않는다", () -> { Path dir = writeCourses("cro"); byte[] before = Files.readAllBytes(dir.resolve("courses.csv")); new FileStorage(dir.toString()).loadCourses(); ok(Arrays.equals(before, Files.readAllBytes(dir.resolve("courses.csv"))), "파일이 바뀜"); });
        done();
    }
    static String manyRows(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append(String.format("5%07d,과목%d,%03d,GENERAL,월1-2,교수,강의실,2,0,0\n", i, i, i % 1000));
        return sb.toString();
    }

    static void storageStudents() {
        sec("14. students.csv 읽기 (FileStorage.loadStudents)");
        String h = HEAD_S + "\n";
        String[][] bad = {
            {"겹치는 과목이 든 시간표", h + "1,가,10001001;20002002,CONFIRMED\n", "2번째 줄"}, {"같은 분반이 두 번", h + "1,가,10001001;10001001,CONFIRMED\n", "10001001"}, {"과목 목록에 없는 코드", h + "1,가,10001001;77777777,CONFIRMED\n", "77777777"},
            {"학번 중복", h + "1,가,10001001,CONFIRMED\n1,나,10002001,CONFIRMED\n", "3번째 줄"}, {"학번 중복 (앞뒤 공백만 다름)", h + "1,가,,EDITING\n 1 ,나,,EDITING\n", "중복"}, {"시간표상태 오타", h + "1,가,10001001,CONFIRM\n", "CONFIRM"},
            {"시간표상태 소문자", h + "1,가,10001001,confirmed\n", "confirmed"}, {"시간표상태 한글", h + "1,가,10001001,확정\n", "확정"}, {"시간표상태 빈 칸", h + "1,가,10001001,\n", "시간표상태"}, {"빈 학번", h + ",가,10001001,CONFIRMED\n", "학번"},
            {"숫자가 아닌 학번", h + "abc,가,10001001,CONFIRMED\n", "학번"}, {"학번에 하이픈", h + "2026-1,가,,EDITING\n", "학번"}, {"학번 가운데 공백", h + "20 26,가,,EDITING\n", "학번"}, {"빈 이름", h + "1,,10001001,CONFIRMED\n", "이름"}, {"공백뿐인 이름", h + "1,  ,10001001,CONFIRMED\n", "이름"},
            {"다섯 칸", h + "1,가,10001001,CONFIRMED,덤\n", "칸 수"}, {"두 칸", h + "1,가\n", "칸 수"}, {"한 칸", h + "1\n", "칸 수"}, {"쉼표로 나눈 과목 코드 (칸이 밀림)", h + "1,가,10001001,20001001,CONFIRMED\n", "칸 수"},
            {"이름의 쉼표를 따옴표로 안 감쌈", h + "1,김,철수,10001001,CONFIRMED\n", "칸 수"}, {"헤더가 없는 파일 (첫 학생이 사라질 뻔)", "1,가,10001001,CONFIRMED\n2,나,,EDITING\n", "헤더"}, {"다른 파일의 헤더", HEAD_R + "\n1,가,10001001,CONFIRMED\n", "헤더"},
            {"과목명으로 적은 시간표", h + "1,가,전공A,CONFIRMED\n", "전공A"}, {"코드에 공백이 낌", h + "1,가,1000 1001,CONFIRMED\n", "1000 1001"}, {"둘째 학생의 오류 위치", h + "1,가,,EDITING\n\n2,나,99999999,CONFIRMED\n", "4번째 줄"},
        };
        for (String[] b : bad) badFile(b[0], "students.csv", b[1], b[2]);
        goodFile("시간표상태 칸이 없는 옛 형식", "students.csv", "학번,이름,과목코드\n1,가,10001001\n2,나,\n", w -> { ok(w.get("1").isTimetableConfirmed(), "과목 있으면 확정"); ok(!w.get("2").isTimetableConfirmed(), "없으면 편집 중"); });
        goodFile("편집 중인데 과목이 있음", "students.csv", h + "1,가,10001001;20001001,EDITING\n", w -> { ok(!w.get("1").isTimetableConfirmed(), "편집 중"); eq(2, w.get("1").getTimetable().size(), "과목"); });
        goodFile("확정인데 과목이 없음", "students.csv", h + "1,가,,CONFIRMED\n", w -> { ok(w.get("1").isTimetableConfirmed(), "확정"); eq(0, w.get("1").getTimetable().size(), "과목"); });
        goodFile("과목 코드 끝에 세미콜론", "students.csv", h + "1,가,10001001;20001001;,CONFIRMED\n", w -> eq(Arrays.asList(M1, FR1), w.codes("1"), "시간표"));
        goodFile("과목 코드 사이 ';;'와 공백", "students.csv", h + "1,가, 10001001 ;; 20001001 ,CONFIRMED\n", w -> eq(Arrays.asList(M1, FR1), w.codes("1"), "시간표"));
        goodFile("따옴표로 감싼 이름 (쉼표·따옴표)", "students.csv", h + "1,\"김,\"\"철\"\"수\",10001001,CONFIRMED\n", w -> eq("김,\"철\"수", w.get("1").getName(), "이름"));
        goodFile("18학점을 넘는 시간표 (제한 전에 저장한 파일)", "students.csv", h + "1,가," + String.join(";", M1, M2, M3, M4, M5, JP1, BIG) + ",CONFIRMED\n", w -> eq(21, w.get("1").getTimetable().getTotalCredits(), "학점"));
        goodFile("같은 과목 두 분반 (제한 전에 저장한 파일)", "students.csv", h + "1,가,20001001;20001002,CONFIRMED\n", w -> eq(2, w.get("1").getTimetable().size(), "과목"));
        goodFile("헤더만 있는 파일", "students.csv", h, w -> eq(0, w.registry.size(), "학생"));
        goodFile("빈 파일 (0바이트)", "students.csv", "", w -> eq(0, w.registry.size(), "학생"));
        goodFile("윈도우 줄바꿈 (CRLF)", "students.csv", (h + "1,가,10001001,CONFIRMED\n2,나,,EDITING\n").replace("\n", "\r\n"), w -> { eq(2, w.registry.size(), "학생"); ok(w.get("1").isTimetableConfirmed(), "상태 칸에 \\r이 남지 않음"); });
        goodFile("BOM으로 시작", "students.csv", "﻿" + h + "1,가,10001001,CONFIRMED\n", w -> eq(1, w.registry.size(), "학생"));
        goodFile("마지막 줄에 줄바꿈 없음", "students.csv", h + "1,가,10001001,CONFIRMED", w -> ok(w.get("1").isTimetableConfirmed(), "확정"));
        goodFile("학생·과목 순서가 파일 그대로", "students.csv", h + "3,다,20001001;10001001,CONFIRMED\n1,가,,EDITING\n2,나,,EDITING\n", w -> { eq("3", w.registry.all().get(0).getId(), "첫 학생"); eq("2", w.registry.all().get(2).getId(), "셋째 학생"); eq(Arrays.asList(FR1, M1), w.codes("3"), "과목 순서"); });
        goodFile("앞에 0이 붙은 학번·아주 긴 학번", "students.csv", h + "007,가,,EDITING\n" + "9".repeat(40) + ",나,,EDITING\n", w -> { ok(w.registry.find("007").isPresent(), "007"); ok(w.registry.find("7").isEmpty(), "7은 다른 학번"); ok(w.registry.find("9".repeat(40)).isPresent(), "긴 학번"); });
        goodFile("시간 없는 과목·주말 과목이 든 시간표", "students.csv", h + "1,가,20006001;20008001;40001001,CONFIRMED\n", w -> eq(3, w.get("1").getTimetable().size(), "과목"));
        goodFile("학생 2천 명", "students.csv", h + manyStudents(2000), w -> eq(2000, w.registry.size(), "학생"));
        t("students.csv가 없으면 빈 상태로 시작", () -> { W w = load(writeCourses("snone")); eq(0, w.registry.size(), "학생"); });
        t("students.csv가 UTF-8이 아니면 인코딩 안내", () -> { Path dir = writeCourses("seuc"); Files.write(dir.resolve("students.csv"), (h + "1,가나다,,EDITING\n").getBytes(Charset.forName("EUC-KR"))); String e = loadError(dir); ok(e != null, "읽힘"); has(e, "UTF-8"); });
        done();
    }
    static String manyStudents(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append(String.format("3%08d,학생%d,10001001;20001001,CONFIRMED\n", i, i));
        return sb.toString();
    }

    static void storageRequests() {
        sec("15. requests.csv 읽기 (FileStorage.loadRequests)");
        String h = HEAD_R + "\n";
        String[][] bad = {
            {"번호 글자", h + "abc,1,20001001,생명,WAITING\n", "요청 번호"}, {"번호 음수", h + "-1,1,20001001,생명,WAITING\n", "요청 번호"}, {"번호 0", h + "0,1,20001001,생명,WAITING\n", "1 이상"}, {"번호 소수", h + "1.5,1,20001001,생명,WAITING\n", "요청 번호"},
            {"번호 빈 칸", h + ",1,20001001,생명,WAITING\n", "요청 번호"}, {"번호가 int 범위를 넘음", h + "2147483648,1,20001001,생명,WAITING\n", "요청 번호"}, {"번호가 int 최댓값 (다음 번호가 음수가 됨)", h + "2147483647,1,20001001,생명,WAITING\n", "요청 번호"},
            {"번호에 #", h + "#1,1,20001001,생명,WAITING\n", "요청 번호"}, {"번호 중복", h + "1,1,20001001,생명,WAITING\n1,2,20002001,프랑스어,WAITING\n", "중복"}, {"번호 중복 (01과 1)", h + "01,1,20001001,생명,WAITING\n1,2,20002001,프랑스어,WAITING\n", "중복"},
            {"상태 오타", h + "1,1,20001001,생명,WAIT\n", "WAIT"}, {"상태 소문자", h + "1,1,20001001,생명,waiting\n", "waiting"}, {"상태 한글", h + "1,1,20001001,생명,대기\n", "대기"}, {"상태 빈 칸", h + "1,1,20001001,생명,\n", "상태"},
            {"없는 과목 코드", h + "1,1,88888888,생명,WAITING\n", "88888888"}, {"과목명으로 적은 내놓는 과목", h + "1,1,프랑스어,생명,WAITING\n", "프랑스어"}, {"내놓는 과목 빈 칸", h + "1,1,,생명,WAITING\n", "과목"}, {"네 칸", h + "1,1,20001001,생명\n", "칸 수"},
            {"여섯 칸", h + "1,1,20001001,생명,WAITING,덤\n", "칸 수"}, {"희망 과목의 쉼표를 따옴표로 안 감쌈", h + "1,1,20001001,쉼표,과목,WAITING\n", "칸 수"}, {"빈 학번", h + "1,,20001001,생명,WAITING\n", "학번"}, {"숫자가 아닌 학번", h + "1,abc,20001001,생명,WAITING\n", "학번"},
            {"희망 과목 빈 칸", h + "1,1,20001001,,WAITING\n", "희망"}, {"희망 과목 공백뿐", h + "1,1,20001001,   ,WAITING\n", "희망"}, {"헤더가 없는 파일 (첫 요청이 사라질 뻔)", "1,1,20001001,생명,WAITING\n", "헤더"}, {"다른 파일의 헤더", HEAD_S + "\n1,1,20001001,생명,WAITING\n", "헤더"},
            {"셋째 요청의 오류 위치", h + "1,1,20001001,생명,WAITING\n2,1,20001001,영화,WAITING\n3,1,20001001,일본어,DONE\n", "4번째 줄"},
        };
        for (String[] b : bad) badFile(b[0], "requests.csv", b[1], b[2]);
        for (RequestStatus st : RequestStatus.values()) goodFile("상태 " + st.name(), "requests.csv", h + "1,1,20001001,생명," + st.name() + "\n", w -> eq(st, w.board.all().get(0).getStatus(), "상태"));
        goodFile("번호가 뒤섞이고 건너뜀 -> 다음 번호는 가장 큰 번호 + 1", "requests.csv", h + "7,1,20001001,생명,WAITING\n2,1,20001001,영화,CANCELLED\n40,2,20002001,프랑스어,MATCHED\n", w -> { eq(3, w.board.size(), "요청 수");
            eq(41, w.board.register(w.stu("9", true, FR1), w.c(FR1), "일본어").getId(), "다음 번호"); eq(7, w.board.all().get(0).getId(), "파일 순서 유지"); });
        goodFile("학생 목록에 없는 학번의 요청", "requests.csv", h + "1,999,20001001,생명,WAITING\n", w -> eq("999", w.board.all().get(0).getStudentId(), "학번"));
        goodFile("과목 목록에 없는 희망 과목명 (학기가 바뀐 경우)", "requests.csv", h + "1,1,20001001,없어진과목,WAITING\n", w -> eq("없어진과목", w.board.all().get(0).getWantName(), "희망"));
        goodFile("따옴표로 감싼 희망 과목 (쉼표)", "requests.csv", h + "1,1,20001001,\"쉼표,과목\",WAITING\n", w -> eq("쉼표,과목", w.board.all().get(0).getWantName(), "희망"));
        goodFile("번호 앞뒤 공백·앞의 0", "requests.csv", h + " 3 ,1,20001001,생명,WAITING\n007,1,20001001,영화,WAITING\n", w -> { ok(w.board.find(3).isPresent(), "#3"); ok(w.board.find(7).isPresent(), "#7"); });
        goodFile("내놓는 과목이 주인의 시간표에 없는 요청 (매칭에서 제외됨)", "requests.csv", h + "1,1,20001001,생명,WAITING\n", w -> { w.stu("1", true, M1); w.stu("2", true, LF1); SwapRequest o = w.raw("2", LF1, "프랑스어"); eq("상대가 내놓는 과목이 상대 시간표에 없음", w.resultFor(o, w.board.find(1).get()).getReason(), "사유"); });
        goodFile("헤더만 있는 파일", "requests.csv", h, w -> eq(0, w.board.size(), "요청"));
        goodFile("빈 파일 (0바이트)", "requests.csv", "", w -> eq(0, w.board.size(), "요청"));
        goodFile("윈도우 줄바꿈 (CRLF)", "requests.csv", (h + "1,1,20001001,생명,WAITING\n2,1,20001001,영화,CANCELLED\n").replace("\n", "\r\n"), w -> { eq(2, w.board.size(), "요청"); eq(RequestStatus.CANCELLED, w.board.find(2).get().getStatus(), "상태 칸에 \\r이 남지 않음"); });
        goodFile("BOM으로 시작", "requests.csv", "﻿" + h + "1,1,20001001,생명,WAITING\n", w -> eq(1, w.board.size(), "요청"));
        goodFile("마지막 줄에 줄바꿈 없음", "requests.csv", h + "1,1,20001001,생명,WAITING", w -> eq(RequestStatus.WAITING, w.board.find(1).get().getStatus(), "상태"));
        goodFile("요청 5천 건", "requests.csv", h + manyRequests(5000), w -> { eq(5000, w.board.size(), "요청"); eq(5001, w.board.register(w.stu("9", true, FR1), w.c(FR1), "일본어").getId(), "다음 번호"); });
        t("requests.csv가 없으면 빈 상태로 시작", () -> { W w = load(writeCourses("rnone")); eq(0, w.board.size(), "요청"); eq(1, w.board.register(w.stu("1", true, FR1), w.c(FR1), "생명").getId(), "첫 번호"); });
        done();
    }
    static String manyRequests(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append(i).append(",").append(1000 + i % 50).append(",20001001,생명,").append(i % 3 == 0 ? "CANCELLED" : "WAITING").append("\n");
        return sb.toString();
    }

    static void storageRoundTrip() throws IOException {
        sec("16. 저장 -> 복원 (FileStorage.save*)");
        String[] names = {"가", "김철수", "김,철수", "\"따옴표\"", "a\"b", "\"\"", ",", ",,", "\"", "이름;세미콜론", "한 글", "O'Neil", "<tag>", "\\", "#", "=1+1", "😀", "ㅋㅋ", "가".repeat(200), "a,b,\"c\",d",
            "\"시작따옴표", "끝따옴표\"", ",쉼표시작", "쉼표끝,", "탭\t사이", "CONFIRMED", "학번,이름,과목코드,시간표상태", "1;2;3", "null", "求교환", "José", "'", "\"\"\"", "a\"\"b", "-", "0"};
        for (String name : names) t("이름 '" + show(name) + "' 저장 -> 복원", () -> { W w = new W(); Timetable tt = new Timetable(); tt.restore(w.c(M1)); tt.restore(w.c(FR1)); w.registry.put(new Student("1", name, tt, true)); w.registry.put(new Student("2", "다음학생", new Timetable(), false));
            Path dir = writeCourses("rt-" + (++fileNo)); w.save(dir); W x = load(dir); eq(name, x.get("1").getName(), "이름"); eq(w.snapshot(), x.snapshot(), "상태"); eq(3, Files.readAllLines(dir.resolve("students.csv")).size(), "줄 수 (한 학생 = 한 줄)"); });
        String[] wantNames = {"쉼표,과목", "따옴표\"과목", "\"전체따옴표\"", "a,b,\"c\"", "세미;콜론", "공백 있는 과목", "Java Programming", ",", "\"", "가".repeat(100)};
        for (String want : wantNames) t("희망 과목명 '" + show(want) + "' 저장 -> 복원", () -> { W w = new W(); w.stu("1", true, FR1); w.board.put(new SwapRequest(1, "1", w.c(FR1), want, RequestStatus.WAITING)); w.board.put(new SwapRequest(2, "1", w.c(FR1), "생명", RequestStatus.CANCELLED));
            Path dir = writeCourses("rw-" + (++fileNo)); w.save(dir); W x = load(dir); eq(want, x.board.find(1).get().getWantName(), "희망"); eq(w.snapshot(), x.snapshot(), "상태"); eq(3, Files.readAllLines(dir.resolve("requests.csv")).size(), "줄 수"); });
        t("저장한 파일의 모양: 헤더, 세미콜론 목록, enum 이름", () -> { W w = new W(); w.stu("1", true, M1, FR1); w.registry.put(new Student("2", "나", new Timetable(), false)); w.raw("1", FR1, "생명"); w.raw("1", FR1, "영화", RequestStatus.CANCELLED); w.raw("1", FR1, "일본어", RequestStatus.MATCHED);
            Path dir = writeCourses("shape"); w.save(dir); eq(Arrays.asList(HEAD_S, "1,학생1,10001001;20001001,CONFIRMED", "2,나,,EDITING"), Files.readAllLines(dir.resolve("students.csv")), "students.csv");
            eq(Arrays.asList(HEAD_R, "1,1,20001001,생명,WAITING", "2,1,20001001,영화,CANCELLED", "3,1,20001001,일본어,MATCHED"), Files.readAllLines(dir.resolve("requests.csv")), "requests.csv"); });
        t("아무도 없어도 헤더만 있는 파일을 쓰고, 다시 읽힌다", () -> { W w = new W(); Path dir = writeCourses("empty"); w.save(dir); eq(Collections.singletonList(HEAD_S), Files.readAllLines(dir.resolve("students.csv")), "students"); eq(Collections.singletonList(HEAD_R), Files.readAllLines(dir.resolve("requests.csv")), "requests"); W x = load(dir); eq(0, x.registry.size() + x.board.size(), "빈 상태"); });
        t("두 번 저장해도 파일이 같다", () -> { W w = randomWorld(7, 10); Path dir = writeCourses("twice"); w.save(dir); String first = fileState(dir); w.save(dir); eq(first, fileState(dir), "파일"); });
        t("저장은 courses.csv를 건드리지 않는다", () -> { W w = randomWorld(8, 5); Path dir = writeCourses("keep"); byte[] before = Files.readAllBytes(dir.resolve("courses.csv")); w.save(dir); ok(Arrays.equals(before, Files.readAllBytes(dir.resolve("courses.csv"))), "courses.csv가 바뀜"); });
        t("없는 폴더(여러 단계)에 저장하면 폴더를 만든다", () -> { Path dir = base.resolve("new-a").resolve("b").resolve("c"); new FileStorage(dir.toString()).saveStudents(Collections.emptyList()); ok(Files.exists(dir.resolve("students.csv")), "파일"); });
        t("저장할 자리가 폴더가 아니라 파일이면 IOException", () -> { Path file = base.resolve("iam-a-file"); Files.write(file, new byte[]{1}); try { new FileStorage(file.toString()).saveStudents(Collections.emptyList()); } catch (IOException e) { return; } throw new AssertionError("예외 없음"); });
        t("쓸 권한이 없으면 안내와 함께 IOException, 원래 파일은 그대로", () -> { W w = randomWorld(9, 3); Path dir = writeCourses("ro"); w.save(dir); java.io.File f = dir.resolve("students.csv").toFile(); String before = fileState(dir);
            if (!f.setWritable(false) || f.canWrite()) return; try { w.registry.register("999", "새학생"); new FileStorage(dir.toString()).saveStudents(w.registry.all()); } catch (IOException e) { has(e.getMessage(), "쓸 권한"); eq(before, fileState(dir), "파일"); return; } finally { f.setWritable(true); } throw new AssertionError("저장됨"); });
        for (int seed = 1; seed <= 20; seed++) { final int fseed = seed;
            t("무작위 세계 (seed " + seed + ") 저장 -> 복원 -> 저장: 상태도 파일도 같다", () -> { W w = randomWorld(200 + fseed, 15); Path dir = writeCourses("fix-" + fseed); w.save(dir); String first = fileState(dir); W x = load(dir); eq(w.snapshot(), x.snapshot(), "상태"); x.save(dir); eq(first, fileState(dir), "파일"); }); }
        done();
    }

    static String snap(Path dir) throws IOException { return load(dir).snapshot(); }
    static final String[] IDS = {"100", "101", "201", "202", "204", "205", "206", "300", "207"};
    static void sane(Path dir, String out) throws IOException {
        endsCleanly(out);
        hasNot(out, "Exception");
        eq(Collections.emptyList(), violations(load(dir)), "저장된 상태의 위반");
    }

    static void consoleStartup() {
        sec("17. 콘솔: 시작·종료·파일 문제 (Main)");
        t("데이터 폴더가 없으면 안내하고 끝난다 (폴더를 만들지 않음, 학번을 묻지 않음)", () -> { Path dir = base.resolve("nowhere"); String out = console(dir, "101", "0");
            has(out, "[안내]"); has(out, "courses.csv 파일을 찾을 수 없습니다"); hasNot(out, "학번 입력"); ok(!Files.exists(dir), "폴더가 생김"); });
        t("폴더는 있는데 courses.csv가 없으면 안내하고 끝난다", () -> { Path dir = dirOf("start-empty"); String out = console(dir, "101", "0"); has(out, "파일을 찾을 수 없습니다"); hasNot(out, "학번 입력"); ok(!Files.exists(dir.resolve("students.csv")), "students.csv가 생김"); });
        t("데이터 폴더 자리에 파일이 있으면 안내·오류를 내고 끝난다", () -> { Path file = base.resolve("start-file"); Files.write(file, new byte[]{1}); String out = console(file, "101", "0"); ok(out.contains("[안내]") || out.contains("[오류]"), clip(out)); hasNot(out, "학번 입력"); });
        t("courses.csv 자리에 폴더가 있으면 오류를 내고 끝난다", () -> { Path dir = dirOf("start-dir"); Files.createDirectories(dir.resolve("courses.csv")); String out = console(dir, "101", "0"); ok(out.contains("[안내]") || out.contains("[오류]"), clip(out)); hasNot(out, "학번 입력"); });
        t("데이터 폴더 이름에 쓸 수 없는 글자가 있어도 죽지 않는다", () -> { PrintStream o = System.out; InputStream in = System.in; ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try { System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8)); System.setIn(new ByteArrayInputStream(new byte[0])); kookyohwan.Main.main(new String[]{"bad\u0000dir"}); }
            catch (Throwable e) { throw new AssertionError("예외로 죽음: " + e); } finally { System.setOut(o); System.setIn(in); }
            String out = buf.toString(StandardCharsets.UTF_8); ok(out.contains("[안내]") || out.contains("[오류]"), clip(out)); hasNot(out, "학번 입력"); });
        String[][] corrupt = {
            {"courses.csv", HEAD_C + "\n1,가,001,GENERAL,월0\n", "courses.csv 2번째 줄"}, {"students.csv", HEAD_S + "\n101,가,77777777,CONFIRMED\n", "students.csv 2번째 줄"}, {"requests.csv", HEAD_R + "\n1,101,20001001,생명,WAIT\n", "requests.csv 2번째 줄"},
            {"students.csv", "101,가,10001001,CONFIRMED\n", "헤더"}, {"requests.csv", "1,101,20001001,생명,WAITING\n", "헤더"}, {"students.csv", HEAD_S + "\n101,가,10001001;20002002,CONFIRMED\n", "겹치는"},
        };
        for (String[] c : corrupt) t("깨진 " + c[0] + " (" + c[2] + "): 오류를 알리고 끝나며, 어떤 파일도 덮어쓰지 않는다", () -> { Path dir = world("start-bad-" + (++fileNo)); Files.write(dir.resolve(c[0]), c[1].getBytes(StandardCharsets.UTF_8));
            String before = fileState(dir); byte[] courses = Files.readAllBytes(dir.resolve("courses.csv")); String out = console(dir, "101", "2", M2, "", "0");
            has(out, "[오류] 파일을 읽지 못했습니다."); has(out, c[2]); has(out, "파일을 고친 뒤 다시 실행하세요"); hasNot(out, "학번 입력"); hasNot(out, "[저장]"); eq(before, fileState(dir), "파일"); ok(Arrays.equals(courses, Files.readAllBytes(dir.resolve("courses.csv"))), "courses.csv"); });
        t("UTF-8이 아닌 courses.csv: 인코딩 안내", () -> { Path dir = dirOf("start-euc"); Files.write(dir.resolve("courses.csv"), (HEAD_C + "\n" + ROW + "\n").getBytes(Charset.forName("EUC-KR"))); String out = console(dir, "1", "0"); has(out, "UTF-8"); hasNot(out, "학번 입력"); });
        t("입력이 전혀 없으면 (바로 EOF) 안내하고 끝나며 저장하지 않는다", () -> { Path dir = world("start-eof"); String before = fileState(dir); String out = console(dir); has(out, "[안내] 학번을 입력하지 않아 종료합니다."); hasNot(out, "[저장]"); eq(before, fileState(dir), "파일"); });
        t("학번 자리에 빈 줄이면 안내하고 끝난다", () -> { Path dir = world("start-blank"); String out = console(dir, "", "101", "0"); has(out, "[안내] 학번을 입력하지 않아 종료합니다."); hasNot(out, "메뉴"); });
        t("과목이 하나도 없는 courses.csv (헤더만): 모든 메뉴가 죽지 않고 안내한다", () -> { Path dir = dirOf("start-nocourse"); put(dir, "courses.csv", HEAD_C);
            String out = console(dir, "1", "가", "1", "x", "", "2", "10001001", "저장", "", "3", "4", "5", "9", "", "0"); has(out, "과목 0개"); has(out, "[과목 목록] 총 0개"); has(out, "[안내] 'x'에 맞는 과목이 없습니다."); has(out, "[거절] 10001001");
            has(out, "[안내] 시간표가 비어 있습니다"); has(out, "[안내] 등록된 교환 요청이 없습니다."); has(out, "[안내] 대기 중인 내 교환 요청이 없습니다."); endsCleanly(out); W w = load(dir); eq(1, w.registry.size(), "학생"); eq(0, w.board.size(), "요청"); });
        t("students.csv·requests.csv 없이 시작하면 종료할 때 만들어진다", () -> { Path dir = writeCourses("start-fresh"); String out = console(dir, "1", "가", "2", M1, "", "0"); has(out, "학생 0명"); has(out, "교환 요청 0건"); W w = load(dir); eq(Collections.singletonList(M1), w.codes("1"), "시간표"); ok(Files.exists(dir.resolve("requests.csv")), "requests.csv"); });
        t("저장할 수 없으면 (읽기 전용 파일) 오류를 알리고 정상 종료한다", () -> { Path dir = world("start-ro"); java.io.File f = dir.resolve("students.csv").toFile(); String before = fileState(dir);
            if (!f.setWritable(false) || f.canWrite()) return; try { String out = console(dir, "100", "2", M1, "", "0"); has(out, "[오류] 파일에 저장하지 못했습니다."); has(out, "쓸 권한"); endsCleanly(out); eq(before, fileState(dir), "파일"); } finally { f.setWritable(true); } });
        t("사용자 전환 때 저장에 실패해도 계속 쓸 수 있다", () -> { Path dir = world("start-ro2"); java.io.File f = dir.resolve("requests.csv").toFile();
            if (!f.setWritable(false) || f.canWrite()) return; try { String out = console(dir, "101", "9", "201", "4", "0"); has(out, "[오류] 파일에 저장하지 못했습니다."); has(out, "[교환 요청 목록]"); endsCleanly(out); } finally { f.setWritable(true); } });
        t("윈도우 줄바꿈(CRLF) 입력", () -> { Path dir = world("start-crlf"); String out = consoleRaw(dir, "101\r\n4\r\n0\r\n".getBytes(StandardCharsets.UTF_8)); has(out, "[확인] 학생101 님"); has(out, "[교환 요청 목록]"); hasNot(out, "[오류]"); sane(dir, out); });
        t("마지막 줄에 줄바꿈이 없는 입력", () -> { Path dir = world("start-nonl"); String out = consoleRaw(dir, "101\n0".getBytes(StandardCharsets.UTF_8)); hasNot(out, "[오류]"); sane(dir, out); });
        t("입력 맨 앞에 BOM이 붙어도 죽지 않는다", () -> { Path dir = world("start-bom"); String out = consoleRaw(dir, "﻿101\n101\n0\n".getBytes(StandardCharsets.UTF_8)); sane(dir, out); has(out, "[확인] 학생101 님"); });
        t("깨진 바이트(잘못된 UTF-8)가 섞인 입력에도 죽지 않는다", () -> { Path dir = world("start-bytes"); byte[] in = {'1', '0', '1', '\n', (byte) 0xFF, (byte) 0xFE, (byte) 0xC3, '\n', '2', '\n', (byte) 0x80, (byte) 0xED, (byte) 0xA0, '\n', '\n', '0', '\n'}; sane(dir, consoleRaw(dir, in)); });
        t("NUL·제어 문자가 섞인 입력에도 죽지 않는다", () -> { Path dir = world("start-ctl"); String before = snap(dir); String out = console(dir, "101", "\u0000\u0001\u0002", "2", "\u0007\u001B[31m", "\u0000", "", "3", "\u007F", "", "0"); sane(dir, out); eq(before, snap(dir), "상태"); });
        t("아주 긴 한 줄(100만 자) 입력에도 죽지 않는다", () -> { Path dir = world("start-long"); String before = snap(dir); String out = console(dir, "101", "9".repeat(1_000_000), "2", "1".repeat(1_000_000), "", "0"); sane(dir, out); eq(before, snap(dir), "상태"); });
        t("같은 폴더로 두 번 실행해도 결과가 같다 (저장 -> 복원 -> 저장)", () -> { Path dir = world("start-twice"); console(dir, "101", "0"); String first = fileState(dir); console(dir, "101", "0"); eq(first, fileState(dir), "파일"); });
        t("배너와 복원 안내는 ASCII와 한글·한자만", () -> { Path dir = world("start-banner"); for (char ch : console(dir, "101", "0").toCharArray()) ok(ch < 128 || (ch >= '가' && ch <= '힣') || ch == '求', "허용되지 않는 문자 U+" + Integer.toHexString(ch)); });
        done();
    }

    static void consoleMenu() {
        sec("18. 콘솔: 메뉴 입력 (ConsoleUI.run)");
        String[] junk = {"abc", "-1", "6", "7", "8", "10", "99", "1.0", "1 2", "y", "n", "", "   ", "+", "-", "--1", "1-", "0x0", "1e0", "2147483648", "-2147483649", "9".repeat(30), "저장", "메뉴",
            "１２", "!@#$", "';--", "%s%n", "\\", "null", "\u0000", "😀", "9".repeat(10000), "선택", "ㅁ", "1번", "#1", "1,2", "1;2", "0.0", "-0.5", "O", "l", "０x", "２２", "3 3", "①", "一", "종료", "exit", "quit", "q", "5!", "/0", "0/", "९९"};
        for (String j : junk) t("메뉴에 '" + show(j) + "' -> [오류]를 내고 다시 묻는다, 상태 그대로", () -> { Path dir = world("menu-" + (++fileNo)); String before = snap(dir); String out = console(dir, "101", j, "0");
            has(out, "[오류]"); eq(2, out.split("선택 > ", -1).length - 1, "메뉴를 두 번 물어야 함"); sane(dir, out); eq(before, snap(dir), "상태"); });
        String[] exits = {"0", " 0 ", "00", "+0", "-0", "０", "\t0", "0\t"};
        for (String e : exits) t("메뉴에 '" + show(e) + "' -> 저장하고 종료", () -> { Path dir = world("exit-" + (++fileNo)); String out = console(dir, "101", e, "4"); hasNot(out, "[오류]"); hasNot(out, "[교환 요청 목록]"); has(out, "[저장] students.csv"); has(out, "[저장] requests.csv"); sane(dir, out); });
        String[] fours = {"4", "04", " 4 ", "４", "+4", "004"};
        for (String f : fours) t("메뉴에 '" + show(f) + "' -> 4번 (요청 목록)", () -> { Path dir = world("four-" + (++fileNo)); String out = console(dir, "101", f, "0"); has(out, "[교환 요청 목록] 총 7건 (대기 7)"); hasNot(out, "[오류]"); sane(dir, out); });
        for (String id : IDS) for (String menu : new String[]{"1", "2", "3", "4", "5", "9"}) t("학생 " + id + ": 메뉴 " + menu + " 에 들어갔다가 빈 줄로 나오면 아무것도 안 바뀐다", () -> { Path dir = world("back-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, id, menu, "", "", "0"); sane(dir, out); eq(before, snap(dir), "상태"); hasNot(out, "[등록]"); });
        t("엉뚱한 메뉴 입력 500번 뒤에도 정상 종료", () -> { Path dir = world("menu-many"); String before = snap(dir); String[] lines = new String[502]; lines[0] = "101"; for (int i = 1; i <= 500; i++) lines[i] = junk[i % junk.length]; lines[501] = "0";
            String out = console(dir, lines); eq(500, out.split("\\[오류\\]", -1).length - 1, "[오류] 횟수"); sane(dir, out); eq(before, snap(dir), "상태"); });
        for (int which = 0; which < 2; which++) { final int fw = which;
            t("메뉴 동작 안에서 예상 못 한 예외가 나도 프로그램이 죽지 않고 메뉴로 돌아간다 (" + (which == 0 ? "저장" : "로그인") + " 단계의 고장)", () -> { W w = new W(); Student me = w.stu("1", true, FR1);
                PrintStream original = System.out; ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try { System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
                    new kookyohwan.ui.ConsoleUI(new Scanner("9\n4\n9\n0\n"), me, w.catalog, w.registry, w.board, w.matcher,
                            () -> { if (fw == 1) throw new IllegalStateException("고장"); return Optional.empty(); }, s -> { if (fw == 0) throw new IllegalStateException("고장"); }).run();
                } finally { System.setOut(original); }
                String out = buffer.toString(StandardCharsets.UTF_8); eq(2, out.split("\\[오류\\] 처리하지 못한 문제가 생겨 메뉴로 돌아갑니다. \\(IllegalStateException\\)", -1).length - 1, "오류 안내 횟수"); has(out, "[안내] 등록된 교환 요청이 없습니다."); eq(4, out.split("선택 > ", -1).length - 1, "메뉴 횟수"); }); }
        t("메뉴는 매번 다시 보여 준다 (번호 7개가 고정)", () -> { Path dir = world("menu-show"); String out = console(dir, "101", "4", "abc", "0"); eq(3, out.split(" 1\\. 과목 목록 보기", -1).length - 1, "메뉴 횟수");
            seq(out, " 1. 과목 목록 보기", " 2. 내 시간표 보기 / 과목 추가", " 3. 교환 요청 등록", " 4. 교환 요청 목록 보기", " 5. 매칭 실행", " 9. 사용자 전환", " 0. 저장하고 종료"); });
        t("메뉴 1: 검색 결과가 30개를 넘으면 30줄만 보여 주고 나머지 개수를 알린다", () -> { Path dir = dirOf("menu-30"); Files.write(dir.resolve("courses.csv"), (HEAD_C + "\n" + manyRows(45)).getBytes(StandardCharsets.UTF_8));
            String out = console(dir, "1", "가", "1", "과목", "", "0"); has(out, "[확인] '과목' 검색 결과 45개"); has(out, "... 외 15개는 생략했습니다."); eq(30, out.split("\n 5\\d{7} ", -1).length - 1, "표 줄 수"); });
        String[] keys = {"프랑스어", "20001", "교수", "java", "JAVA", "쉼표,과목", " 생명 ", "온라인"};
        int[] counts = {2, 2, 33, 1, 1, 1, 4, 3};
        for (int i = 0; i < keys.length; i++) { final int at = i; t("메뉴 1: 검색어 '" + keys[i] + "' -> " + counts[i] + "개", () -> { Path dir = world("search-" + (++fileNo)); String out = console(dir, "101", "1", keys[at], "", "0"); has(out, "[확인] '" + keys[at].trim() + "' 검색 결과 " + counts[at] + "개"); sane(dir, out); }); }
        String[] none = {"없는과목", ".*", "%", "\\", "'", "\"", "(", "[", "9", "00000000", "😀"};
        for (String k : none) t("메뉴 1: 검색어 '" + show(k) + "' -> 맞는 과목 없음 안내 후 다시 묻는다", () -> { Path dir = world("search-" + (++fileNo)); String before = snap(dir); String out = console(dir, "101", "1", k, "프랑스어", "", "0");
            has(out, "에 맞는 과목이 없습니다."); has(out, "[확인] '프랑스어' 검색 결과 2개"); sane(dir, out); eq(before, snap(dir), "상태"); });
        done();
    }

    static void consoleLogin() {
        sec("19. 콘솔: 학번·이름 입력과 사용자 전환 (Main.login)");
        String[] junk = {"abc", "12a", "-1", "1.5", "１０１", "2026 12345", "#101", "101;", "0x65", "+101", "101.", "一〇一", "ㅁㄴㅇ", "😀", "'101'", "\"101\"", "101,102", "1 0 1", "10 1", "-", "--", "학번", "null", "０", "101번", "id=101", "１０１"};
        for (String j : junk) t("학번에 '" + show(j) + "' -> [오류] 후 다시 묻는다", () -> { Path dir = world("login-" + (++fileNo)); String before = snap(dir); String out = console(dir, j, "101", "0");
            seq(out, "[오류] 학번은 숫자로 입력하세요.", "[확인] 학생101 님의 시간표를 불러왔습니다. (3과목)"); sane(dir, out); eq(before, snap(dir), "상태"); });
        int[] sizes = {0, 3, 1, 1, 2, 6, 2, 0, 3};
        for (int i = 0; i < IDS.length; i++) { final int at = i; t("학번 " + IDS[i] + " 로그인 -> 시간표 " + sizes[i] + "과목", () -> { Path dir = world("login-" + (++fileNo)); String out = console(dir, IDS[at], "0");
            has(out, "님의 시간표를 불러왔습니다. (" + sizes[at] + "과목)"); has(out, "(내 시간표 " + sizes[at] + "과목)"); sane(dir, out); }); }
        t("학번 앞뒤 공백은 무시", () -> { Path dir = world("login-sp"); has(console(dir, "  101\t", "0"), "[확인] 학생101 님"); });
        t("앞에 0이 붙은 학번은 다른 학번 (새 학생으로 묻는다)", () -> { Path dir = world("login-zero"); String out = console(dir, "0101", "", "101", "0"); seq(out, "[안내] 등록되지 않은 학번입니다.", "[취소] 학생을 등록하지 않았습니다.", "[확인] 학생101 님"); eq(9, load(dir).registry.size(), "학생 수"); });
        String[] names = {"홍길동", "\"따옴표\"", "세미;콜론", "😀", "ㅋㅋ", "가".repeat(50), "저장", "0", "y", "n", "-", "a b", "O'Neil", "<b>", "\\", "=1+1", "CONFIRMED", "1", "학번", "\"", "a\"b", "José"};
        for (String name : names) t("새 학생 이름 '" + show(name) + "' -> 등록되고 저장·복원된다", () -> { Path dir = world("name-" + (++fileNo)); String out = console(dir, "999", name, "0");
            has(out, "[등록] " + name + " (999) 님을 등록했습니다."); sane(dir, out); W w = load(dir); eq(10, w.registry.size(), "학생 수"); eq(name, w.get("999").getName(), "이름"); ok(!w.get("999").isTimetableConfirmed(), "편집 중"); eq(0, w.get("999").getTimetable().size(), "빈 시간표"); });
        for (String name : new String[]{",", "a,b", ",,", "김,철수", "철수,"}) t("새 학생 이름 '" + name + "' (쉼표) -> [오류] 후 다시 묻는다", () -> { Path dir = world("name-" + (++fileNo)); String out = console(dir, "999", name, "홍길동", "0");
            seq(out, "[오류] 이름에는 쉼표(,)를 쓸 수 없습니다.", "[등록] 홍길동 (999)"); eq("홍길동", load(dir).get("999").getName(), "이름"); });
        t("새 학생 이름을 비우면 등록하지 않고 학번부터 다시 묻는다", () -> { Path dir = world("name-blank"); String before = snap(dir); String out = console(dir, "999", "   ", "101", "0"); seq(out, "[취소] 학생을 등록하지 않았습니다.", "학번 입력 > ", "[확인] 학생101 님"); eq(before, snap(dir), "상태"); });
        t("새 학생 이름 앞뒤 공백은 잘라 낸다", () -> { Path dir = world("name-trim"); console(dir, "999", "  홍 길동  ", "0"); eq("홍 길동", load(dir).get("999").getName(), "이름"); });
        t("아주 긴 학번(50자리)도 새 학생으로 등록·복원된다", () -> { Path dir = world("login-long"); String id = "1234567890".repeat(5); String out = console(dir, id, "긴학번", "0"); sane(dir, out); eq("긴학번", load(dir).get(id).getName(), "이름"); });
        t("새 학생 이름을 묻는 중에 입력이 끊기면 등록하지 않고 끝난다", () -> { Path dir = world("login-eof"); String before = fileState(dir); String out = console(dir, "999"); has(out, "[취소] 학생을 등록하지 않았습니다."); has(out, "[안내] 학번을 입력하지 않아 종료합니다."); eq(before, fileState(dir), "파일"); });
        t("새 학생이 아무것도 안 하고 종료해도 저장된다 (다음 실행에서 바로 로그인)", () -> { Path dir = world("login-new"); console(dir, "999", "홍길동", "0"); String out = console(dir, "999", "0"); has(out, "[확인] 홍길동 님의 시간표를 불러왔습니다. (0과목)"); hasNot(out, "이름 입력"); });
        t("메뉴 9: 빈 줄이면 사용자를 바꾸지 않는다", () -> { Path dir = world("sw-blank"); String out = console(dir, "101", "9", "", "4", "0"); seq(out, "[저장] students.csv", "[취소] 사용자를 바꾸지 않았습니다. (학생101)", "#1"); has(out, "<- 내 요청"); });
        t("메뉴 9: 전환 전에 저장하고, 전환 뒤에는 그 학생의 요청이 '내 요청'", () -> { Path dir = world("sw-ok"); String out = console(dir, "101", "9", "201", "4", "0"); seq(out, "[저장] students.csv", "[저장] requests.csv", "학번 입력 > ", "[확인] 학생201 님", "[교환 요청 목록]");
            String list = out.substring(out.indexOf("[교환 요청 목록]")); for (String l : list.split("\n")) if (l.contains("<- 내 요청")) ok(l.contains("학생201"), "남의 요청에 표시: " + l); has(list, "<- 내 요청"); });
        t("메뉴 9: 자기 학번으로 전환해도 문제없다", () -> { Path dir = world("sw-self"); String before = snap(dir); String out = console(dir, "101", "9", "101", "9", "101", "0"); sane(dir, out); eq(before, snap(dir), "상태"); });
        t("메뉴 9: 엉뚱한 학번은 [오류] 후 다시 묻고, 빈 줄이면 원래 사용자", () -> { Path dir = world("sw-junk"); String out = console(dir, "101", "9", "abc", "-5", "", "5", "0"); seq(out, "[오류] 학번은 숫자로", "[오류] 학번은 숫자로", "[취소] 사용자를 바꾸지 않았습니다.", "[매칭 실행] 내 요청 #1"); });
        t("메뉴 9: 새 학번으로 전환하면 새 학생이 등록된다", () -> { Path dir = world("sw-new"); String out = console(dir, "101", "9", "999", "새학생", "2", M2, "", "0"); sane(dir, out); W w = load(dir); eq(Collections.singletonList(M2), w.codes("999"), "새 학생 시간표"); eq(Arrays.asList(M1, M3, FR1), w.codes("101"), "원래 학생 시간표"); });
        t("메뉴 9: 전환한 학생의 변경이 앞 학생에게 섞이지 않는다", () -> { Path dir = world("sw-mix"); String out = console(dir, "100", "2", M1, "", "9", "300", "2", M2, "", "9", "100", "2", FR1, "", "0"); sane(dir, out); W w = load(dir); eq(Arrays.asList(M1, FR1), w.codes("100"), "100"); eq(Collections.singletonList(M2), w.codes("300"), "300"); });
        t("메뉴 9: 30번 연달아 전환해도 상태가 그대로", () -> { Path dir = world("sw-many"); String before = snap(dir); List<String> lines = new ArrayList<>(); lines.add("101"); for (int i = 0; i < 30; i++) { lines.add("9"); lines.add(IDS[i % IDS.length]); } lines.add("0");
            String out = console(dir, lines.toArray(new String[0])); sane(dir, out); eq(before, snap(dir), "상태"); eq(31, out.split("\\[저장\\] students.csv", -1).length - 1, "저장 횟수"); });
        done();
    }

    static void consoleTimetable() throws IOException {
        sec("20. 콘솔: 시간표 보기·추가·교체·빼기·확정 (메뉴 2)");
        String[] junk = {"abc", "--", "-abc", "-99999999", "99999999", "1000100", "100010011", "10001001 x", "+10001001", "１０００１００１", "10001001;20001001", "10001001,20001001", "전공A", "프랑스어", "-전공A",
            "y", "n", "0", "9", "#", "'", "-", " - ", "--10001001", "저장 ", "save", "확정", "ㅈㅓㅈㅏㅇ", "10001001\t10002001", "-0", "- -", "-저장", "삭제 10001001", "10001001-", "1e7"};
        for (String id : new String[]{"100", "101"}) for (String j : junk) t("학생 " + id + ": 학수번호 자리에 '" + show(j) + "' -> 거절·오류·안내, 시간표 그대로", () -> { Path dir = world("tt-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, id, "2", j, "", "0"); ok(out.contains("[거절]") || out.contains("[오류]") || out.contains("[안내]"), "아무 안내도 없음: " + clip(out)); hasNot(out, "[등록]"); hasNot(out, "[거절] :"); sane(dir, out); eq(before, snap(dir), "상태"); });
        t("'-'만 넣으면 뺄 학수번호를 넣으라는 [오류]", () -> { Path dir = world("tt-dash"); String out = console(dir, "101", "2", "-", "", "0"); has(out, "[오류]"); hasNot(out, "[거절] :"); });
        t("이미 확정한 학생이 '저장'을 넣으면 이미 확정했다는 [안내]", () -> { Path dir = world("tt-save2"); String out = console(dir, "101", "2", "저장", "", "0"); has(out, "[안내]"); has(out, "확정"); hasNot(out, "[거절] 저장"); });
        t("빈 시간표에서 '저장' -> 비어 있다는 [안내], 편집 중 그대로", () -> { Path dir = world("tt-save0"); String out = console(dir, "100", "2", "저장", "", "0"); has(out, "[안내] 시간표가 비어 있습니다."); ok(!load(dir).get("100").isTimetableConfirmed(), "확정됨"); });
        W ref = new W();
        for (Course c : ref.courses) t("편집 중인 빈 시간표에 " + c.getCode() + " " + c.getName() + " 추가 (정원과 상관없이)", () -> { Path dir = world("add-" + (++fileNo)); String out = console(dir, "100", "2", c.getCode(), "", "0");
            has(out, "[등록] " + c.getCode() + " " + c.getName() + " " + c.getSection()); has(out, "1과목 " + c.getCredits() + "학점"); sane(dir, out); eq(Collections.singletonList(c.getCode()), load(dir).codes("100"), "저장된 시간표"); });
        for (Course c : ref.courses) { boolean full = c.getCapacity() > 0 && c.getBaseEnrolled() >= c.getCapacity();
            t("확정한 빈 시간표에 " + c.getCode() + " " + c.getName() + " 수강신청 -> " + (full ? "마감이라 거절" : "들어감"), () -> { Path dir = world("enr-" + (++fileNo)); String out = console(dir, "300", "2", c.getCode(), "", "0"); sane(dir, out);
                if (full) { has(out, "[거절] " + c.getName() + " " + c.getSection() + ": 정원이 마감되었습니다"); eq(Collections.emptyList(), load(dir).codes("300"), "시간표"); } else { has(out, "[등록] " + c.getCode()); eq(Collections.singletonList(c.getCode()), load(dir).codes("300"), "시간표"); } }); }
        for (Course c : ref.courses) t("18학점을 채운 학생(205)에게 " + c.getCode() + " " + c.getName() + " -> 묻더라도 n이면 그대로, 학점을 넘기지 않는다", () -> { Path dir = world("full-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, "205", "2", c.getCode(), "n", "", "0"); sane(dir, out); eq(before, snap(dir), "상태"); hasNot(out, "[등록]"); ok(out.contains("[거절]") || out.contains("[취소]"), "거절도 취소도 없음"); });
        for (Course c : ref.courses) t("18학점을 채운 학생(205)에게 " + c.getCode() + " " + c.getName() + " -> y로 답해도 18학점 이하·겹침 없음", () -> { Path dir = world("fully-" + (++fileNo));
            String out = console(dir, "205", "2", c.getCode(), "y", "", "0"); sane(dir, out); ok(load(dir).get("205").getTimetable().getTotalCredits() <= 18, "학점 초과"); });
        String[][] yn = {{"y", "y"}, {"Y", "y"}, {" y ", "y"}, {"ㅛ", "y"}, {"n", "n"}, {"N", "n"}, {"", "n"}, {"ㅜ", "n"}, {" n ", "n"}, {"yes", "?"}, {"no", "?"}, {"예", "?"}, {"아니오", "?"}, {"1", "?"}, {"0", "?"}, {"yy", "?"}, {"ㅇ", "?"}, {"y n", "?"}, {"Yes", "?"}, {"ok", "?"}, {"ｙ", "?"}};
        for (String[] a : yn) t("교체 확인에 '" + show(a[0]) + "' -> " + (a[1].equals("y") ? "교체" : a[1].equals("n") ? "취소" : "[오류] 후 다시 묻는다"), () -> { Path dir = world("yn-" + (++fileNo));
            String out = console(dir, "100", "2", M1, LF2, a[0], "n", "", "0"); sane(dir, out); has(out, "다음 과목과 시간이 겹칩니다.");
            if (a[1].equals("y")) { has(out, "[등록] 전공A 삭제, 생명 002 추가"); eq(Collections.singletonList(LF2), load(dir).codes("100"), "시간표"); }
            else { if (a[1].equals("?")) has(out, "[오류] y 또는 n을 입력하세요."); has(out, "[취소] 시간표를 변경하지 않았습니다."); eq(Collections.singletonList(M1), load(dir).codes("100"), "시간표"); } });
        for (String[] a : yn) t("빼기 확인에 '" + show(a[0]) + "' -> " + (a[1].equals("y") ? "삭제" : a[1].equals("n") ? "취소" : "[오류] 후 다시 묻는다"), () -> { Path dir = world("ynr-" + (++fileNo));
            String out = console(dir, "100", "2", M1, "-" + M1, a[0], "n", "", "0"); sane(dir, out);
            if (a[1].equals("y")) { has(out, "[등록] 전공A 001 삭제. 0과목 0학점"); eq(Collections.emptyList(), load(dir).codes("100"), "시간표"); }
            else { if (a[1].equals("?")) has(out, "[오류] y 또는 n을 입력하세요."); eq(Collections.singletonList(M1), load(dir).codes("100"), "시간표"); } });
        for (String[] a : yn) t("시간표 확정 확인에 '" + show(a[0]) + "' -> " + (a[1].equals("y") ? "확정" : a[1].equals("n") ? "취소" : "[오류] 후 다시 묻는다"), () -> { Path dir = world("ync-" + (++fileNo));
            String out = console(dir, "100", "2", M1, "저장", a[0], "n", "", "0"); sane(dir, out); eq(a[1].equals("y"), load(dir).get("100").isTimetableConfirmed(), "확정 여부");
            if (a[1].equals("y")) has(out, "[등록] 시간표 확정. 1과목 3학점"); else has(out, "[취소] 시간표를 확정하지 않았습니다."); });
        t("같은 분반을 두 번 넣으면 거절", () -> { Path dir = world("tt-twice"); String out = console(dir, "100", "2", M1, M1, "", "0"); has(out, "[거절] 전공A 001: 이미 시간표에 있습니다."); eq(Collections.singletonList(M1), load(dir).codes("100"), "시간표"); });
        t("같은 과목의 다른 분반은 거절 (시간이 안 겹쳐도)", () -> { Path dir = world("tt-sec"); String out = console(dir, "100", "2", FR1, FR2, ON1, ON2, "", "0"); has(out, "[거절] 프랑스어 002: 같은 과목의 001 분반을 이미 듣고 있습니다."); has(out, "[거절] 온라인교양 002: 같은 과목의 001 분반"); eq(Arrays.asList(FR1, ON1), load(dir).codes("100"), "시간표"); });
        t("같은 과목의 다른 분반이 시간까지 겹치면 교체로 바꿀 수 있다", () -> { Path dir = dirOf("tt-swapsec"); put(dir, "courses.csv", HEAD_C, "20001001,프랑스어,001,GENERAL,화5-6,교수,방,2,0,0", "20001003,프랑스어,003,GENERAL,화6-7,교수,방,2,0,0");
            String out = console(dir, "1", "가", "2", "20001001", "20001003", "y", "", "0"); has(out, "[등록] 프랑스어 삭제, 프랑스어 003 추가"); eq(Collections.singletonList("20001003"), load(dir).codes("1"), "시간표"); });
        t("내놓는 중인 과목과 겹치는 과목은 교체 거절 (요청 번호 안내)", () -> { Path dir = world("tt-giving"); String before = snap(dir); String out = console(dir, "101", "2", LF3, "", "0"); has(out, "[거절] 프랑스어: 요청 #1이 이 과목을 내놓는 중입니다. 먼저 요청을 취소하세요."); eq(before, snap(dir), "상태"); });
        t("내놓는 중인 과목은 빼기도 거절", () -> { Path dir = world("tt-giving2"); String before = snap(dir); String out = console(dir, "101", "2", "-" + FR1, "y", "", "0"); has(out, "[거절] 프랑스어: 요청 #1이 이 과목을 내놓는 중입니다."); eq(before, snap(dir), "상태"); });
        t("'- 학수번호'처럼 사이에 공백이 있어도 빼기", () -> { Path dir = world("tt-space"); String out = console(dir, "101", "2", "-  " + M3 + " ", "y", "", "0"); has(out, "[등록] 전공C 001 삭제."); eq(Arrays.asList(M1, FR1), load(dir).codes("101"), "시간표"); });
        t("시간표에 없는 과목 빼기는 거절", () -> { Path dir = world("tt-nope"); String out = console(dir, "101", "2", "-" + M2, "", "0"); has(out, "[거절] " + M2 + ": 내 시간표에 없는 학수번호입니다."); });
        t("18학점에서 1학점 추가 거절: 현재·넣은 뒤 학점을 보여 준다", () -> { Path dir = world("tt-18"); String out = console(dir, "205", "2", ONE, "", "0"); has(out, "[거절] 한학점 001: 최대 18학점을 넘습니다 (현재 18학점, 넣으면 19학점)."); has(out, "시간표를 변경하지 않았습니다."); });
        t("18학점에서 과목을 빼면 그만큼 다시 넣을 수 있다", () -> { Path dir = world("tt-18b"); String out = console(dir, "205", "2", "-" + JP1, "y", MOV, ONE, FR1, "", "0"); sane(dir, out); eq(18, load(dir).get("205").getTimetable().getTotalCredits(), "학점"); has(out, "[거절] 프랑스어 001: 최대 18학점을 넘습니다"); });
        t("확정 학생: 남은 한 자리를 다른 학생이 차지하면 마감으로 거절", () -> { Path dir = world("tt-seat"); String out = console(dir, "300", "2", CO, "", "9", "206", "2", CO, "", "0"); seq(out, "[등록] " + CO, "[거절] 마감교양 002: 정원이 마감되었습니다 (2명)"); W w = load(dir); ok(w.get("300").getTimetable().contains(CO), "300"); ok(!w.get("206").getTimetable().contains(CO), "206"); });
        t("확정 학생: 마감 과목을 뺐다가 다시 넣을 수 있다 (자기 자리가 빈 것)", () -> { Path dir = world("tt-reseat"); String out = console(dir, "300", "2", CO, "-" + CO, "y", CO, "", "0"); sane(dir, out); eq(Collections.singletonList(CO), load(dir).codes("300"), "시간표"); });
        t("마감 과목을 삭제하면 과목 목록의 빈자리가 1 늘고, 삭제 안내에도 나온다", () -> { Path dir = world("tt-freed"); String out = console(dir, "300", "2", CO, "", "1", "20009002", "", "2", "-" + CO, "y", "", "1", "20009002", "", "0");
            seq(out, "[등록] " + CO, "마감 (2/2)", "[등록] 마감교양 002 삭제. 0과목 0학점  (마감교양 002: 빈자리 1 (1/2))", "빈자리 1 (1/2)"); sane(dir, out); });
        t("내가 삭제해서 생긴 빈자리에 다른 확정 학생이 수강신청할 수 있고, 다시 실행해도 그대로다", () -> { Path dir = world("tt-freed2"); console(dir, "300", "2", CO, "", "0"); String full = console(dir, "206", "2", CO, "", "0"); has(full, "[거절] 마감교양 002: 정원이 마감되었습니다");
            console(dir, "300", "2", "-" + CO, "y", "", "0"); String out = console(dir, "206", "1", "20009002", "", "2", CO, "", "0"); seq(out, "빈자리 1 (1/2)", "[등록] " + CO); ok(load(dir).get("206").getTimetable().contains(CO), "206 시간표"); });
        t("정원 제한이 없는 과목을 삭제하면 빈자리 안내를 붙이지 않는다", () -> { Path dir = world("tt-nolimit"); String out = console(dir, "101", "2", "-" + M3, "y", "", "0"); has(out, "[등록] 전공C 001 삭제. 2과목 5학점\n"); });
        t("확정 뒤에는 안내 문구가 수강신청으로 바뀌고 '저장'이 통하지 않는다", () -> { Path dir = world("tt-after"); String out = console(dir, "100", "2", M1, "저장", "y", CF, "", "0"); seq(out, "넣을 학수번호", "[등록] 시간표 확정. 1과목 3학점", "수강신청할 학수번호", "[거절] 마감교양 001: 정원이 마감되었습니다"); eq(Collections.singletonList(M1), load(dir).codes("100"), "시간표"); });
        t("편집 중 표시는 확정 전에만", () -> { Path dir = world("tt-mark"); has(console(dir, "100", "2", "", "0"), "[편집 중 - 확정 전]"); hasNot(console(dir, "101", "2", "", "0"), "[편집 중"); });
        t("주말 과목을 넣으면 격자에 토·일 열이 생긴다", () -> { Path dir = world("tt-weekend"); String out = console(dir, "100", "2", SAT, "", "0"); String last = out.substring(out.lastIndexOf("[내 시간표]")); has(last, "토"); has(last, "|주말교양      |"); });
        t("시간표를 여러 번 고친 뒤 격자의 모든 줄 폭이 같다", () -> { Path dir = world("tt-shape"); String out = console(dir, "100", "2", M1, FR1, COMMA, JAVA, ONE, "", "0"); String last = out.substring(out.lastIndexOf("[내 시간표]")); String[] lines = last.split("\n"); int width = TextUtil.displayWidth(lines[1]);
            for (int i = 1; i < lines.length; i++) { if (lines[i].startsWith(" 전공:")) break; eq(width, TextUtil.displayWidth(lines[i]), "줄 폭 [" + lines[i] + "]"); } });
        done();
    }

    static void consoleRequests() {
        sec("21. 콘솔: 요청 등록·목록·매칭 (메뉴 3·4·5)");
        for (String j : new String[]{"0", "4", "-1", "abc", "1.5", "99999999999", "#3", "3번", "y", "FR1", "20001001", "세 번째", "3 3", "-"}) t("내놓을 과목 번호에 '" + j + "' -> [오류] 후 다시 묻는다", () -> { Path dir = world("give-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, "101", "3", j, "", "0"); has(out, "[오류]"); sane(dir, out); eq(before, snap(dir), "상태"); });
        for (String[] g : new String[][]{{"1", "전공A"}, {"2", "전공C"}, {"01", "전공A"}, {" 2 ", "전공C"}}) t("내놓을 과목 번호 '" + g[0] + "' (" + g[1] + ") -> 전공이라 거절 후 다시 묻는다", () -> { Path dir = world("give-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, "101", "3", g[0], "", "0"); has(out, "[거절] " + g[1] + ": 전공 과목은 교환 요청을 등록할 수 없습니다."); has(out, "요청 목록을 변경하지 않았습니다."); eq(2, out.split("내놓을 과목 번호 > ", -1).length - 1, "다시 물음"); eq(before, snap(dir), "상태"); });
        String[][] badWants = {{"없는과목", "과목 목록에 없습니다"}, {"전공B", "교양 과목만 교환할 수 있습니다"}, {"프랑스어", "내놓을 과목과 희망 과목이 같습니다"}, {"20001001", "내놓을 과목과 희망 과목이 같습니다"}, {"20001002", "내놓을 과목과 희망 과목이 같습니다"},
            {"30001001", "교양 과목만 교환할 수 있습니다"}, {"10002001", "교양 과목만 교환할 수 있습니다"}, {"생명", "같은 내용의 대기 요청이 이미 있습니다 (#1)"}, {"20002003", "같은 내용의 대기 요청이 이미 있습니다 (#1)"}, {"99999999", "과목 목록에 없습니다"},
            {"생명 과", "과목 목록에 없습니다"}, {"java programming", "과목 목록에 없습니다"}, {"y", "과목 목록에 없습니다"}, {"0", "과목 목록에 없습니다"}, {"-", "과목 목록에 없습니다"}, {"전공A", "교양 과목만 교환할 수 있습니다"}, {"군사학", "교양 과목만 교환할 수 있습니다"}, {"생명,일본어", "과목 목록에 없습니다"}};
        for (String[] b : badWants) t("희망 과목에 '" + b[0] + "' -> 거절 (" + b[1] + ") 후 다시 묻는다", () -> { Path dir = world("want-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, "101", "3", "3", b[0], "", "0"); has(out, "[거절]"); has(out, b[1]); eq(2, out.split("희망 과목명 \\(분반 무관, 학수번호도 가능\\) > ", -1).length - 1, "다시 물음"); sane(dir, out); eq(before, snap(dir), "상태"); });
        String[][] okWants = {{"일본어", "일본어"}, {"20003002", "일본어"}, {"영화", "영화"}, {"온라인교양", "온라인교양"}, {"Java Programming", "Java Programming"}, {"20017001", "Java Programming"}, {"쉼표,과목", "쉼표,과목"}, {"20015001", "쉼표,과목"}, {"마감교양", "마감교양"}, {"꽉찬교양", "꽉찬교양"},
            {"한학점", "한학점"}, {"데이터베이스", "데이터베이스"}, {"20010001", "데이터베이스"}, {"10010001", "데이터베이스"}, {"주말교양", "주말교양"}, {"큰교양", "큰교양"}, {"다중", "다중"}, {"인접", "인접"}, {"넓은", "넓은"}, {"온라인둘", "온라인둘"}, {"  영화  ", "영화"}};
        for (String[] w : okWants) t("희망 과목에 '" + w[0] + "' -> 요청 #8 등록 (" + w[1] + "), 저장·복원", () -> { Path dir = world("wantok-" + (++fileNo)); String out = console(dir, "101", "3", "3", w[0], "y", "0");
            if (!w[0].trim().equals(w[1])) has(out, "[확인] " + w[0] + " -> " + w[1]); has(out, "[등록] 요청 #8 등록 완료  상태: 대기"); sane(dir, out); W x = load(dir); SwapRequest r = x.board.find(8).get(); eq(w[1], r.getWantName(), "희망"); eq("101", r.getStudentId(), "학번"); eq(FR1, r.getGiveCourse().getCode(), "내놓는 과목"); eq(8, x.board.size(), "요청 수"); eq(Arrays.asList(M1, M3, FR1), x.codes("101"), "시간표는 그대로"); });
        for (String[] a : new String[][]{{"y", "y"}, {"Y", "y"}, {"ㅛ", "y"}, {"n", "n"}, {"", "n"}, {"ㅜ", "n"}, {"maybe", "?"}, {"등록", "?"}}) t("등록 확인에 '" + a[0] + "' -> " + (a[1].equals("y") ? "등록" : a[1].equals("n") ? "취소" : "[오류] 후 다시 묻는다"), () -> { Path dir = world("regyn-" + (++fileNo));
            String out = console(dir, "101", "3", "3", "영화", a[0], "n", "0"); sane(dir, out); eq(a[1].equals("y") ? 8 : 7, load(dir).board.size(), "요청 수"); if (!a[1].equals("y")) has(out, "[취소] 요청을 등록하지 않았습니다."); if (a[1].equals("?")) has(out, "[오류] y 또는 n을 입력하세요."); });
        t("빈 시간표 학생: 요청 등록 대신 안내", () -> { Path dir = world("req-empty"); has(console(dir, "100", "3", "0"), "[안내] 시간표가 비어 있습니다."); has(console(dir, "300", "3", "0"), "[안내] 시간표가 비어 있습니다."); });
        t("확정 전 학생: 요청 등록 대신 확정하라는 안내", () -> { Path dir = world("req-edit"); String out = console(dir, "100", "2", FR1, "", "3", "0"); has(out, "[안내] 시간표를 확정한 뒤에 교환 요청을 등록할 수 있습니다."); eq(7, load(dir).board.size(), "요청 수"); });
        t("확정하자마자 요청을 등록하고 바로 매칭할 수 있다", () -> { Path dir = world("req-flow"); String out = console(dir, "100", "2", FR1, "저장", "y", "", "3", "1", "생명", "y", "5", "0"); seq(out, "[등록] 시간표 확정", "[등록] 요청 #8 등록 완료", "[매칭 실행] 내 요청 #8", "[가능]", "[결과] 교환 가능한 상대"); sane(dir, out); });
        t("같은 요청을 한 번 더 등록하려 하면 번호와 함께 거절", () -> { Path dir = world("req-dup"); String out = console(dir, "101", "3", "3", "영화", "y", "3", "3", "영화", "", "0"); has(out, "[거절] 프랑스어 -> 영화: 같은 내용의 대기 요청이 이미 있습니다 (#8)."); eq(8, load(dir).board.size(), "요청 수"); });
        t("요청 100건을 연달아 등록해도 번호가 이어지고 목록이 나온다", () -> { Path dir = dirOf("req-100"); StringBuilder cs = new StringBuilder(HEAD_C + "\n20001001,프랑스어,001,GENERAL,화5-6,교수,방,2,0,0\n"); for (int i = 1; i <= 100; i++) cs.append(String.format("6%04d001,교양%d,001,GENERAL,수1,교수,방,2,0,0\n", i, i));
            Files.write(dir.resolve("courses.csv"), cs.toString().getBytes(StandardCharsets.UTF_8)); List<String> in = new ArrayList<>(Arrays.asList("1", "가", "2", "20001001", "저장", "y", "")); for (int i = 1; i <= 100; i++) in.addAll(Arrays.asList("3", "1", "교양" + i, "y")); in.addAll(Arrays.asList("4", "5", "100", "0"));
            String out = console(dir, in.toArray(new String[0])); has(out, "[등록] 요청 #100 등록 완료"); has(out, "[교환 요청 목록] 총 100건 (대기 100)"); has(out, "[매칭 실행] 내 요청 #100"); W w = load(dir); eq(100, w.board.size(), "요청 수"); eq(Collections.emptyList(), violations(w), "위반"); });
        for (String j : new String[]{"0", "1", "4", "8", "99", "abc", "#", "##5", "5.0", "-5", "５５", "5 6", "5,6", "y"}) t("매칭할 요청 번호에 '" + j + "' -> [오류] 후 다시 묻는다", () -> { Path dir = world("mno-" + (++fileNo)); String before = snap(dir);
            String out = console(dir, "207", "5", j, "", "0"); has(out, "[오류]"); hasNot(out, "[매칭 실행]"); sane(dir, out); eq(before, snap(dir), "상태"); });
        for (String f : new String[]{"5", "#5", " 5 ", "05", "#05", "５", "+5"}) t("매칭할 요청 번호 '" + f + "' -> 요청 #5", () -> { Path dir = world("mok-" + (++fileNo)); has(console(dir, "207", "5", f, "0"), "[매칭 실행] 내 요청 #5: 일본어 (목 5-6) -> 생명"); });
        for (int id : new int[]{1, 2, 3, 4, 5, 6, 7}) t("요청 #" + id + " 매칭: 화면의 가능·제외 수가 MatchService 결과와 같고, 상태를 바꾸지 않는다", () -> { Path dir = world("mcount-" + (++fileNo)); W w = load(dir); SwapRequest mine = w.board.find(id).get(); String before = w.snapshot();
            int possible = 0, excluded = 0; for (MatchResult r : w.match(mine)) { if (r.isPossible()) possible++; else excluded++; }
            boolean many = w.board.waitingOf(mine.getStudentId()).size() > 1; String out = many ? console(dir, mine.getStudentId(), "5", String.valueOf(id), "0") : console(dir, mine.getStudentId(), "5", "0");
            has(out, "[매칭 실행] 내 요청 #" + id); eq(possible, out.split("\\[가능\\]", -1).length - 1, "[가능] 줄 수"); eq(excluded, out.split("\\[제외\\]", -1).length - 1, "[제외] 줄 수");
            if (possible + excluded == 0) has(out, "[결과] 조건에 맞는 상대가 없습니다."); else has(out, "[결과] 교환 가능한 상대 " + possible + "명"); eq(before, snap(dir), "상태"); });
        t("매칭 결과 요약: 후보 수와 조건 불일치 수의 합이 다른 대기 요청 수", () -> { Path dir = world("msum"); String out = console(dir, "101", "5", "0"); has(out, "대기 중인 다른 요청 6건 중 조건이 맞는 후보 3건 (조건 불일치 3건은 후보 아님)"); has(out, "[결과] 교환 가능한 상대 1명"); });
        t("요청 목록: 요청이 없으면 안내", () -> { Path dir = writeCourses("list-none"); has(console(dir, "1", "가", "4", "0"), "[안내] 등록된 교환 요청이 없습니다."); });
        t("요청 목록: 학생 목록에 없는 요청자는 학번으로 표시", () -> { Path dir = world("list-unknown"); Files.write(dir.resolve("requests.csv"), (HEAD_R + "\n1,555,20002001,프랑스어,WAITING\n").getBytes(StandardCharsets.UTF_8)); String out = console(dir, "101", "4", "0"); has(out, " #1    555 "); });
        t("요청 목록: 취소·매칭완료도 상태와 함께 보인다", () -> { Path dir = world("list-states"); Files.write(dir.resolve("requests.csv"), (HEAD_R + "\n1,101,20001001,생명,CANCELLED\n2,201,20002001,프랑스어,MATCHED\n3,101,20001001,영화,WAITING\n").getBytes(StandardCharsets.UTF_8));
            String out = console(dir, "101", "4", "5", "0"); has(out, "[교환 요청 목록] 총 3건 (대기 1)"); has(out, "취소"); has(out, "매칭완료"); has(out, "[매칭 실행] 내 요청 #3"); });
        t("매칭: 상대 시간표가 없으면 '상대 시간표 미확인'이 화면에 나온다", () -> { Path dir = world("m-unknown"); Files.write(dir.resolve("requests.csv"), (HEAD_R + "\n1,101,20001001,생명,WAITING\n2,555,20002001,프랑스어,WAITING\n").getBytes(StandardCharsets.UTF_8));
            String out = console(dir, "101", "5", "0"); has(out, "[가능]"); has(out, "상대 시간표 미확인"); has(out, "1) 555  내 프랑스어 (화 5-6) <-> 555 생명 001 (수 5-6)"); });
        t("매칭: 내놓는 과목이 내 시간표에 없는 요청(파일을 손으로 고친 경우)도 죽지 않고 제외로 보인다", () -> { Path dir = world("m-missing"); Files.write(dir.resolve("requests.csv"), (HEAD_R + "\n1,101,20003001,생명,WAITING\n2,201,20002001,일본어,WAITING\n").getBytes(StandardCharsets.UTF_8));
            String out = console(dir, "101", "5", "0"); has(out, "[제외]"); has(out, "내놓는 과목이 내 시간표에 없음"); has(out, "[결과] 교환 가능한 상대 0명"); });
        done();
    }

    static void consoleEndOfInput() {
        sec("22. 콘솔: 입력이 도중에 끊길 때 (EOF)");
        String[][] scripts = {
            {"100", "2", M1, LF2, "y", FR1, "-" + FR1, "y", JP1, "저장", "y", CO, "", "3", "2", "영화", "y", "4", "5", "9", "101", "5", "1", "프랑스어", "", "0"},
            {"101", "3", "3", "일본어", "y", "5", "1", "2", LF2, "y", "-" + M3, "y", "", "9", "999", "새학생", "2", M2, "저장", "y", "", "3", "1", "", "4", "0"},
            {"207", "5", "6", "5", "#7", "3", "2", "생명", "3", "3", "영화", "y", "2", "-" + M2, "y", WIDE, "y", "", "9", "", "4", "0"},
        };
        for (int s = 0; s < scripts.length; s++) for (int k = 0; k <= scripts[s].length; k++) { final String[] script = scripts[s]; final int cut = k;
            t("시나리오 " + (s + 1) + " 을 " + k + "번째 줄까지만 입력하고 끊음 -> 죽지 않고, 저장된 파일이 다시 읽히고, 위반이 없다", () -> { Path dir = world("eof-" + (++fileNo)); String out = console(dir, Arrays.copyOf(script, cut)); sane(dir, out);
                if (cut > 0) { has(out, "[저장] students.csv"); has(out, "구교환을 종료합니다."); } String again = console(dir, script[0], "0"); sane(dir, again); has(again, "님의 시간표를 불러왔습니다."); }); }
        for (int s = 0; s < scripts.length; s++) { final String[] script = scripts[s]; t("시나리오 " + (s + 1) + " 의 마지막 줄이 줄바꿈 없이 끝나도 같은 결과", () -> { Path a = world("eofa-" + (++fileNo)), b = world("eofb-" + (++fileNo));
            console(a, script); consoleRaw(b, String.join("\n", script).getBytes(StandardCharsets.UTF_8)); eq(snap(a), snap(b), "상태"); }); }
        for (int s = 0; s < scripts.length; s++) { final String[] script = scripts[s]; t("시나리오 " + (s + 1) + " 를 윈도우 줄바꿈(CRLF)으로 넣어도 같은 결과", () -> { Path a = world("crlfa-" + (++fileNo)), b = world("crlfb-" + (++fileNo));
            console(a, script); consoleRaw(b, (String.join("\r\n", script) + "\r\n").getBytes(StandardCharsets.UTF_8)); eq(snap(a), snap(b), "상태"); }); }
        for (int s = 0; s < scripts.length; s++) { final String[] script = scripts[s]; t("시나리오 " + (s + 1) + " 의 모든 줄 앞뒤에 공백·탭을 붙여도 같은 결과", () -> { Path a = world("spa-" + (++fileNo)), b = world("spb-" + (++fileNo)); String[] padded = new String[script.length];
            for (int i = 0; i < script.length; i++) padded[i] = "  \t" + script[i] + " \t "; console(a, script); console(b, padded); eq(snap(a), snap(b), "상태"); }); }
        done();
    }

    static void consoleFuzz() throws IOException {
        sec("23. 콘솔: 무작위 입력");
        W ref = new W();
        List<String> pool = new ArrayList<>(Arrays.asList("1", "2", "2", "2", "3", "3", "4", "5", "5", "9", "y", "y", "y", "n", "", "", "저장", "ㅛ", "ㅜ", "abc", "-", "#1", "#5", "0x1", "99", "-1", "생명 ", "없는과목", "😀", "6", "7", "8", ",", "\"", ";"));
        for (Course c : ref.courses) { pool.add(c.getCode()); pool.add(c.getCode()); pool.add("-" + c.getCode()); pool.add(c.getName()); }
        for (int i = 1; i <= 9; i++) pool.add(String.valueOf(i));
        pool.addAll(Arrays.asList(IDS)); pool.add("999"); pool.add("새학생");
        int[] stats = {0, 0};
        for (int seed = 1; seed <= 150; seed++) { final int fseed = seed;
            t("무작위 입력 120여 줄 (seed " + seed + "): 죽지 않고, 저장된 파일이 다시 읽히고, 겹침·학점 초과·중복 수강·어긋난 요청이 없다", () -> { Random rnd = new Random(fseed); Path dir = world("fz-" + fseed); W before = load(dir);
                List<String> script = new ArrayList<>(); script.add(IDS[rnd.nextInt(IDS.length)]);
                while (script.size() < 120) { Course c = ref.courses.get(rnd.nextInt(ref.courses.size())); String yn = rnd.nextInt(4) == 0 ? "n" : "y";
                    switch (rnd.nextInt(10)) {
                        case 0 -> script.addAll(Arrays.asList("2", c.getCode(), yn, ""));
                        case 1 -> script.addAll(Arrays.asList("2", "-" + c.getCode(), yn, ""));
                        case 2 -> script.addAll(Arrays.asList("2", "저장", yn, ""));
                        case 3, 4 -> script.addAll(Arrays.asList("3", String.valueOf(1 + rnd.nextInt(5)), rnd.nextBoolean() ? c.getName() : c.getCode(), yn));
                        case 5 -> script.addAll(Arrays.asList("5", "#" + (1 + rnd.nextInt(12))));
                        case 6 -> script.addAll(rnd.nextInt(4) == 0 ? Arrays.asList("9", "999", "새학생") : Arrays.asList("9", IDS[rnd.nextInt(IDS.length)]));
                        default -> { for (int i = 0; i < 3; i++) script.add(pool.get(rnd.nextInt(pool.size()))); }
                    } }
                String[] lines = script.toArray(new String[0]); String out = console(dir, lines); sane(dir, out); W after = load(dir);
                for (Student s : before.registry.all()) { ok(after.registry.find(s.getId()).isPresent(), "학생 " + s.getId() + " 이 사라짐"); eq(s.getName(), after.get(s.getId()).getName(), "이름"); if (s.isTimetableConfirmed()) ok(after.get(s.getId()).isTimetableConfirmed(), "확정이 풀림 " + s.getId()); }
                for (SwapRequest r : before.board.all()) { SwapRequest now = after.board.find(r.getId()).orElseThrow(() -> new AssertionError("요청 #" + r.getId() + " 이 사라짐")); eq(r.getStudentId(), now.getStudentId(), "요청자"); eq(r.getGiveCourse().getCode(), now.getGiveCourse().getCode(), "내놓는 과목"); eq(r.getWantName(), now.getWantName(), "희망"); eq(r.getStatus(), now.getStatus(), "콘솔에는 상태를 바꾸는 기능이 없음 #" + r.getId()); }
                stats[0] += after.board.size() - before.board.size(); stats[1] += out.split("\\[등록\\]", -1).length - 1; String again = console(dir, lines[0], "0"); sane(dir, again); eq(after.snapshot(), snap(dir), "다시 실행해도 그대로"); }); }
        t("무작위 입력이 실제로 상태를 많이 바꿨다 (테스트가 약하지 않음)", () -> { ok(stats[0] > 100, "새 요청 " + stats[0] + "건"); ok(stats[1] > 500, "[등록] " + stats[1] + "회"); System.out.println("       새 요청 " + stats[0] + "건, [등록] " + stats[1] + "회"); });
        done();
    }

    static void realData() throws IOException {
        sec("24. 실제 데이터 (data/, data-ac8/)");
        for (String name : new String[]{"data", "data-ac8"}) { Path dir = Paths.get(name); W w; try { w = load(dir); } catch (IOException e) { t(name + "/ 이 읽힌다", () -> { throw new AssertionError(e.getMessage()); }); continue; }
            final W fw = w;
            t(name + "/: 과목 3,242개, 학수번호는 8자리(숫자·영문 대문자)이고 겹치지 않는다", () -> { eq(3242, fw.courses.size(), "과목 수"); Set<String> seen = new HashSet<>(); for (Course c : fw.courses) { ok(c.getCode().matches("[0-9A-Z]{8}"), "코드 " + c.getCode()); ok(seen.add(c.getCode()), "중복 " + c.getCode()); } });
            t(name + "/: 분반은 학수번호 끝 3자리", () -> { for (Course c : fw.courses) eq(c.getCode().substring(5), c.getSection(), c.getCode()); });
            t(name + "/: 과목명이 비어 있지 않고 앞뒤 공백이 없다", () -> { for (Course c : fw.courses) ok(!c.getName().isEmpty() && c.getName().equals(c.getName().trim()), c.getCode()); });
            t(name + "/: 학점은 1~18, 정원·수강인원은 0 이상", () -> { for (Course c : fw.courses) { ok(c.getCredits() >= 1 && c.getCredits() <= 18, c.getCode() + " 학점 " + c.getCredits()); ok(c.getCapacity() >= 0 && c.getBaseEnrolled() >= 0, c.getCode()); } });
            t(name + "/: 모든 수업이 1~14교시 안 (격자에 다 그려진다)", () -> { for (Course c : fw.courses) for (TimeSlot s : c.getTimes()) ok(s.getStart() >= 1 && s.getEnd() <= 14, c.getCode() + " " + s); });
            t(name + "/: 과목명·교수·강의실을 저장 형식으로 써도 다시 읽힌다 (쉼표·따옴표)", () -> { for (Course c : fw.courses) { ok(!c.getName().contains("\n") && !c.getName().contains("\r"), "줄바꿈 " + c.getCode()); ok(!c.getName().contains(";"), "세미콜론 " + c.getCode()); } });
            t(name + "/: 과목명을 14칸으로 자르면 언제나 14칸 이하", () -> { for (Course c : fw.courses) ok(TextUtil.displayWidth(TextUtil.cut(c.getName(), 14)) <= 14, c.getName()); });
            t(name + "/: 과목명으로 검색하면 그 과목이 나오고, 학수번호로 검색하면 맨 앞에 나온다", () -> { for (Course c : fw.courses) { if (c.getCode().hashCode() % 20 != 0) continue; ok(fw.catalog.search(c.getName()).contains(c), "이름 " + c.getName()); ok(fw.catalog.search(c.getCode()).contains(c), "코드 " + c.getCode()); eq(Optional.of(c.getName()), fw.catalog.resolveName(c.getCode()), "변환"); } });
            t(name + "/: 교수명으로 검색하면 그 교수의 과목이 나온다", () -> { int checked = 0; for (Course c : fw.courses) { if (c.getProfessor().isEmpty() || c.getCode().hashCode() % 20 != 0) continue; ok(fw.catalog.search(c.getProfessor()).contains(c), "교수 " + c.getProfessor()); checked++; } ok(checked > 50, "검사한 과목 " + checked); });
            t(name + "/: 영문 과목명은 대소문자를 가리지 않고 검색된다", () -> { int checked = 0; for (Course c : fw.courses) { if (!c.getName().matches(".*[A-Za-z]{4,}.*") || c.getCode().hashCode() % 10 != 0) continue; ok(fw.catalog.search(c.getName().toLowerCase()).contains(c), "소문자 " + c.getName()); ok(fw.catalog.search(c.getName().toUpperCase()).contains(c), "대문자 " + c.getName()); checked++; } ok(checked > 10, "검사한 과목 " + checked); });
            t(name + "/: 학번은 숫자이고 겹치지 않으며 이름이 있다", () -> { Set<String> seen = new HashSet<>(); for (Student s : fw.registry.all()) { ok(s.getId().matches("[0-9]+"), s.getId()); ok(seen.add(s.getId()), "중복 " + s.getId()); ok(!s.getName().trim().isEmpty(), "이름 " + s.getId()); } ok(fw.registry.size() >= 7, "학생 수"); });
            t(name + "/: 모든 시간표에 겹침·중복 수강·학점 초과가 없고, 대기 요청이 어긋나지 않는다", () -> eq(Collections.emptyList(), violations(fw), "위반"));
            t(name + "/: 파일의 수강인원 + 이 프로그램 학생 수가 정원을 넘는 과목이 없다 (넘으면 삭제해도 빈자리가 안 생김)", () -> { List<String> over = new ArrayList<>(); for (Course c : fw.courses) if (c.getCapacity() > 0 && fw.enroll.enrolledOf(c) > c.getCapacity()) over.add(c.getCode() + " " + fw.enroll.enrolledOf(c) + "/" + c.getCapacity()); eq(Collections.emptyList(), over, "정원 초과"); });
            t(name + "/: 어느 학생이 정원 있는 과목을 삭제해도 그 과목의 빈자리가 정확히 1 늘어난다", () -> { W x = load(dir); int checked = 0; for (Student s : x.registry.all()) for (Course c : new ArrayList<>(s.getTimetable().getCourses())) { if (c.getCapacity() == 0) continue; int before = x.enroll.vacancyOf(c);
                ok(s.getTimetable().remove(c), "삭제"); eq(before + 1, x.enroll.vacancyOf(c), s.getName() + " " + c.getCode() + " 빈자리"); ok(s.getTimetable().restore(c), "되돌리기"); eq(before, x.enroll.vacancyOf(c), "되돌린 뒤"); checked++; } ok(checked > 10, "검사 " + checked + "건"); System.out.println("       " + name + ": " + checked + "건 검사"); });
            t(name + "/: 요청 번호는 1 이상이고 겹치지 않으며, 희망 과목은 과목 목록에 있는 교양", () -> { Set<Integer> seen = new HashSet<>(); for (SwapRequest r : fw.board.all()) { ok(r.getId() >= 1 && seen.add(r.getId()), "#" + r.getId()); boolean general = false; for (Course c : fw.catalog.findByName(r.getWantName())) general |= c.isGeneral(); ok(general, "#" + r.getId() + " 희망 " + r.getWantName()); } });
            t(name + "/: 모든 대기 요청의 매칭이 죽지 않고, 양쪽 판정이 같다", () -> { int pairs = 0; for (SwapRequest m : fw.board.all()) { if (!m.isWaiting() || fw.registry.find(m.getStudentId()).isEmpty()) continue; for (MatchResult r : fw.match(m)) { if (fw.registry.find(r.getRequest().getStudentId()).isEmpty()) continue; MatchResult back = fw.resultFor(r.getRequest(), m); ok(back != null, "#" + m.getId()); eq(r.isPossible(), back.isPossible(), "#" + m.getId() + " <-> #" + r.getRequest().getId()); pairs++; } } System.out.println("       " + name + ": " + pairs + "쌍 검사"); });
            t(name + "/: 저장 형식으로 다시 써도 같은 상태로 읽힌다 (사본에서)", () -> { Path copy = dirOf("real-" + name); for (String f : new String[]{"courses.csv", "students.csv", "requests.csv"}) Files.copy(dir.resolve(f), copy.resolve(f), StandardCopyOption.REPLACE_EXISTING); W x = load(copy); x.save(copy); eq(fw.snapshot(), load(copy).snapshot(), "상태"); });
            for (Student s : w.registry.all()) { if (name.equals("data") && Integer.parseInt(s.getId().substring(s.getId().length() - 2)) % 3 != 0 && !s.getId().startsWith("2026123")) continue;
                t(name + "/ " + s.getName() + " (" + s.getId() + "): 시간표 격자의 모든 줄 폭이 같고 모든 과목이 요약 줄에 있다", () -> { boolean weekend = false; for (Course c : s.getTimetable().getCourses()) for (TimeSlot ts : c.getTimes()) weekend |= ts.getDay().ordinal() >= 5;
                    String out = grid(s.getTimetable()); gridShape(out, weekend ? 7 : 5); String summary = out.substring(out.lastIndexOf(" 전공: ")); for (Course c : s.getTimetable().getCourses()) has(summary, c.getName()); }); }
        }
        Path copy = dirOf("real-console");
        for (String f : new String[]{"courses.csv", "students.csv", "requests.csv"}) Files.copy(Paths.get("data-ac8", f), copy.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        String original = snap(copy);
        for (String id : new String[]{"202612345", "202612346", "202612347", "202612348", "202612349", "202612350", "202612351"}) t("data-ac8 사본: 학번 " + id + " 으로 메뉴 1·2·4·5를 돌아도 죽지 않고 상태가 그대로", () -> {
            String out = console(copy, id, "1", "생명과 나눔", "이경복", "", "2", "", "4", "5", "", "0"); endsCleanly(out); hasNot(out, "Exception"); has(out, "[교환 요청 목록] 총 8건"); eq(original, snap(copy), "상태"); });
        done();
    }
}
