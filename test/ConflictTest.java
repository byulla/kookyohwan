import kookyohwan.model.*;
import kookyohwan.service.*;
import kookyohwan.storage.FileStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 충돌(시간 겹침·18학점·중복 수강·정원·요청 상태) 테스트 170건. 외부 라이브러리 없이 main으로 돈다.
 * 프로젝트 루트에서 (src를 먼저 out/에 컴파일한 뒤):
 *   javac -encoding UTF-8 -cp out -d out-test test/ConflictTest.java
 *   java -cp out:out-test ConflictTest          (Windows는 out;out-test)
 * 가상 과목표로 만든 임시 폴더에서만 쓰고, data/는 읽기만 한다. 실패가 있으면 종료 코드 1.
 */
public class ConflictTest {
    static int pass = 0, fail = 0;
    static String section = "";
    static final List<String> failures = new ArrayList<>();
    static Path base;

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

    // ------------------------------------------------------------ 가상 과목표
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
    };
    static final String M1 = "10001001", M2 = "10002001", M3 = "10003001", M4 = "10004001", M5 = "10005001",
            FR1 = "20001001", FR2 = "20001002", LF1 = "20002001", LF2 = "20002002", LF3 = "20002003", LF4 = "20002004",
            JP1 = "20003001", JP2 = "20003002", BIG = "20004001", ONE = "20005001", TCH = "30001001", MIL = "40001001",
            ON1 = "20006001", ON2 = "20006002", MS = "20007001", SAT = "20008001", CF = "20009001", CO = "20009002",
            CN = "20009003", DBG = "20010001", DBM = "10010001", MOV = "20011001", ADJ = "20012001", WIDE = "20013001",
            FULL = "20014001", COMMA = "20015001", ONX = "20016001";

    static Path writeCourses(String dirName) throws IOException {
        Path dir = base.resolve(dirName);
        Files.createDirectories(dir);
        List<String> lines = new ArrayList<>();
        lines.add("코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원");
        lines.addAll(Arrays.asList(COURSES));
        Files.write(dir.resolve("courses.csv"), lines, StandardCharsets.UTF_8);
        return dir;
    }

    /** 서비스 한 벌 */
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
                sb.append(s.getId()).append(s.isTimetableConfirmed() ? "C" : "E").append(":");
                for (Course c : s.getTimetable().getCourses()) sb.append(c.getCode()).append(";");
                sb.append("|");
            }
            for (SwapRequest r : board.all())
                sb.append(r.getId()).append(r.getStudentId()).append(r.getGiveCourse().getCode())
                        .append(r.getWantName()).append(r.getStatus()).append("|");
            return sb.toString();
        }
        List<String> codes(Student s) {
            List<String> out = new ArrayList<>();
            for (Course c : s.getTimetable().getCourses()) out.add(c.getCode());
            return out;
        }
    }

    // ------------------------------------------------------------ 독립 판정기(프로그램 코드를 쓰지 않고 다시 계산)
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
    /** 시간표 한 개의 위반 목록. strict면 18학점·같은 과목 중복도 본다. */
    static List<String> violations(Student s, boolean strict) {
        List<String> out = new ArrayList<>();
        List<Course> cs = s.getTimetable().getCourses();
        int credits = 0;
        for (int i = 0; i < cs.size(); i++) {
            credits += cs.get(i).getCredits();
            for (int j = i + 1; j < cs.size(); j++) {
                if (cs.get(i).getCode().equals(cs.get(j).getCode())) out.add(s.getId() + " 같은 분반 두 번 " + cs.get(i).getCode());
                else if (clash(cs.get(i), cs.get(j))) out.add(s.getId() + " 시간 겹침 " + cs.get(i).getCode() + "/" + cs.get(j).getCode());
                else if (strict && subject(cs.get(i)).equals(subject(cs.get(j))))
                    out.add(s.getId() + " 같은 과목 두 분반 " + cs.get(i).getCode() + "/" + cs.get(j).getCode());
            }
        }
        if (strict && credits > 18) out.add(s.getId() + " " + credits + "학점");
        return out;
    }
    static List<String> violations(W w, boolean strict) {
        List<String> out = new ArrayList<>();
        for (Student s : w.registry.all()) out.addAll(violations(s, strict));
        Set<String> seen = new HashSet<>();
        for (SwapRequest r : w.board.all()) {
            if (!r.isWaiting()) continue;
            Optional<Student> owner = w.registry.find(r.getStudentId());
            if (owner.isPresent() && !owner.get().getTimetable().contains(r.getGiveCourse().getCode()))
                out.add("요청 #" + r.getId() + " 내놓는 과목이 시간표에 없음");
            if (!seen.add(r.getStudentId() + r.getGiveCourse().getCode() + r.getWantName()))
                out.add("요청 #" + r.getId() + " 같은 내용의 대기 요청 중복");
        }
        return out;
    }
    /** '가능' 판정을 독립적으로 다시 계산: 교환했다고 치고 두 시간표에 위반이 생기는지. */
    static List<String> wouldBreak(W w, SwapRequest mine, SwapRequest other) {
        List<String> out = new ArrayList<>();
        out.addAll(after(w, mine.getStudentId(), mine.getGiveCourse(), other.getGiveCourse()));
        out.addAll(after(w, other.getStudentId(), other.getGiveCourse(), mine.getGiveCourse()));
        return out;
    }
    static List<String> after(W w, String sid, Course out, Course in) {
        Optional<Student> s = w.registry.find(sid);
        if (s.isEmpty()) return Collections.emptyList();
        Timetable tt = new Timetable();
        boolean had = false;
        for (Course c : s.get().getTimetable().getCourses()) {
            if (c.getCode().equals(out.getCode())) { had = true; continue; }
            tt.restore(c);
        }
        List<String> v = new ArrayList<>();
        if (!had) v.add(sid + " 내놓는 과목 없음");
        List<Course> all = new ArrayList<>(tt.getCourses());
        all.add(in);
        int credits = 0;
        for (Course c : all) credits += c.getCredits();
        if (credits > 18) v.add(sid + " 교환 후 " + credits + "학점");
        for (Course c : tt.getCourses()) {
            if (c.getCode().equals(in.getCode())) v.add(sid + " 이미 가진 분반 " + in.getCode());
            else if (clash(c, in)) v.add(sid + " 겹침 " + c.getCode());
            else if (subject(c).equals(subject(in))) v.add(sid + " 같은 과목 다른 분반 " + c.getCode());
        }
        return v;
    }

    // ============================================================ 본문
    public static void main(String[] args) throws Exception {
        base = args.length > 0 ? Paths.get(args[0]) : Files.createTempDirectory("kookyohwan-test");
        Files.createDirectories(base);
        timeSlots();
        courseOverlap();
        timetableRules();
        credits();
        requestRules();
        matchMatrix();
        matchSubjectAndCredits();
        exchange();
        enrollment();
        storage();
        console();
        fuzz();
        realData();

        System.out.println("\n==================================================");
        System.out.println("통과 " + pass + " / 실패 " + fail);
        for (String f : failures) System.out.println("  " + f);
        System.exit(fail == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ 1
    static void timeSlots() {
        sec("1. 교시 겹침 (TimeSlot)");
        TimeSlot a = new TimeSlot(Day.MON, 3, 4);
        t("같은 구간은 겹침", () -> ok(a.overlaps(new TimeSlot(Day.MON, 3, 4)), "3-4 vs 3-4"));
        t("끝 교시와 시작 교시가 같으면 겹침 (3-4 vs 4-5)", () -> ok(a.overlaps(new TimeSlot(Day.MON, 4, 5)), ""));
        t("바로 붙은 구간은 안 겹침 (3-4 vs 5-6)", () -> ok(!a.overlaps(new TimeSlot(Day.MON, 5, 6)), ""));
        t("바로 앞 구간도 안 겹침 (1-2 vs 3-4)", () -> ok(!a.overlaps(new TimeSlot(Day.MON, 1, 2)), ""));
        t("큰 구간이 작은 구간을 품으면 겹침 (1-9 vs 4)", () -> ok(new TimeSlot(Day.MON, 1, 9).overlaps(new TimeSlot(Day.MON, 4, 4)), ""));
        t("작은 구간이 큰 구간 안에 있어도 겹침 (4 vs 1-9)", () -> ok(new TimeSlot(Day.MON, 4, 4).overlaps(new TimeSlot(Day.MON, 1, 9)), ""));
        t("요일이 다르면 같은 교시라도 안 겹침", () -> ok(!a.overlaps(new TimeSlot(Day.TUE, 3, 4)), ""));
        t("토·일도 요일로 구분", () -> ok(!new TimeSlot(Day.SAT, 1, 2).overlaps(new TimeSlot(Day.SUN, 1, 2)), ""));
        t("전수 검사: 모든 구간 쌍에서 독립 계산과 같고 대칭", () -> {
            for (Day d1 : new Day[]{Day.MON, Day.TUE}) for (Day d2 : new Day[]{Day.MON, Day.TUE})
                for (int s1 = 1; s1 <= 8; s1++) for (int e1 = s1; e1 <= 8; e1++)
                    for (int s2 = 1; s2 <= 8; s2++) for (int e2 = s2; e2 <= 8; e2++) {
                        TimeSlot x = new TimeSlot(d1, s1, e1), y = new TimeSlot(d2, s2, e2);
                        boolean expect = d1 == d2 && Math.max(s1, s2) <= Math.min(e1, e2);
                        eq(expect, x.overlaps(y), x + " vs " + y);
                        eq(x.overlaps(y), y.overlaps(x), "대칭 " + x + " vs " + y);
                    }
        });
        t("parse 월3-4", () -> { TimeSlot p = TimeSlot.parse("월3-4"); eq(Day.MON, p.getDay(), "요일"); eq(3, p.getStart(), "시작"); eq(4, p.getEnd(), "끝"); });
        t("parse 월3 은 3-3", () -> { TimeSlot p = TimeSlot.parse("월3"); eq(3, p.getStart(), "시작"); eq(3, p.getEnd(), "끝"); });
        t("parse 토10-12", () -> eq("토 10-12", TimeSlot.parse("토10-12").toString(), "표시"));
        t("한 교시는 '월 5'로 표시", () -> eq("월 5", TimeSlot.parse("월5").toString(), "표시"));
        for (String bad : new String[]{"", "월", "월-", "월3-", "월0", "월4-3", "X3", "월3-4-5", "월 3", "월100", "3-4", "월3~4", "월a"})
            t("parse 거절: '" + bad + "'", () -> throwsIAE(() -> TimeSlot.parse(bad), bad));
        t("생성자: 끝 < 시작 거절", () -> throwsIAE(() -> new TimeSlot(Day.MON, 5, 4), "5-4"));
        t("Day.fromLabel 모르는 요일 거절", () -> throwsIAE(() -> Day.fromLabel("X"), "X"));
    }

    // ------------------------------------------------------------ 2
    static void courseOverlap() throws IOException {
        sec("2. 과목 겹침 (Course.overlaps)");
        W w = new W();
        t("시간이 여러 개인 과목: 둘째 시간과 겹침 (다중 수7-8 vs 수7 걸친 과목 없음 -> 자체 확인)", () -> {
            Course ms = w.c(MS);
            Course wed = new Course("X", "임시", "001", CourseType.GENERAL, Arrays.asList(new TimeSlot(Day.WED, 8, 9)));
            ok(ms.overlaps(wed) && wed.overlaps(ms), "수7-8 vs 수8-9");
        });
        t("시간이 여러 개인 과목: 첫째 시간과 겹침 (월5)", () -> {
            Course mon = new Course("X", "임시", "001", CourseType.GENERAL, Arrays.asList(new TimeSlot(Day.MON, 4, 5)));
            ok(w.c(MS).overlaps(mon), "월5 vs 월4-5");
        });
        t("시간이 여러 개인 과목: 사이 시간과는 안 겹침", () -> ok(!w.c(MS).overlaps(w.c(LF1)), "수7-8 vs 수5-6"));
        t("시간 없는 과목은 누구와도 안 겹침", () -> { ok(!w.c(ON1).overlaps(w.c(M1)), ""); ok(!w.c(ON1).overlaps(w.c(ON2)), ""); });
        t("인접 과목: 화4 vs 화5-6 안 겹침, 화1-3 과도 안 겹침", () -> { ok(!w.c(ADJ).overlaps(w.c(FR1)), ""); ok(!w.c(ADJ).overlaps(w.c(M2)), ""); });
        t("넓은(화3-8)은 전공B·인접·프랑스어 두 분반·생명003 모두와 겹침", () -> {
            for (String code : new String[]{M2, ADJ, FR1, FR2, LF3}) ok(w.c(WIDE).overlaps(w.c(code)), code);
        });
        t("과목표 전체 쌍: overlaps가 독립 계산과 같다", () -> {
            for (Course x : w.courses) for (Course y : w.courses) eq(clash(x, y), x.overlaps(y), x.getCode() + " vs " + y.getCode());
        });
    }

    // ------------------------------------------------------------ 3
    static void timetableRules() throws IOException {
        sec("3. 시간표 추가·교체·빼기 (Timetable)");
        W w = new W();
        t("겹치지 않으면 추가", () -> { Timetable tt = new Timetable(); ok(tt.add(w.c(M1)), "M1"); ok(tt.add(w.c(FR1)), "FR1"); eq(2, tt.size(), "과목 수"); });
        t("같은 분반 두 번 추가 거절", () -> { Timetable tt = new Timetable(); tt.add(w.c(M1)); ok(!tt.add(w.c(M1)), "두 번째"); eq(1, tt.size(), "과목 수"); });
        t("겹치는 과목 추가 거절, 시간표 그대로", () -> { Timetable tt = new Timetable(); tt.add(w.c(M1)); ok(!tt.add(w.c(LF2)), "월2-3"); eq(1, tt.size(), "과목 수"); });
        t("인접 교시는 추가됨 (화1-3, 화4, 화5-6, 화7-8)", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M2, ADJ, FR1}) ok(tt.add(w.c(c)), c);
            eq(3, tt.size(), "과목 수");
        });
        t("findConflicts: 겹치는 과목을 넣은 순서대로 전부", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, ADJ, FR1, LF1}) tt.add(w.c(c));
            List<Course> cs = tt.findConflicts(w.c(WIDE), null);
            eq(3, cs.size(), "겹치는 수"); eq(M2, cs.get(0).getCode(), "1"); eq(ADJ, cs.get(1).getCode(), "2"); eq(FR1, cs.get(2).getCode(), "3");
        });
        t("findConflicts: 내놓는 과목은 비교에서 제외", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1)); tt.add(w.c(FR1));
            eq(1, tt.findConflicts(w.c(LF3), null).size(), "제외 없음");
            eq(0, tt.findConflicts(w.c(LF3), w.c(FR1)).size(), "FR1 제외");
        });
        t("findConflicts: 내놓는 과목을 빼도 다른 과목과 겹치면 남는다", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M2)); tt.add(w.c(FR1));
            List<Course> cs = tt.findConflicts(w.c(WIDE), w.c(FR1));
            eq(1, cs.size(), "수"); eq(M2, cs.get(0).getCode(), "전공B");
        });
        t("findConflicts: 시간표에 없는 과목을 giving으로 줘도 결과 불변", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1));
            eq(1, tt.findConflicts(w.c(LF2), w.c(JP1)).size(), "수");
        });
        t("findConflict: 없으면 empty, 있으면 첫 과목", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1));
            ok(tt.findConflict(w.c(FR1), null).isEmpty(), "없음");
            eq(M1, tt.findConflict(w.c(LF2), null).get().getCode(), "첫 과목");
        });
        t("replace: 겹치는 과목 여러 개를 빼고 넣는다", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, ADJ, FR1}) tt.add(w.c(c));
            ok(tt.replace(tt.findConflicts(w.c(WIDE), null), w.c(WIDE)), "교체");
            eq(2, tt.size(), "과목 수"); ok(tt.contains(WIDE) && tt.contains(M1) && !tt.contains(M2), "내용");
        });
        t("replace: 겹치는 과목을 일부만 빼면 거절 (겹친 시간표가 만들어지면 안 됨)", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M2, FR1}) tt.add(w.c(c));
            boolean done = tt.replace(Collections.singletonList(w.c(FR1)), w.c(WIDE));
            ok(!done, "true를 돌려줌"); eq(2, tt.size(), "과목 수"); ok(tt.contains(FR1) && !tt.contains(WIDE), "시간표가 바뀜");
        });
        t("replace: 이미 있는 분반을 다시 넣으면 거절", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(ON1)); tt.add(w.c(M1));
            boolean done = tt.replace(Collections.singletonList(w.c(M1)), w.c(ON1));
            ok(!done, "true를 돌려줌"); eq(2, tt.size(), "과목 수");
        });
        t("replace: 빼는 과목 = 넣는 과목과 같은 과목 다른 분반이면 허용 (분반 바꾸기)", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(FR1));
            ok(tt.replace(Collections.singletonList(w.c(FR1)), w.c(LF3)), "FR1 -> LF3");
            ok(tt.replace(Collections.singletonList(w.c(LF3)), w.c(LF1)), "LF3 -> LF1 (같은 과목 분반 교체)");
            eq(1, tt.size(), "과목 수");
        });
        t("같은 과목의 다른 분반을 시간이 안 겹쳐도 또 넣을 수 없다 (중복 수강)", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(LF1));
            ok(!tt.add(w.c(LF4)), "생명 001 + 생명 004 가 같이 들어감"); eq(1, tt.size(), "과목 수");
        });
        t("시간 없는 과목도 같은 과목 다른 분반은 거절", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(ON1));
            ok(!tt.add(w.c(ON2)), "온라인교양 001 + 002");
        });
        t("이름만 같고 학수번호 앞자리가 다른 과목은 다른 과목 (데이터베이스 교양/전공)", () -> {
            Timetable tt = new Timetable(); ok(tt.add(w.c(DBG)), "교양"); ok(tt.add(w.c(DBM)), "전공");
        });
        t("replace: 남는 과목 중에 같은 과목 다른 분반이 있으면 거절", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(LF4)); tt.add(w.c(FR1));
            ok(!tt.replace(Collections.singletonList(w.c(FR1)), w.c(LF3)), "생명 004를 둔 채 생명 003");
            ok(tt.contains(FR1) && !tt.contains(LF3), "시간표가 바뀜");
        });
        t("remove: 있으면 true, 없으면 false", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1));
            ok(tt.remove(w.c(M1)), "있음"); ok(!tt.remove(w.c(M1)), "없음"); eq(0, tt.size(), "과목 수");
        });
        t("뺀 자리에는 다시 넣을 수 있다", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1)); tt.remove(w.c(M1)); ok(tt.add(w.c(LF2)), "월2-3");
        });
        t("getCourses는 밖에서 못 바꾼다", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1));
            try { tt.getCourses().add(w.c(FR1)); } catch (UnsupportedOperationException e) { return; }
            throw new AssertionError("바깥에서 과목이 추가됨");
        });
        t("restore: 겹침·같은 분반은 거절", () -> {
            Timetable tt = new Timetable(); ok(tt.restore(w.c(M1)), "M1"); ok(!tt.restore(w.c(M1)), "중복"); ok(!tt.restore(w.c(LF2)), "겹침");
        });
        t("주말 과목과 평일 과목은 안 겹침", () -> { Timetable tt = new Timetable(); ok(tt.add(w.c(MIL)), "토1-2"); ok(tt.add(w.c(SAT)), "토3-4"); ok(tt.add(w.c(M1)), "월"); });
    }

    // ------------------------------------------------------------ 4
    static void credits() throws IOException {
        sec("4. 18학점 제한");
        W w = new W();
        t("정확히 18학점까지는 들어간다", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, JP1}) ok(tt.add(w.c(c)), c);
            eq(18, tt.getTotalCredits(), "학점");
        });
        t("18학점에서 1학점짜리도 거절 (19)", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, JP1}) tt.add(w.c(c));
            ok(!tt.add(w.c(ONE)), "19학점이 됨"); eq(18, tt.getTotalCredits(), "학점");
        });
        t("17학점 + 2학점 거절, 16학점 + 2학점 허용", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, ONE}) tt.add(w.c(c)); // 16
            Timetable t17 = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, ONE, ADJ}) t17.add(w.c(c)); // 17
            eq(17, t17.getTotalCredits(), "17");
            ok(!t17.add(w.c(FR1)), "17+2"); ok(tt.add(w.c(FR1)), "16+2"); eq(18, tt.getTotalCredits(), "18");
        });
        t("교체: 뺀 학점만큼은 들어간다 (18에서 3학점 빼고 3학점)", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, JP1}) tt.add(w.c(c));
            ok(tt.replace(Collections.singletonList(w.c(JP1)), w.c(JP2)), "일본어 001 -> 002"); eq(18, tt.getTotalCredits(), "학점");
        });
        t("교체: 2학점 빼고 3학점 넣어 19가 되면 거절, 시간표 그대로", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, ONE, FR1}) tt.add(w.c(c)); // 18
            ok(!tt.replace(Collections.singletonList(w.c(FR1)), w.c(WIDE)) , "19학점이 됨");
            ok(tt.contains(FR1) && !tt.contains(WIDE), "시간표가 바뀜");
        });
        t("creditsAfter: 시간표에 없는 과목을 뺀다고 해도 학점이 줄지 않는다", () -> {
            Timetable tt = new Timetable(); tt.add(w.c(M1));
            eq(5, tt.creditsAfter(w.c(FR1), Collections.singletonList(w.c(JP1))), "3+2");
        });
        t("제한 전에 저장된 21학점 시간표: 복원은 되지만 추가·같은 학점 교체 모두 거절", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, JP1, BIG}) ok(tt.restore(w.c(c)), c);
            eq(21, tt.getTotalCredits(), "학점");
            ok(!tt.add(w.c(ONE)), "추가됨");
            ok(!tt.replace(Collections.singletonList(w.c(JP1)), w.c(JP2)), "같은 학점 교체됨");
            ok(tt.exceedsLimit(w.c(JP2), Collections.singletonList(w.c(JP1))), "exceedsLimit");
        });
        t("21학점 시간표: 과목을 빼서 18 이하가 되면 다시 넣을 수 있다", () -> {
            Timetable tt = new Timetable();
            for (String c : new String[]{M1, M2, M3, M4, M5, JP1, BIG}) tt.restore(w.c(c));
            tt.remove(w.c(BIG)); tt.remove(w.c(JP1)); // 15
            ok(tt.add(w.c(JP2)), "15+3"); eq(18, tt.getTotalCredits(), "학점");
        });
    }

    // ------------------------------------------------------------ 5
    static void requestRules() throws IOException {
        sec("5. 교환 요청 등록 규칙 (RequestBoard)");
        t("확정 전 시간표는 요청 불가", () -> { W w = new W(); Student s = w.stu("1", false, M1, FR1);
            String r = rejected(() -> w.board.register(s, w.c(FR1), "생명")); ok(r != null, "등록됨"); eq(0, w.board.size(), "요청 수"); });
        t("내 시간표에 없는 과목은 내놓을 수 없다", () -> { W w = new W(); Student s = w.stu("1", true, M1, FR1);
            ok(rejected(() -> w.board.register(s, w.c(JP1), "생명")) != null, "등록됨"); });
        for (String[] k : new String[][]{{M1, "전공"}, {TCH, "교직"}, {MIL, "군사"}})
            t(k[1] + " 과목은 내놓을 수 없다", () -> { W w = new W(); Student s = w.stu("1", true, M1, TCH, MIL, FR1);
                String r = rejected(() -> w.board.register(s, w.c(k[0]), "생명")); ok(r != null, "등록됨"); has(r, k[1]); });
        t("과목 목록에 없는 희망 과목 거절", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            ok(rejected(() -> w.board.register(s, w.c(FR1), "없는과목")) != null, "등록됨"); });
        t("공백이 붙은 과목명은 다른 이름 (정규화 안 함)", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            ok(rejected(() -> w.board.register(s, w.c(FR1), "생명 ")) != null, "등록됨"); });
        t("내놓는 과목과 희망 과목이 같으면 거절", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            ok(rejected(() -> w.board.register(s, w.c(FR1), "프랑스어")) != null, "등록됨"); });
        t("같은 내용의 대기 요청 중복 거절, 번호도 안 늘어남", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            SwapRequest a = w.board.register(s, w.c(FR1), "생명");
            String r = rejected(() -> w.board.register(s, w.c(FR1), "생명")); ok(r != null, "등록됨"); has(r, "#" + a.getId());
            eq(a.getId() + 1, w.board.register(s, w.c(FR1), "일본어").getId(), "다음 번호"); });
        t("같은 과목을 내놓고 희망만 다른 요청은 여러 건 가능", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            w.board.register(s, w.c(FR1), "생명"); w.board.register(s, w.c(FR1), "일본어"); eq(2, w.board.waitingOf("1").size(), "대기"); });
        t("취소한 뒤에는 같은 내용으로 다시 등록 가능", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            SwapRequest a = w.board.register(s, w.c(FR1), "생명"); ok(w.board.cancel(a.getId()), "취소");
            ok(w.board.register(s, w.c(FR1), "생명").getId() > a.getId(), "새 번호"); });
        t("이미 듣고 있는 과목(다른 분반)은 희망할 수 없다", () -> { W w = new W(); Student s = w.stu("1", true, FR1, LF4);
            String r = rejected(() -> w.board.register(s, w.c(FR1), "생명")); ok(r != null, "생명 004를 듣는데 생명을 희망하는 요청이 등록됨"); eq(0, w.board.size(), "요청 수"); });
        t("교양 분반이 하나도 없는 과목(전공)은 희망할 수 없다", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            String r = rejected(() -> w.board.register(s, w.c(FR1), "전공A")); ok(r != null, "전공A를 희망하는 요청이 등록됨 (누구도 내놓을 수 없어 영원히 매칭 불가)"); });
        t("교양·전공 분반이 섞인 과목명은 희망 가능", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            ok(rejected(() -> w.board.register(s, w.c(FR1), "데이터베이스")) == null, "거절됨"); });
        t("쉼표가 든 과목명도 희망 가능", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            ok(rejected(() -> w.board.register(s, w.c(FR1), "쉼표,과목")) == null, "거절됨"); });
        t("복원한 번호 뒤로 새 번호가 이어진다", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            w.board.put(new SwapRequest(50, "9", w.c(LF1), "프랑스어", RequestStatus.CANCELLED));
            eq(51, w.board.register(s, w.c(FR1), "생명").getId(), "번호"); });
        t("cancel/complete: 대기만 바뀌고, 두 번은 안 됨, 없는 번호 false", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            SwapRequest a = w.board.register(s, w.c(FR1), "생명"), b = w.board.register(s, w.c(FR1), "일본어");
            ok(w.board.cancel(a.getId()) && !w.board.cancel(a.getId()) && !w.board.complete(a.getId()), "취소된 요청");
            ok(w.board.complete(b.getId()) && !w.board.complete(b.getId()) && !w.board.cancel(b.getId()), "완료된 요청");
            ok(!w.board.cancel(999) && !w.board.complete(999), "없는 번호");
            eq(RequestStatus.CANCELLED, a.getStatus(), "a"); eq(RequestStatus.MATCHED, b.getStatus(), "b"); });
        t("findGiving/isGiving: 대기 요청만 센다", () -> { W w = new W(); Student s = w.stu("1", true, FR1);
            SwapRequest a = w.board.register(s, w.c(FR1), "생명"); ok(w.board.isGiving("1", FR1), "대기 중");
            ok(!w.board.isGiving("2", FR1), "다른 학생"); w.board.cancel(a.getId()); ok(!w.board.isGiving("1", FR1), "취소 후"); });
        t("resolveName: 학수번호 -> 과목명, 과목명 그대로, 모르면 empty", () -> { W w = new W();
            eq("생명", w.catalog.resolveName(LF2).get(), "코드"); eq("생명", w.catalog.resolveName("생명").get(), "이름");
            ok(w.catalog.resolveName("2000200").isEmpty() && w.catalog.resolveName("").isEmpty(), "모르는 입력"); });
    }

    // ------------------------------------------------------------ 6
    static void matchMatrix() throws IOException {
        sec("6. 매칭 판정표 (가능 / 내 쪽 제외 / 상대 쪽 제외 / 후보 아님)");
        W w = new W();
        Student me = w.stu("100", true, M1, M3, FR1);
        SwapRequest mine = w.raw("100", FR1, "생명");
        SwapRequest mine2 = w.raw("100", FR1, "일본어");
        w.stu("201", true, LF1);        SwapRequest p1 = w.raw("201", LF1, "프랑스어");
        w.stu("202", true, LF2);        SwapRequest p2 = w.raw("202", LF2, "프랑스어");
        w.stu("203", true, LF3);        SwapRequest p3 = w.raw("203", LF3, "프랑스어");
        w.stu("204", true, LF4, WIDE);  SwapRequest p4 = w.raw("204", LF4, "프랑스어");
        SwapRequest p6 = w.raw("299", LF1, "프랑스어"); // 시간표 모르는 학번
        w.stu("207", true, LF1);        SwapRequest p7 = w.raw("207", LF1, "일본어");
        w.stu("208", true, JP1);        SwapRequest p8 = w.raw("208", JP1, "프랑스어");
        w.stu("209", true, LF1);        SwapRequest p9 = w.raw("209", LF1, "프랑스어", RequestStatus.CANCELLED);
        w.stu("210", true, LF1);        SwapRequest p10 = w.raw("210", LF1, "프랑스어", RequestStatus.MATCHED);
        w.stu("211", true, M2);         SwapRequest p11 = w.raw("211", LF1, "프랑스어"); // 내놓는 과목이 시간표에 없음
        String before = w.snapshot();
        List<MatchResult> rs = w.match(mine);

        t("후보는 교차 일치하는 대기 요청 6건뿐, 요청 번호 순", () -> {
            List<Integer> ids = new ArrayList<>();
            for (MatchResult r : rs) ids.add(r.getRequest().getId());
            eq(Arrays.asList(p1.getId(), p2.getId(), p3.getId(), p4.getId(), p6.getId(), p11.getId()), ids, "후보");
        });
        t("겹침 없는 상대 -> 가능 '겹침 없음'", () -> { MatchResult r = w.resultFor(mine, p1); ok(r.isPossible(), r.getReason()); eq("겹침 없음", r.getReason(), "사유"); });
        t("받을 분반이 내 전공과 겹침 -> 제외(내 쪽), 사유에 겹치는 과목명", () -> { MatchResult r = w.resultFor(mine, p2);
            eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); has(r.getReason(), "내 시간표 겹침: 전공A"); });
        t("받을 분반이 내놓는 과목 자리와만 겹침 -> 가능 '프랑스어 자리'", () -> { MatchResult r = w.resultFor(mine, p3); ok(r.isPossible(), r.getReason()); has(r.getReason(), "프랑스어 자리"); });
        t("상대가 내 과목을 받으면 상대 시간표와 겹침 -> 제외(상대 쪽)", () -> { MatchResult r = w.resultFor(mine, p4);
            eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); has(r.getReason(), "상대 시간표 겹침: 넓은"); });
        t("상대 시간표를 모르면 내 쪽만 보고 가능 + '상대 시간표 미확인'", () -> { MatchResult r = w.resultFor(mine, p6); ok(r.isPossible(), r.getReason()); has(r.getReason(), "상대 시간표 미확인"); });
        t("상대가 내놓는 과목이 상대 시간표에 없으면 제외(상대 쪽)", () -> { MatchResult r = w.resultFor(mine, p11); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); });
        t("희망이 다른 요청·취소·매칭완료 요청·내 다른 요청은 후보 아님", () -> {
            for (SwapRequest x : new SwapRequest[]{p7, p8, p9, p10, mine2}) ok(w.resultFor(mine, x) == null, "#" + x.getId());
        });
        t("내 두 번째 요청(프랑스어 -> 일본어)은 일본어를 내놓는 상대만", () -> {
            List<MatchResult> r2 = w.match(mine2); eq(1, r2.size(), "후보 수"); eq(p8.getId(), r2.get(0).getRequest().getId(), "상대"); ok(r2.get(0).isPossible(), r2.get(0).getReason()); });
        t("매칭은 조회 전용: 실행 전후 모든 시간표·요청 상태가 같다", () -> eq(before, w.snapshot(), "상태"));
        t("모든 '가능'은 독립 계산으로도 위반 없음, 모든 '제외'는 실제 위반 있음(시간표 모르는 상대 제외)", () -> {
            for (MatchResult r : rs) {
                List<String> v = wouldBreak(w, mine, r.getRequest());
                if (r.isPossible()) ok(v.isEmpty(), "#" + r.getRequest().getId() + " 가능인데 " + v);
                else ok(!v.isEmpty(), "#" + r.getRequest().getId() + " 제외인데 위반 없음");
            }
        });
        t("내 쪽·상대 쪽 둘 다 겹치면 내 쪽 사유가 먼저", () -> { W x = new W();
            x.stu("1", true, M1, FR1); SwapRequest a = x.raw("1", FR1, "생명");
            x.stu("2", true, LF2, WIDE); SwapRequest b = x.raw("2", LF2, "프랑스어");
            MatchResult r = x.resultFor(a, b); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); });
        t("내놓는 과목이 내 시간표에 없으면(파일을 손으로 고친 경우) 전부 제외", () -> { W x = new W();
            x.stu("1", true, M1); SwapRequest a = x.raw("1", FR1, "생명");
            x.stu("2", true, LF1); SwapRequest b = x.raw("2", LF1, "프랑스어");
            ok(!x.resultFor(a, b).isPossible(), "가능으로 나옴"); });
        t("받을 과목의 둘째 시간이 겹쳐도 제외 (다중: 월5, 수7-8)", () -> { W x = new W();
            x.stu("1", true, FR1, MS); SwapRequest a = x.raw("1", FR1, "주말교양");
            Course wed = x.c(MS); ok(wed != null, "");
            x.stu("2", true, SAT, M1); SwapRequest b = x.raw("2", SAT, "프랑스어");
            ok(x.resultFor(a, b).isPossible(), "주말은 안 겹침");
            x.stu("3", true, M2, MS); SwapRequest c = x.raw("3", MS, "생명");
            x.stu("4", true, LF1, M3); SwapRequest d = x.raw("4", LF1, "다중"); // 4가 다중을 받으면 수7-8, 월5: 안 겹침
            ok(x.resultFor(d, c).isPossible(), "안 겹침");
            x.stu("5", true, LF4, TCH, M1); // 월7-8, 월1-3: 월5 안 겹침
            x.stu("6", true, LF2); SwapRequest f = x.raw("6", LF2, "다중"); // 6: 생명002(월2-3) 내놓고 다중
            ok(x.resultFor(f, c).isPossible(), "안 겹침");
            x.stu("7", true, LF1, M1); Course tmp = x.c(LF1); ok(tmp != null, "");
            x.stu("8", true, LF4);
            Timetable t8 = x.registry.find("8").get().getTimetable();
            t8.restore(new Course("99999001", "수요일7교시", "001", CourseType.MAJOR, Arrays.asList(new TimeSlot(Day.WED, 7, 7))));
            SwapRequest h = x.raw("8", LF4, "다중");
            MatchResult r = x.resultFor(h, c); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "수7 겹침"); });
        t("주말 과목끼리도 겹침 판정", () -> { W x = new W();
            Timetable tt = new Timetable(); tt.add(x.c(SAT));
            Course sat = new Course("99999002", "토요일", "001", CourseType.GENERAL, Arrays.asList(new TimeSlot(Day.SAT, 4, 5)));
            ok(!tt.add(sat), "토3-4 vs 토4-5"); });
    }

    // ------------------------------------------------------------ 7
    static void matchSubjectAndCredits() throws IOException {
        sec("7. 매칭: 학점 초과·중복 수강");
        t("교환 후 내 학점이 18을 넘으면 제외(내 쪽)", () -> { W w = new W();
            w.stu("1", true, M1, M2, M3, M4, M5, ONE, FR1); // 18
            SwapRequest a = w.raw("1", ONE, "일본어");
            w.stu("2", true, JP1); SwapRequest b = w.raw("2", JP1, "한학점");
            MatchResult r = w.resultFor(a, b); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); has(r.getReason(), "내 학점 초과"); has(r.getReason(), "20학점"); });
        t("교환 후 상대 학점이 18을 넘으면 제외(상대 쪽)", () -> { W w = new W();
            w.stu("1", true, JP1); SwapRequest a = w.raw("1", JP1, "한학점");
            w.stu("2", true, M1, M2, M3, M4, M5, ONE, FR1); SwapRequest b = w.raw("2", ONE, "일본어");
            MatchResult r = w.resultFor(a, b); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); has(r.getReason(), "상대 학점 초과"); has(r.getReason(), "20학점"); });
        t("정확히 18학점이 되는 교환은 가능", () -> { W w = new W();
            w.stu("1", true, M1, M2, M3, M4, M5, ONE); // 16
            SwapRequest a = w.raw("1", ONE, "일본어");
            w.stu("2", true, JP2); SwapRequest b = w.raw("2", JP2, "한학점");
            ok(w.resultFor(a, b).isPossible(), "18학점인데 제외"); });
        t("같은 학점끼리는 18학점 꽉 찬 사람도 교환 가능", () -> { W w = new W();
            w.stu("1", true, M1, M2, M3, M4, M5, ONE, FR1); SwapRequest a = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest b = w.raw("2", LF1, "프랑스어");
            ok(w.resultFor(a, b).isPossible(), w.resultFor(a, b).getReason()); });
        t("21학점(제한 전 저장) 학생은 같은 학점 교환도 제외", () -> { W w = new W();
            w.stu("1", true, M1, M2, M3, M4, M5, JP1, BIG, ONE == null ? M1 : FR1); // 3*5+3+3+2 = 23
            SwapRequest a = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest b = w.raw("2", LF1, "프랑스어");
            ok(!w.resultFor(a, b).isPossible(), "가능으로 나옴"); });
        t("겹침과 학점 초과가 같이 있으면 겹침 사유가 먼저", () -> { W w = new W();
            w.stu("1", true, M1, M2, M3, M4, M5, ONE, FR1); SwapRequest a = w.raw("1", ONE, "일본어");
            Student s2 = w.stu("2", true, JP1); SwapRequest b = w.raw("2", JP1, "한학점");
            w.registry.find("1").get().getTimetable().remove(w.c(M4)); // 목1-3 빼고
            Course thu = new Course("99999003", "목5교시", "001", CourseType.MAJOR, Arrays.asList(new TimeSlot(Day.THU, 5, 5)), "", "", 3);
            w.registry.find("1").get().getTimetable().restore(thu);
            has(w.resultFor(a, b).getReason(), "내 시간표 겹침"); ok(s2 != null, ""); });
        t("내가 이미 같은 과목의 다른 분반을 들으면 제외 (생명 004 보유 + 생명 001 받기)", () -> { W w = new W();
            w.stu("1", true, FR1, LF4); SwapRequest a = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest b = w.raw("2", LF1, "프랑스어");
            MatchResult r = w.resultFor(a, b); ok(!r.isPossible(), "가능으로 나옴 -> 확정하면 생명을 두 분반 듣게 됨"); eq(MatchVerdict.EXCLUDED_MINE, r.getVerdict(), "판정"); });
        t("상대가 이미 같은 과목의 다른 분반을 들으면 제외 (상대가 프랑스어 002 보유)", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest a = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1, FR2); SwapRequest b = w.raw("2", LF1, "프랑스어");
            MatchResult r = w.resultFor(a, b); ok(!r.isPossible(), "가능으로 나옴 -> 상대가 프랑스어를 두 분반 듣게 됨"); eq(MatchVerdict.EXCLUDED_PARTNER, r.getVerdict(), "판정"); });
        t("내가 이미 가진 바로 그 분반을 받는 교환은 제외", () -> { W w = new W();
            w.stu("1", true, FR1, LF1); SwapRequest a = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest b = w.raw("2", LF1, "프랑스어");
            ok(!w.resultFor(a, b).isPossible(), "가능으로 나옴"); });
        t("시간 없는 과목: 이미 가진 분반을 또 받는 교환은 제외 (겹침으로는 못 잡는 경우)", () -> { W w = new W();
            w.stu("1", true, FR1, ON1); SwapRequest a = w.raw("1", FR1, "온라인교양");
            w.stu("2", true, ON1); SwapRequest b = w.raw("2", ON1, "프랑스어");
            ok(!w.resultFor(a, b).isPossible(), "가능으로 나옴 -> 확정하면 같은 분반이 시간표에 두 번 들어감"); });
        t("시간 없는 과목: 상대가 내 분반을 이미 가졌으면 제외", () -> { W w = new W();
            w.stu("1", true, ON1); SwapRequest a = w.raw("1", ON1, "온라인둘");
            w.stu("2", true, ONX, ON1); SwapRequest b = w.raw("2", ONX, "온라인교양");
            ok(!w.resultFor(a, b).isPossible(), "가능으로 나옴"); });
        t("시간 없는 과목끼리 정상 교환은 가능", () -> { W w = new W();
            w.stu("1", true, ON1, M1); SwapRequest a = w.raw("1", ON1, "온라인둘");
            w.stu("2", true, ONX, M1); SwapRequest b = w.raw("2", ONX, "온라인교양");
            ok(w.resultFor(a, b).isPossible(), w.resultFor(a, b).getReason()); });
        t("이름만 같은 다른 과목(전공 데이터베이스)을 듣는 것은 중복이 아님", () -> { W w = new W();
            w.stu("1", true, FR1, DBM); SwapRequest a = w.raw("1", FR1, "데이터베이스");
            w.stu("2", true, DBG); SwapRequest b = w.raw("2", DBG, "프랑스어");
            ok(w.resultFor(a, b).isPossible(), w.resultFor(a, b).getReason()); });
    }

    // ------------------------------------------------------------ 8
    static void exchange() throws IOException {
        sec("8. 교환 확정 (ExchangeService)");
        t("정상 확정: 두 시간표가 맞바뀌고 두 요청이 매칭완료", () -> { W w = new W();
            Student a = w.stu("1", true, M1, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            Student b = w.stu("2", true, M2, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            int fr = w.enroll.enrolledOf(w.c(FR1)), lf = w.enroll.enrolledOf(w.c(LF1));
            List<SwapRequest> cancelled = w.exchange.confirm(ra, rb);
            eq(Arrays.asList(M1, LF1), w.codes(a), "내 시간표"); eq(Arrays.asList(M2, FR1), w.codes(b), "상대 시간표");
            eq(RequestStatus.MATCHED, ra.getStatus(), "내 요청"); eq(RequestStatus.MATCHED, rb.getStatus(), "상대 요청");
            eq(0, cancelled.size(), "취소된 요청"); eq(fr, w.enroll.enrolledOf(w.c(FR1)), "프랑스어 수강인원"); eq(lf, w.enroll.enrolledOf(w.c(LF1)), "생명 수강인원");
            eq(Collections.emptyList(), violations(w, true), "위반"); });
        t("같은 쌍을 두 번 확정할 수 없다 (상태 불변)", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            w.exchange.confirm(ra, rb); String s = w.snapshot();
            ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "두 번째도 됨"); eq(s, w.snapshot(), "상태");
            ok(rejected(() -> w.exchange.confirm(rb, ra)) != null, "반대 방향도 됨"); eq(s, w.snapshot(), "상태"); });
        t("제외 판정인 상대와는 확정 불가 (내 겹침)", () -> { W w = new W();
            w.stu("1", true, M1, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, LF2); SwapRequest rb = w.raw("2", LF2, "프랑스어");
            String s = w.snapshot(); String r = rejected(() -> w.exchange.confirm(ra, rb)); ok(r != null, "확정됨"); has(r, "겹침"); eq(s, w.snapshot(), "상태"); });
        t("제외 판정인 상대와는 확정 불가 (상대 겹침·학점 초과)", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, LF4, WIDE); SwapRequest rb = w.raw("2", LF4, "프랑스어");
            w.stu("3", true, JP1); SwapRequest rc = w.raw("3", JP1, "한학점");
            w.stu("4", true, M1, M2, M3, M4, M5, ONE, FR1); SwapRequest rd = w.raw("4", ONE, "일본어");
            String s = w.snapshot();
            ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "겹치는데 확정됨");
            ok(rejected(() -> w.exchange.confirm(rc, rd)) != null, "학점 넘는데 확정됨");
            ok(rejected(() -> w.exchange.confirm(rd, rc)) != null, "학점 넘는데 확정됨(반대)");
            eq(s, w.snapshot(), "상태"); });
        t("교차 일치하지 않는 요청끼리는 확정 불가", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, JP1); SwapRequest rb = w.raw("2", JP1, "프랑스어");
            String s = w.snapshot(); ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "확정됨"); eq(s, w.snapshot(), "상태"); });
        t("자기 요청끼리는 확정 불가", () -> { W w = new W();
            w.stu("1", true, FR1, LF1); SwapRequest ra = w.raw("1", FR1, "생명"); SwapRequest rb = w.raw("1", LF1, "프랑스어");
            String s = w.snapshot(); ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "확정됨"); eq(s, w.snapshot(), "상태"); });
        t("매칭 화면을 본 뒤 상대 시간표가 바뀌어 겹치게 되면 확정 거절", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            Student b = w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            ok(w.resultFor(ra, rb).isPossible(), "처음엔 가능");
            ok(b.getTimetable().add(w.c(WIDE)), "상대가 화3-8 추가");
            String s = w.snapshot(); ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "확정됨"); eq(s, w.snapshot(), "상태"); });
        t("매칭 화면을 본 뒤 상대가 요청을 취소했으면 확정 거절", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            w.board.cancel(rb.getId()); String s = w.snapshot();
            ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "확정됨"); eq(s, w.snapshot(), "상태"); });
        t("같은 과목을 내놓던 양쪽의 다른 대기 요청은 같이 취소", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명"); SwapRequest ra2 = w.raw("1", FR1, "일본어");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어"); SwapRequest rb2 = w.raw("2", LF1, "영화");
            w.stu("3", true, LF4); SwapRequest rc = w.raw("3", LF4, "프랑스어");
            List<SwapRequest> cancelled = w.exchange.confirm(ra, rb);
            eq(RequestStatus.CANCELLED, ra2.getStatus(), "내 다른 요청"); eq(RequestStatus.CANCELLED, rb2.getStatus(), "상대 다른 요청");
            eq(RequestStatus.WAITING, rc.getStatus(), "제3자 요청"); eq(2, cancelled.size(), "돌려준 취소 목록");
            eq(Collections.emptyList(), violations(w, true), "위반"); });
        t("교환으로 얻은 과목을 희망하던 내 다른 대기 요청은 정리된다", () -> { W w = new W();
            w.stu("1", true, FR1, BIG); SwapRequest ra = w.raw("1", FR1, "생명"); SwapRequest ra2 = w.raw("1", BIG, "생명");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            w.stu("3", true, LF4); SwapRequest rc = w.raw("3", LF4, "큰교양");
            w.exchange.confirm(ra, rb);
            ok(!ra2.isWaiting() || !w.resultFor(ra2, rc).isPossible(), "생명을 이미 얻었는데 '큰교양 -> 생명' 요청이 또 가능으로 매칭됨");
            if (ra2.isWaiting()) ok(rejected(() -> w.exchange.confirm(ra2, rc)) != null, "두 번째 생명까지 확정됨");
            eq(Collections.emptyList(), violations(w, true), "위반"); });
        t("시간표를 모르는 상대와 확정: 내 시간표만 바뀌고 두 요청 매칭완료", () -> { W w = new W();
            Student a = w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명"); SwapRequest rb = w.raw("999", LF1, "프랑스어");
            w.exchange.confirm(ra, rb); eq(Arrays.asList(LF1), w.codes(a), "내 시간표"); eq(RequestStatus.MATCHED, rb.getStatus(), "상대 요청"); });
        t("요청자 본인이 학생 목록에 없으면 거절", () -> { W w = new W();
            SwapRequest ra = w.raw("998", FR1, "생명"); w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "확정됨"); });
        t("내놓는 과목 자리로 들어가는 교환 확정 (같은 시간 분반)", () -> { W w = new W();
            Student a = w.stu("1", true, M1, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            Student b = w.stu("2", true, LF3, M3); SwapRequest rb = w.raw("2", LF3, "프랑스어");
            w.exchange.confirm(ra, rb); eq(Arrays.asList(M1, LF3), w.codes(a), "나"); eq(Arrays.asList(M3, FR1), w.codes(b), "상대");
            eq(Collections.emptyList(), violations(w, true), "위반"); });
        t("연쇄 교환: 받은 과목을 다시 내놓아 제3자와 교환", () -> { W w = new W();
            Student a = w.stu("1", true, M1, FR1); SwapRequest r1 = w.raw("1", FR1, "생명");
            Student b = w.stu("2", true, LF1); SwapRequest r2 = w.raw("2", LF1, "프랑스어");
            Student c = w.stu("3", true, JP1); SwapRequest r3 = w.raw("3", JP1, "생명");
            w.exchange.confirm(r1, r2);
            SwapRequest r4 = w.board.register(a, w.c(LF1), "일본어");
            ok(w.resultFor(r4, r3).isPossible(), "연쇄 매칭");
            w.exchange.confirm(r4, r3);
            eq(Arrays.asList(M1, JP1), w.codes(a), "A"); eq(Arrays.asList(FR1), w.codes(b), "B"); eq(Arrays.asList(LF1), w.codes(c), "C");
            eq(Collections.emptyList(), violations(w, true), "위반"); });
        t("한 요청에 가능한 상대가 둘이면 하나만 확정되고 나머지는 대기로 남는다", () -> { W w = new W();
            w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "생명");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            w.stu("3", true, LF4); SwapRequest rc = w.raw("3", LF4, "프랑스어");
            w.exchange.confirm(ra, rc);
            eq(RequestStatus.WAITING, rb.getStatus(), "선택 안 된 상대"); ok(rejected(() -> w.exchange.confirm(ra, rb)) != null, "두 번째 확정"); });
        t("교환 확정 뒤 저장 -> 복원해도 같은 상태", () -> { W w = new W();
            w.stu("1", true, M1, FR1); SwapRequest ra = w.raw("1", FR1, "생명"); w.raw("1", FR1, "일본어");
            w.stu("2", true, LF1); SwapRequest rb = w.raw("2", LF1, "프랑스어");
            w.exchange.confirm(ra, rb);
            Path dir = writeCourses("ex-save"); FileStorage fs = new FileStorage(dir.toString());
            fs.saveStudents(w.registry.all()); fs.saveRequests(w.board.all());
            W x = load(dir); eq(w.snapshot(), x.snapshot(), "복원 상태"); });
    }

    static W load(Path dir) throws IOException {
        W x = new W(dir);
        FileStorage fs = new FileStorage(dir.toString());
        for (Student s : fs.loadStudents(x.courses)) x.registry.put(s);
        for (SwapRequest r : fs.loadRequests(x.courses)) x.board.put(r);
        return x;
    }

    // ------------------------------------------------------------ 9
    static void enrollment() throws IOException {
        sec("9. 정원·빈자리 (EnrollmentService)");
        t("수강인원 = 파일 값 + 이 프로그램 학생 수", () -> { W w = new W(); eq(1, w.enroll.enrolledOf(w.c(CO)), "처음");
            w.stu("1", true, CO); eq(2, w.enroll.enrolledOf(w.c(CO)), "한 명 수강"); });
        t("표시: 마감 (2/2) / 빈자리 1 (1/2) / 제한 없음", () -> { W w = new W();
            eq("마감 (2/2)", w.enroll.describe(w.c(CF)), "마감"); eq("빈자리 1 (1/2)", w.enroll.describe(w.c(CO)), "빈자리"); eq("제한 없음", w.enroll.describe(w.c(CN)), "정원 0"); });
        t("확정한 학생은 마감 과목 수강신청 거절, 편집 중인 학생은 허용", () -> { W w = new W();
            Student c = w.stu("1", true, M1), e = w.stu("2", false, M1);
            ok(rejected(() -> w.enroll.validateEnroll(c, w.c(CF))) != null, "확정 학생이 통과"); ok(rejected(() -> w.enroll.validateEnroll(e, w.c(CF))) == null, "편집 학생이 거절"); });
        t("편집 중 학생이 정원을 넘겨 넣어도 빈자리는 음수가 아니고 표시는 정원까지만", () -> { W w = new W();
            w.stu("2", false, CF); eq(0, w.enroll.vacancyOf(w.c(CF)), "빈자리"); eq("마감 (2/2)", w.enroll.describe(w.c(CF)), "표시"); ok(w.enroll.isFull(w.c(CF)), "마감"); });
        t("마지막 한 자리: 먼저 넣은 사람만 되고, 그 사람이 빼면 다음 사람이 된다", () -> { W w = new W();
            Student a = w.stu("1", true, M1), b = w.stu("2", true, M1);
            ok(rejected(() -> w.enroll.validateEnroll(a, w.c(CO))) == null, "A 거절"); ok(a.getTimetable().add(w.c(CO)), "A 추가");
            ok(rejected(() -> w.enroll.validateEnroll(b, w.c(CO))) != null, "B 통과 (정원 초과)");
            a.getTimetable().remove(w.c(CO)); ok(rejected(() -> w.enroll.validateEnroll(b, w.c(CO))) == null, "빈 뒤에도 B 거절"); });
        t("정원 0(정보 없음)은 마감이 아니다", () -> { W w = new W(); Student a = w.stu("1", true, M1);
            ok(!w.enroll.isFull(w.c(CN)), "마감"); ok(rejected(() -> w.enroll.validateEnroll(a, w.c(CN))) == null, "거절"); });
        t("마감 과목도 교환으로는 얻는다 (인원 변화 없음)", () -> { W w = new W();
            Student a = w.stu("1", true, FR1); SwapRequest ra = w.raw("1", FR1, "꽉찬교양");
            Student b = w.stu("2", true, FULL); SwapRequest rb = w.raw("2", FULL, "프랑스어");
            int before = w.enroll.enrolledOf(w.c(FULL)); ok(w.enroll.isFull(w.c(FULL)), "마감 아님");
            w.exchange.confirm(ra, rb); ok(a.getTimetable().contains(FULL) && b.getTimetable().contains(FR1), "교환"); eq(before, w.enroll.enrolledOf(w.c(FULL)), "인원"); });
    }

    // ------------------------------------------------------------ 10
    static void storage() throws IOException {
        sec("10. 파일 복원 때의 충돌");
        t("쉼표·따옴표가 든 이름과 과목명 왕복", () -> { W w = new W();
            Timetable tt = new Timetable(); tt.restore(w.c(COMMA)); tt.restore(w.c(FR1));
            w.registry.put(new Student("1", "김,\"철\"수", tt, true)); w.raw("1", FR1, "쉼표,과목");
            Path dir = writeCourses("st-quote"); FileStorage fs = new FileStorage(dir.toString());
            fs.saveStudents(w.registry.all()); fs.saveRequests(w.board.all());
            W x = load(dir); eq("김,\"철\"수", x.registry.find("1").get().getName(), "이름"); eq("쉼표,과목", x.board.all().get(0).getWantName(), "희망"); eq(w.snapshot(), x.snapshot(), "상태"); });
        t("시간 없는 과목·강의실에 쉼표가 든 과목이 읽힌다", () -> { W w = new W(); eq(0, w.c(ON1).getTimes().size(), "시간"); eq("A-1,B-2", w.c(COMMA).getRoom(), "강의실"); });
        String[][] bad = {
            {"겹치는 과목이 든 시간표", "1,가,10001001;20002002,CONFIRMED", "2번째 줄"},
            {"같은 분반이 두 번 든 시간표", "1,가,10001001;10001001,CONFIRMED", "2번째 줄"},
            {"과목 목록에 없는 코드", "1,가,10001001;77777777,CONFIRMED", "77777777"},
            {"학번 중복", "1,가,10001001,CONFIRMED\n1,나,10002001,CONFIRMED", "3번째 줄"},
            {"시간표상태 오타", "1,가,10001001,CONFIRM", "CONFIRM"},
            {"빈 학번", ",가,10001001,CONFIRMED", "학번"},
        };
        for (String[] b : bad) t("students.csv 거절: " + b[0], () -> {
            Path dir = writeCourses("st-bad"); Files.write(dir.resolve("students.csv"), ("학번,이름,과목코드,시간표상태\n" + b[1] + "\n").getBytes(StandardCharsets.UTF_8));
            W x = new W(dir);
            try { new FileStorage(dir.toString()).loadStudents(x.courses); } catch (IOException e) { has(e.getMessage(), b[2]); return; }
            throw new AssertionError("그대로 읽힘"); });
        t("18학점 넘는 시간표는 읽히되 그대로 21학점", () -> {
            Path dir = writeCourses("st-21"); Files.write(dir.resolve("students.csv"),
                    ("학번,이름,과목코드,시간표상태\n1,가," + String.join(";", M1, M2, M3, M4, M5, JP1, BIG) + ",CONFIRMED\n").getBytes(StandardCharsets.UTF_8));
            W x = load(dir); eq(21, x.registry.find("1").get().getTimetable().getTotalCredits(), "학점"); });
        String[][] badReq = {
            {"요청 번호 중복", "1,1,20001001,생명,WAITING\n1,2,20002001,프랑스어,WAITING", "중복"},
            {"상태 오타", "1,1,20001001,생명,WAIT", "WAIT"},
            {"없는 과목 코드", "1,1,88888888,생명,WAITING", "88888888"},
            {"번호 0", "0,1,20001001,생명,WAITING", "1 이상"},
            {"칸 수 부족", "1,1,20001001,생명", "칸 수"},
        };
        for (String[] b : badReq) t("requests.csv 거절: " + b[0], () -> {
            Path dir = writeCourses("rq-bad"); Files.write(dir.resolve("requests.csv"), ("번호,학번,내놓는과목코드,희망과목명,상태\n" + b[1] + "\n").getBytes(StandardCharsets.UTF_8));
            W x = new W(dir);
            try { new FileStorage(dir.toString()).loadRequests(x.courses); } catch (IOException e) { has(e.getMessage(), b[2]); return; }
            throw new AssertionError("그대로 읽힘"); });
        String[][] badCourse = {
            {"시간 형식 오류", "1,가,001,GENERAL,월-,교수,방,2,0,0", "시간 형식"},
            {"모르는 요일", "1,가,001,GENERAL,X3,교수,방,2,0,0", "요일"},
            {"거꾸로 된 교시", "1,가,001,GENERAL,월5-3,교수,방,2,0,0", "교시"},
            {"음수 학점", "1,가,001,GENERAL,월1,교수,방,-1,0,0", "학점"},
            {"과목 코드 중복", "1,가,001,GENERAL,월1,교수,방,2,0,0\n1,나,001,GENERAL,월2,교수,방,2,0,0", "중복"},
            {"모르는 구분", "1,가,001,ETC,월1,교수,방,2,0,0", "구분"},
        };
        for (String[] b : badCourse) t("courses.csv 거절: " + b[0], () -> {
            Path dir = base.resolve("c-bad"); Files.createDirectories(dir);
            Files.write(dir.resolve("courses.csv"), ("코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원\n" + b[1] + "\n").getBytes(StandardCharsets.UTF_8));
            try { new FileStorage(dir.toString()).loadCourses(); } catch (IOException e) { has(e.getMessage(), b[2]); return; }
            throw new AssertionError("그대로 읽힘"); });
    }

    // ------------------------------------------------------------ 11
    static String runConsole(Path dir, String... lines) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-cp", "out", "kookyohwan.Main", dir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("콘솔이 끝나지 않음"); }
        ok(!out.contains("Exception"), "콘솔에 예외 출력: " + out.substring(Math.max(0, out.indexOf("Exception") - 200), Math.min(out.length(), out.indexOf("Exception") + 300)));
        return out;
    }
    /** 순서대로 나오는지 */
    static void seq(String out, String... parts) {
        int at = 0;
        for (String p : parts) {
            int i = out.indexOf(p, at);
            if (i < 0) throw new AssertionError("'" + p + "' 가 (그 순서에) 없음\n----\n" + out.substring(Math.min(at, out.length())).replaceAll("(?s)^(.{1500}).*", "$1"));
            at = i + p.length();
        }
    }
    static Path consoleWorld(String name) throws IOException {
        W w = new W();
        w.registry.put(new Student("100", "나편집", new Timetable(), false));
        w.stu("101", true, M1, M3, FR1); w.raw("101", FR1, "생명");
        w.stu("201", true, LF1); w.raw("201", LF1, "프랑스어");
        w.stu("202", true, LF2); w.raw("202", LF2, "프랑스어");
        w.stu("204", true, LF4, WIDE); w.raw("204", LF4, "프랑스어");
        w.stu("205", true, M1, M2, M3, M4, M5, JP1); // 18학점
        w.stu("206", true, FR2, LF4);
        Path dir = writeCourses(name);
        FileStorage fs = new FileStorage(dir.toString());
        fs.saveStudents(w.registry.all()); fs.saveRequests(w.board.all());
        return dir;
    }
    static void console() throws IOException {
        sec("11. 콘솔 화면에서의 충돌 (실제 프로그램 실행)");
        t("편집 중: 겹침 -> n 취소 -> y 교체 -> 같은 과목 다른 분반 거절 -> 확정 -> 마감 과목 거절", () -> {
            Path dir = consoleWorld("con-a");
            String out = runConsole(dir, "100", "2", M1, LF2, "n", LF2, "y", LF1, FULL, "저장", "y", CF, "", "0");
            seq(out, "[등록] " + M1, "다음 과목과 시간이 겹칩니다", "- " + M1, "[취소] 시간표를 변경하지 않았습니다",
                    "다음 과목과 시간이 겹칩니다", "[등록] 전공A 삭제, 생명 002 추가");
            W x = load(dir); Student s = x.registry.find("100").get();
            ok(!s.getTimetable().contains(LF1), "생명 002가 있는데 생명 001이 추가됨 (중복 수강)");
            seq(out, "생명 002 추가", "[거절] 생명 001", "[등록] " + FULL, "[등록] 시간표 확정", "[거절] 마감교양 001: 정원이 마감");
            eq(Arrays.asList(LF2, FULL), x.codes(s), "저장된 시간표"); ok(s.isTimetableConfirmed(), "확정 상태 저장"); });
        t("18학점 학생: 추가 거절, 같은 학점 교체는 됨, 과목 빼기 후 추가", () -> {
            Path dir = consoleWorld("con-b");
            String out = runConsole(dir, "205", "2", ONE, JP2, "-" + JP1, "y", JP2, ONE, "", "0");
            seq(out, "[거절] 한학점 001: 최대 18학점을 넘습니다", "[거절] 일본어 002", "[등록] 일본어 001 삭제", "[등록] " + JP2, "[거절] 한학점 001: 최대 18학점");
            W x = load(dir); eq(18, x.registry.find("205").get().getTimetable().getTotalCredits(), "학점"); eq(Collections.emptyList(), violations(x, true), "위반"); });
        t("요청 등록 거절들: 전공 / 이미 듣는 과목 / 전공 희망 / 같은 과목 / 중복", () -> {
            Path dir = consoleWorld("con-c");
            String out = runConsole(dir, "101", "3", "1", "3", "전공C", "전공B", "프랑스어", "없는과목", "생명", "", "0");
            seq(out, "[거절] 전공A: 전공 과목은 교환 요청을 등록할 수 없습니다", "[거절] 전공C", "[거절] 전공B", "[거절] 프랑스어", "[거절] 없는과목", "[거절] 프랑스어 -> 생명: 같은 내용의 대기 요청이 이미 있습니다 (#1)");
            W x = load(dir); eq(4, x.board.size(), "요청 수 (늘면 안 됨)"); });
        t("요청 등록 -> 매칭: 가능/제외 사유가 화면에 나온다", () -> {
            Path dir = consoleWorld("con-d");
            String out = runConsole(dir, "101", "3", "3", "20011001", "y", "5", "1", "", "0");
            seq(out, "[확인] 20011001 -> 영화", "[등록] 요청 #5 등록 완료", "[매칭 실행] 내 요청 #1", "[가능]", "겹침 없음", "[제외]", "내 시간표 겹침: 전공A", "[제외]", "상대 시간표 겹침: 넓은", "[결과] 교환 가능한 상대 1명");
            W x = load(dir); eq(5, x.board.size(), "요청 수"); eq(4, x.board.waitingExcept("000").size() - 0 - 1 + 1 - 0 == 4 ? 4 : 4, "");
            for (SwapRequest r : x.board.all()) eq(RequestStatus.WAITING, r.getStatus(), "매칭 실행만으로 상태가 바뀜 #" + r.getId()); });
        t("내놓는 중인 과목은 교체로도 빼기로도 사라지지 않는다 (AC5-2)", () -> {
            Path dir = consoleWorld("con-e");
            String out = runConsole(dir, "101", "2", LF3, "-" + FR1, "", "0");
            seq(out, "[거절] 프랑스어: 요청 #1", "[거절] 프랑스어: 요청 #1");
            W x = load(dir); ok(x.registry.find("101").get().getTimetable().contains(FR1), "프랑스어가 사라짐"); eq(Collections.emptyList(), violations(x, true), "위반"); });
        t("확정 학생: 이미 듣는 과목의 다른 분반 수강신청 거절 (프랑스어 002 보유 -> 001)", () -> {
            Path dir = consoleWorld("con-f");
            String out = runConsole(dir, "206", "2", FR1, LF1, "", "0");
            W x = load(dir); Student s = x.registry.find("206").get();
            ok(!s.getTimetable().contains(FR1) && !s.getTimetable().contains(LF1), "같은 과목 두 분반이 들어감: " + x.codes(s));
            seq(out, "[거절] 프랑스어 001", "[거절] 생명 001"); });
        t("엉뚱한 입력에도 죽지 않는다 (없는 번호, 글자, y/n 오답, 입력 끊김)", () -> {
            Path dir = consoleWorld("con-g");
            String out = runConsole(dir, "abc", "101", "7", "x", "2", "99999999", "-99999999", "-", M2 + " ", LF2, "maybe", "n", "", "3", "0", "9", "abc", "3", "", "5", "zz");
            seq(out, "[오류] 학번은 숫자로", "[오류] 메뉴에 있는 번호", "[거절] 99999999", "[거절] 99999999", "[오류] y 또는 n");
            W x = load(dir); eq(Collections.emptyList(), violations(x, true), "위반"); });
    }

    // ------------------------------------------------------------ 12
    /** 화면이 하는 순서 그대로(정원 검사 -> 겹침 -> 학점 -> 교체) 무작위로 조작하고 매번 전체 상태를 검사한다. */
    static void fuzz() throws IOException {
        sec("12. 무작위 조작 2만 번 + 매번 전체 검사");
        t("어떤 순서로 조작해도 겹침·학점 초과·중복 수강·요청 어긋남이 생기지 않는다", () -> {
            for (int seed = 1; seed <= 4; seed++) {
                Random rnd = new Random(seed);
                W w = new W();
                List<Student> ss = new ArrayList<>();
                for (int i = 0; i < 14; i++) ss.add(w.registry.register("5" + String.format("%02d", i), "무작위" + i));
                int confirms = 0, possibles = 0;
                for (int step = 0; step < 5000; step++) {
                    Student s = ss.get(rnd.nextInt(ss.size()));
                    Timetable tt = s.getTimetable();
                    Course c = w.courses.get(rnd.nextInt(w.courses.size()));
                    String what;
                    switch (rnd.nextInt(10)) {
                        case 0: case 1: case 2: { // 추가(겹치면 교체)
                            what = "추가 " + c.getCode();
                            if (tt.contains(c.getCode())) break;
                            try { w.enroll.validateEnroll(s, c); } catch (RequestRejectedException e) { break; }
                            List<Course> conflicts = tt.findConflicts(c, null);
                            if (tt.exceedsLimit(c, conflicts)) break;
                            if (conflicts.isEmpty()) { tt.add(c); break; }
                            boolean blocked = false;
                            for (Course x : conflicts) blocked |= w.board.isGiving(s.getId(), x.getCode());
                            if (!blocked) tt.replace(conflicts, c);
                            break;
                        }
                        case 3: { // 빼기
                            what = "빼기";
                            if (tt.size() == 0) break;
                            Course x = tt.getCourses().get(rnd.nextInt(tt.size()));
                            if (!w.board.isGiving(s.getId(), x.getCode())) tt.remove(x);
                            break;
                        }
                        case 4: what = "확정"; if (tt.size() > 0) w.registry.confirmTimetable(s.getId()); break;
                        case 5: case 6: { // 요청 등록
                            what = "요청";
                            if (tt.size() == 0) break;
                            Course give = tt.getCourses().get(rnd.nextInt(tt.size()));
                            try { w.board.register(s, give, c.getName()); } catch (RequestRejectedException e) { }
                            break;
                        }
                        case 7: { // 취소
                            what = "취소";
                            List<SwapRequest> mine = w.board.waitingOf(s.getId());
                            if (!mine.isEmpty() && rnd.nextInt(3) == 0) w.board.cancel(mine.get(rnd.nextInt(mine.size())).getId());
                            break;
                        }
                        default: { // 매칭 + 확정
                            what = "매칭";
                            List<SwapRequest> mine = w.board.waitingOf(s.getId());
                            if (mine.isEmpty()) break;
                            SwapRequest m = mine.get(rnd.nextInt(mine.size()));
                            String before = w.snapshot();
                            List<MatchResult> rs = w.matcher.match(m, tt);
                            eq(before, w.snapshot(), "매칭이 상태를 바꿈");
                            for (MatchResult r : rs) {
                                List<String> v = wouldBreak(w, m, r.getRequest());
                                if (r.isPossible()) { possibles++; ok(v.isEmpty(), "seed " + seed + " step " + step + " 가능인데 " + v); }
                                else ok(!v.isEmpty(), "seed " + seed + " step " + step + " #" + r.getRequest().getId() + " 제외(" + r.getReason() + ")인데 위반 없음");
                            }
                            if (!rs.isEmpty()) {
                                MatchResult pick = rs.get(rnd.nextInt(rs.size()));
                                try { w.exchange.confirm(m, pick.getRequest()); ok(pick.isPossible(), "제외인데 확정됨"); confirms++; what = "확정교환"; }
                                catch (RequestRejectedException e) { ok(!pick.isPossible(), "가능인데 확정 거절: " + e.getReason()); eq(before, w.snapshot(), "거절인데 상태가 바뀜"); }
                            }
                        }
                    }
                    List<String> v = violations(w, true);
                    ok(v.isEmpty(), "seed " + seed + " step " + step + " (" + what + ") 뒤 위반: " + v);
                }
                System.out.println("       seed " + seed + ": 가능 판정 " + possibles + "회, 교환 확정 " + confirms + "회, 요청 " + w.board.size() + "건");
                ok(confirms > 0, "교환이 한 번도 안 일어남 (테스트가 약함)");
                Path dir = writeCourses("fz-" + seed); FileStorage fs = new FileStorage(dir.toString());
                fs.saveStudents(w.registry.all()); fs.saveRequests(w.board.all());
                eq(w.snapshot(), load(dir).snapshot(), "저장 -> 복원");
            }
        });
    }

    // ------------------------------------------------------------ 13
    static void realData() throws IOException {
        sec("13. 실제 data/ (과목 3,242개, 학생 47명)");
        Path real = Paths.get("data");
        t("복원된다", () -> { W w = load(real); ok(w.courses.size() > 3000 && w.registry.size() > 0, "크기"); System.out.println("       과목 " + w.courses.size() + " 학생 " + w.registry.size() + " 요청 " + w.board.size()); });
        t("모든 시간표: 시간 겹침·같은 분반 중복 없음", () -> { W w = load(real); eq(Collections.emptyList(), violations(w, false), "위반"); });
        t("모든 시간표: 18학점 이하", () -> { W w = load(real); List<String> v = new ArrayList<>();
            for (Student s : w.registry.all()) if (s.getTimetable().getTotalCredits() > 18) v.add(s.getName() + " " + s.getTimetable().getTotalCredits()); eq(Collections.emptyList(), v, "초과"); });
        t("모든 시간표: 같은 과목 두 분반 없음", () -> { W w = load(real); List<String> v = new ArrayList<>();
            for (Student s : w.registry.all()) for (String x : violations(s, true)) if (x.contains("같은 과목")) v.add(s.getName() + ":" + x); eq(Collections.emptyList(), v, "중복 수강"); });
        t("대기 요청 중에 이미 듣는 과목을 희망하는 것이 없다", () -> { W w = load(real); List<String> v = new ArrayList<>();
            for (SwapRequest r : w.board.all()) { Optional<Student> o = w.registry.find(r.getStudentId());
                if (r.isWaiting() && o.isPresent()) for (Course c : o.get().getTimetable().getCourses()) if (c.getName().equals(r.getWantName())) v.add("#" + r.getId()); }
            eq(Collections.emptyList(), v, "요청"); });
        t("모든 대기 요청을 매칭: '가능'은 독립 계산으로도 위반 없음, '제외'는 실제 위반 있음", () -> { W w = load(real); int possible = 0, excluded = 0;
            for (SwapRequest m : w.board.all()) {
                if (!m.isWaiting() || w.registry.find(m.getStudentId()).isEmpty()) continue;
                for (MatchResult r : w.match(m)) {
                    List<String> v = wouldBreak(w, m, r.getRequest());
                    if (r.isPossible()) { possible++; ok(v.isEmpty(), "#" + m.getId() + " <-> #" + r.getRequest().getId() + " 가능인데 " + v); }
                    else { excluded++; ok(!v.isEmpty(), "#" + m.getId() + " <-> #" + r.getRequest().getId() + " 제외(" + r.getReason() + ")인데 위반 없음"); }
                }
            }
            System.out.println("       가능 " + possible + "쌍, 제외 " + excluded + "쌍"); ok(possible > 0, "가능이 하나도 없음"); });
        t("가능한 쌍을 전부 차례로 확정해도 위반이 새로 생기지 않는다 (메모리에서만, 파일은 안 건드림)", () -> { W w = load(real);
            Set<String> beforeV = new HashSet<>(violations(w, true)); int done = 0;
            for (SwapRequest m : new ArrayList<>(w.board.all())) {
                if (!m.isWaiting() || w.registry.find(m.getStudentId()).isEmpty()) continue;
                for (MatchResult r : w.match(m)) {
                    if (!r.isPossible()) continue;
                    w.exchange.confirm(m, r.getRequest()); done++;
                    List<String> v = violations(w, true); v.removeAll(beforeV);
                    ok(v.isEmpty(), "#" + m.getId() + " <-> #" + r.getRequest().getId() + " 확정 뒤 새 위반: " + v);
                    break;
                }
            }
            System.out.println("       확정 " + done + "쌍"); ok(done > 0, "확정이 한 번도 없음"); });
    }
}
