import kookyohwan.model.*;
import kookyohwan.service.*;
import kookyohwan.storage.FileStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CycleTest {
    static int pass = 0, fail = 0;
    static boolean verbose = false;
    static final Map<String, Integer> failCount = new LinkedHashMap<>();
    static final Map<String, String> failFirst = new LinkedHashMap<>();
    static Path base;

    static long cyclesSeen = 0, possibleSeen = 0, confirmed = 0, refused = 0;

    static final String[] NAMES = {"교양A", "교양B", "교양C", "교양D", "교양E", "교양F", "교양G", "교양H"};
    static final int[] CREDITS = {2, 3, 2, 3, 1, 2, 3, 4};
    static final Day[] DAYS = {Day.MON, Day.TUE, Day.WED};

    static class W {
        final CourseCatalog catalog = new CourseCatalog();
        final StudentRegistry registry = new StudentRegistry();
        final RequestBoard board = new RequestBoard(catalog);
        final MatchService matcher = new MatchService(board, registry);
        final ExchangeService exchange = new ExchangeService(board, registry, matcher);
        final List<Course> courses = new ArrayList<>();
        boolean dirty;

        Timetable tt(SwapRequest r) {
            return registry.find(r.getStudentId()).map(Student::getTimetable).orElse(null);
        }
        List<SwapRequest> waiting() {
            List<SwapRequest> out = new ArrayList<>();
            for (SwapRequest r : board.all()) if (r.isWaiting()) out.add(r);
            return out;
        }
        String snapshot() {
            StringBuilder sb = new StringBuilder();
            for (Student s : registry.all()) {
                sb.append(s.getId()).append(s.isTimetableConfirmed() ? "C" : "E").append(":");
                for (Course c : s.getTimetable().getCourses()) sb.append(c.getCode()).append(";");
                sb.append("|");
            }
            for (SwapRequest r : board.all())
                sb.append(r.getId()).append(",").append(r.getStudentId()).append(",").append(r.getGiveCourse().getCode())
                        .append(",").append(r.getWantName()).append(",").append(r.getStatus()).append("|");
            return sb.toString();
        }
    }

    static List<TimeSlot> slot(Random rnd, int length) {
        if (rnd.nextInt(12) == 0) return Collections.emptyList();
        int start = 1 + rnd.nextInt(7);
        return Collections.singletonList(new TimeSlot(DAYS[rnd.nextInt(DAYS.length)], start, start + length - 1));
    }

    static W world(long seed) {
        Random rnd = new Random(seed);
        W w = new W();
        for (int k = 0; k < NAMES.length; k++)
            for (int sec = 1; sec <= 3; sec++)
                w.courses.add(new Course(String.format("2%04d%03d", k + 1, sec), NAMES[k], String.format("%03d", sec),
                        CourseType.GENERAL, slot(rnd, 2), "교수", "방", CREDITS[k], 0, 0));
        for (int k = 0; k < 4; k++)
            w.courses.add(new Course(String.format("1%04d001", k + 1), "전공" + k, "001", CourseType.MAJOR,
                    slot(rnd, 3), "교수", "방", 3, 0, 0));

        w.courses.add(new Course("29001001", "교양A", "001", CourseType.GENERAL, slot(rnd, 2), "교수", "방", 2, 0, 0));
        w.courses.add(new Course("19002001", "교양B", "001", CourseType.MAJOR, slot(rnd, 3), "교수", "방", 3, 0, 0));
        for (Course c : w.courses) w.catalog.add(c);

        int n = 3 + rnd.nextInt(5);
        List<Student> students = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Timetable tt = new Timetable();
            List<Course> pool = new ArrayList<>(w.courses);
            Collections.shuffle(pool, rnd);
            int want = 1 + rnd.nextInt(6);
            for (Course c : pool) if (tt.size() < want) tt.add(c);
            Student s = new Student(String.valueOf(101 + i), "학생" + (i + 1), tt, true);
            w.registry.put(s);
            students.add(s);
        }

        if (rnd.nextBoolean()) {
            List<Student> order = new ArrayList<>(students);
            Collections.shuffle(order, rnd);
            Course[] give = new Course[3];
            for (int i = 0; i < 3; i++) give[i] = anyGeneral(order.get(i), rnd);
            if (give[0] != null && give[1] != null && give[2] != null)
                for (int i = 0; i < 3; i++) tryRegister(w, order.get(i), give[i], give[(i + 1) % 3].getName());
        }
        int tries = n * (2 + rnd.nextInt(4));
        for (int i = 0; i < tries; i++) {
            Student s = students.get(rnd.nextInt(n));
            Course give = anyGeneral(s, rnd);
            if (give != null) tryRegister(w, s, give, NAMES[rnd.nextInt(NAMES.length)]);
        }
        if (rnd.nextInt(100) < 15) dirty(w, students, rnd);
        return w;
    }

    static Course anyGeneral(Student s, Random rnd) {
        List<Course> g = new ArrayList<>();
        for (Course c : s.getTimetable().getCourses()) if (c.isGeneral()) g.add(c);
        return g.isEmpty() ? null : g.get(rnd.nextInt(g.size()));
    }

    static void tryRegister(W w, Student s, Course give, String want) {
        try {
            w.board.register(s, give, want);
        } catch (RequestRejectedException ignored) {
        }
    }

    static void dirty(W w, List<Student> students, Random rnd) {
        w.dirty = true;
        int next = 1;
        for (SwapRequest r : w.board.all()) next = Math.max(next, r.getId() + 1);
        int changes = 1 + rnd.nextInt(5);
        for (int i = 0; i < changes; i++) {
            Student s = students.get(rnd.nextInt(students.size()));
            Course any = w.courses.get(rnd.nextInt(w.courses.size()));
            String want = NAMES[rnd.nextInt(NAMES.length)];
            switch (rnd.nextInt(7)) {
                case 0 -> s.getTimetable().restore(any);
                case 1 -> w.board.put(new SwapRequest(next++, s.getId(), any, want, RequestStatus.WAITING));
                case 2 -> w.board.put(new SwapRequest(next++, "999", any, want, RequestStatus.WAITING));
                case 3 -> w.board.put(new SwapRequest(next++, s.getId(), any, any.getName(), RequestStatus.WAITING));
                case 4 -> w.board.put(new SwapRequest(next++, s.getId(), any, "없는과목", RequestStatus.WAITING));
                case 5 -> w.board.put(new SwapRequest(next++, s.getId(), any, want,
                        rnd.nextBoolean() ? RequestStatus.CANCELLED : RequestStatus.MATCHED));
                default -> {
                    List<Course> have = s.getTimetable().getCourses();
                    if (!have.isEmpty()) s.getTimetable().remove(have.get(rnd.nextInt(have.size())));
                }
            }
        }
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

    static List<List<SwapRequest>> oracleCycles(W w, SwapRequest mine) {
        List<List<SwapRequest>> out = new ArrayList<>();
        List<SwapRequest> waiting = w.waiting();
        for (SwapRequest b : waiting) {
            if (b.getStudentId().equals(mine.getStudentId()) || !b.getGiveCourse().getName().equals(mine.getWantName())) continue;
            for (SwapRequest c : waiting) {
                if (c.getStudentId().equals(mine.getStudentId()) || c.getStudentId().equals(b.getStudentId())) continue;
                if (c.getGiveCourse().getName().equals(b.getWantName())
                        && mine.getGiveCourse().getName().equals(c.getWantName())) out.add(Arrays.asList(mine, b, c));
            }
        }
        return out;
    }

    static int oracleBlocked(W w, List<SwapRequest> cycle) {
        for (int i = 0; i < cycle.size(); i++) {
            SwapRequest r = cycle.get(i);
            Course in = cycle.get((i + 1) % cycle.size()).getGiveCourse();
            Timetable tt = w.tt(r);
            if (tt == null) continue;
            boolean has = false;
            int credits = in.getCredits();
            boolean bad = false;
            for (Course c : tt.getCourses()) {
                if (c.getCode().equals(r.getGiveCourse().getCode())) { has = true; continue; }
                credits += c.getCredits();
                bad |= clash(c, in) || subject(c).equals(subject(in));
            }
            if (!has || bad || credits > Timetable.MAX_CREDITS) return i;
        }
        return -1;
    }

    static Set<String> violations(W w) {
        Set<String> out = new TreeSet<>();
        for (Student s : w.registry.all()) {
            List<Course> cs = s.getTimetable().getCourses();
            int credits = 0;
            for (int i = 0; i < cs.size(); i++) {
                credits += cs.get(i).getCredits();
                for (int j = i + 1; j < cs.size(); j++) {
                    if (cs.get(i).getCode().equals(cs.get(j).getCode())) out.add(s.getId() + " 같은 분반 두 번 " + cs.get(i).getCode());
                    else if (clash(cs.get(i), cs.get(j))) out.add(s.getId() + " 시간 겹침 " + cs.get(i).getCode() + "/" + cs.get(j).getCode());
                    else if (subject(cs.get(i)).equals(subject(cs.get(j)))) out.add(s.getId() + " 같은 과목 두 분반 " + subject(cs.get(i)));
                }
            }
            if (credits > Timetable.MAX_CREDITS) out.add(s.getId() + " 18학점 초과");
        }
        for (SwapRequest r : w.board.all()) {
            if (!r.isWaiting()) continue;
            Timetable tt = w.tt(r);
            if (tt == null) continue;
            if (!tt.contains(r.getGiveCourse().getCode())) out.add("요청 #" + r.getId() + " 내놓는 과목이 시간표에 없음");
            for (Course have : tt.getCourses())
                for (Course sec : w.courses)
                    if (sec.isGeneral() && sec.getName().equals(r.getWantName()) && subject(sec).equals(subject(have)))
                        out.add("요청 #" + r.getId() + " 희망 과목을 이미 듣고 있음");
        }
        return out;
    }

    static List<String> allCodes(W w) {
        List<String> out = new ArrayList<>();
        for (Student s : w.registry.all()) for (Course c : s.getTimetable().getCourses()) out.add(c.getCode());
        Collections.sort(out);
        return out;
    }

    static String ids(List<SwapRequest> rs) {
        StringBuilder sb = new StringBuilder();
        for (SwapRequest r : rs) sb.append(r == null ? "null" : "#" + r.getId()).append(" ");
        return sb.toString().trim();
    }

    static String describe(W w, SwapRequest mine) {
        Timetable tt = w.tt(mine);
        StringBuilder sb = new StringBuilder();
        if (tt == null) return "";
        for (CycleMatch c : w.matcher.matchCycles(mine, tt)) sb.append(ids(c.getRequests())).append("=").append(c.getVerdict()).append(";");
        return sb.toString();
    }

    interface Check { String run(W w, Random rnd) throws Exception; }

    static void cases(String group, int count, long seedBase, Check check) {
        int before = fail;
        for (int i = 0; i < count; i++) {
            long seed = seedBase + i;
            String problem;
            try {
                problem = check.run(world(seed), new Random(seed * 31 + 7));
            } catch (Throwable t) {
                StackTraceElement at = null;
                for (StackTraceElement e : t.getStackTrace()) if (e.getClassName().startsWith("kookyohwan")) { at = e; break; }
                problem = "예외 " + t.getClass().getSimpleName() + (at == null ? "" : " @ " + at.getFileName() + ":" + at.getLineNumber())
                        + (t.getMessage() == null ? "" : " (" + t.getMessage() + ")");
            }
            if (problem == null) {
                pass++;
            } else {
                fail++;
                String kind = group + " / " + problem.replaceAll("#\\d+", "#n").replaceAll("\\d{3,}", "N");
                if (kind.length() > 150) kind = kind.substring(0, 150);
                failCount.merge(kind, 1, Integer::sum);
                failFirst.putIfAbsent(kind, "씨앗 " + seed + ": " + problem);
                if (verbose) System.out.println("  FAIL " + group + " 씨앗 " + seed + ": " + problem);
            }
        }
        System.out.printf("%-58s %6d개  실패 %d%n", group, count, fail - before);
    }

    static String rejectedOrProblem(Runnable action) {
        try {
            action.run();
            return "거절되지 않음";
        } catch (RequestRejectedException e) {
            return null;
        }
    }

    static String searchMatchesOracle(W w, Random rnd) {
        String before = w.snapshot();
        for (SwapRequest mine : w.waiting()) {
            Timetable tt = w.tt(mine);
            if (tt == null) continue;
            List<CycleMatch> found = w.matcher.matchCycles(mine, tt);
            List<List<SwapRequest>> expected = oracleCycles(w, mine);
            List<String> a = new ArrayList<>(), b = new ArrayList<>();
            for (CycleMatch c : found) a.add(ids(c.getRequests()));
            for (List<SwapRequest> c : expected) b.add(ids(c));
            if (!a.equals(b)) return "고리 목록이 다름: 찾은 것 " + a + " / 정답 " + b;
            for (CycleMatch c : found) {
                cyclesSeen++;
                possibleSeen += c.isPossible() ? 1 : 0;
                int blocked = oracleBlocked(w, c.getRequests());
                MatchVerdict want = blocked < 0 ? MatchVerdict.POSSIBLE
                        : blocked == 0 ? MatchVerdict.EXCLUDED_MINE : MatchVerdict.EXCLUDED_PARTNER;
                if (c.getVerdict() != want) return "판정이 다름 " + ids(c.getRequests()) + ": " + c.getVerdict() + " (" + c.getReason() + ") / 정답 " + want;
                if (c.getReason() == null || c.getReason().isEmpty()) return "사유가 비어 있음";
                if (c.isPossible() != c.getReason().startsWith("겹침 없음")) return "판정과 사유가 안 맞음: " + c.getReason();
            }
        }
        return before.equals(w.snapshot()) ? null : "탐색이 상태를 바꿈";
    }

    static String cycleShape(W w, Random rnd) {
        for (SwapRequest mine : w.waiting()) {
            Timetable tt = w.tt(mine);
            if (tt == null) continue;
            Set<String> seen = new HashSet<>();
            for (CycleMatch c : w.matcher.matchCycles(mine, tt)) {
                List<SwapRequest> rs = c.getRequests();
                if (rs.size() != MatchService.CYCLE_SIZE) return "고리 크기 " + rs.size();
                if (rs.get(0) != mine) return "첫 요청이 내 요청이 아님";
                if (!seen.add(ids(rs))) return "같은 고리가 두 번 나옴 " + ids(rs);
                Set<String> students = new HashSet<>();
                for (int i = 0; i < rs.size(); i++) {
                    SwapRequest r = rs.get(i);
                    if (!r.isWaiting()) return "대기가 아닌 요청이 고리에 있음 #" + r.getId();
                    if (!students.add(r.getStudentId())) return "같은 학생이 고리에 두 번";
                    Course in = c.receiveCourseOf(i);
                    if (in != rs.get((i + 1) % rs.size()).getGiveCourse()) return "받을 과목이 다음 사람의 과목이 아님";
                    if (!in.getName().equals(r.getWantName())) return "받을 과목이 희망 과목과 다름";
                }
            }
        }
        return null;
    }

    static String rotation(W w, Random rnd) {
        for (SwapRequest mine : w.waiting()) {
            Timetable tt = w.tt(mine);
            if (tt == null) continue;
            for (CycleMatch c : w.matcher.matchCycles(mine, tt)) {
                List<SwapRequest> rs = c.getRequests();
                for (int shift = 1; shift < rs.size(); shift++) {
                    List<SwapRequest> turned = new ArrayList<>();
                    for (int i = 0; i < rs.size(); i++) turned.add(rs.get((i + shift) % rs.size()));
                    Timetable other = w.tt(turned.get(0));
                    if (other == null) continue;
                    CycleMatch same = null;
                    for (CycleMatch x : w.matcher.matchCycles(turned.get(0), other)) if (x.getRequests().equals(turned)) same = x;
                    if (same == null) return "돌려 본 고리가 없음 " + ids(turned);
                    if (same.isPossible() != c.isPossible()) return "기준에 따라 가능 여부가 다름 " + ids(rs) + " / " + ids(turned);
                }
            }
        }
        return null;
    }

    static List<CycleMatch> allCycles(W w) {
        List<CycleMatch> out = new ArrayList<>();
        for (SwapRequest mine : w.waiting()) {
            Timetable tt = w.tt(mine);
            if (tt != null) out.addAll(w.matcher.matchCycles(mine, tt));
        }
        return out;
    }

    static String checkConfirmed(W w, CycleMatch c, Set<String> violationsBefore, List<String> codesBefore,
                                 Map<Integer, RequestStatus> statusBefore, List<SwapRequest> cancelled) {
        List<SwapRequest> rs = c.getRequests();
        boolean allKnown = true;
        Set<String> members = new HashSet<>();
        for (int i = 0; i < rs.size(); i++) {
            SwapRequest r = rs.get(i);
            members.add(r.getStudentId());
            if (r.getStatus() != RequestStatus.MATCHED) return "확정 후 매칭완료가 아님 #" + r.getId();
            Timetable tt = w.tt(r);
            if (tt == null) { allKnown = false; continue; }
            if (tt.contains(r.getGiveCourse().getCode())) return "내놓은 과목이 시간표에 남음";
            if (!tt.contains(c.receiveCourseOf(i).getCode())) return "받을 과목이 시간표에 없음";
        }
        Set<String> added = violations(w);
        added.removeAll(violationsBefore);
        if (!added.isEmpty()) return "확정 후 어긋남: " + added;
        if (allKnown && !codesBefore.equals(allCodes(w))) return "과목 수가 맞지 않음 (누가 과목을 잃거나 더 얻음)";
        Set<Integer> cancelledIds = new HashSet<>();
        for (SwapRequest r : cancelled) {
            cancelledIds.add(r.getId());
            if (!members.contains(r.getStudentId())) return "고리 밖 학생의 요청이 취소됨 #" + r.getId();
            if (r.getStatus() != RequestStatus.CANCELLED) return "취소 목록에 있는데 취소 상태가 아님";
        }
        for (SwapRequest r : w.board.all()) {
            RequestStatus was = statusBefore.get(r.getId());
            if (rs.contains(r) || was == r.getStatus()) continue;
            if (!(was == RequestStatus.WAITING && r.getStatus() == RequestStatus.CANCELLED && cancelledIds.contains(r.getId())))
                return "관계없는 요청의 상태가 바뀜 #" + r.getId() + " " + was + "->" + r.getStatus();
        }
        return null;
    }

    static Map<Integer, RequestStatus> statuses(W w) {
        Map<Integer, RequestStatus> out = new HashMap<>();
        for (SwapRequest r : w.board.all()) out.put(r.getId(), r.getStatus());
        return out;
    }

    static String confirmOne(W w, Random rnd) {
        List<CycleMatch> all = allCycles(w);
        if (all.isEmpty()) {
            List<SwapRequest> waiting = w.waiting();
            if (waiting.size() < 3) return null;
            Collections.shuffle(waiting, rnd);
            String before = w.snapshot();
            List<SwapRequest> random = waiting.subList(0, 3);
            if (w.tt(random.get(0)) == null) return null;
            String p = rejectedOrProblem(() -> w.exchange.confirmCycle(random));
            return p != null ? "고리가 아닌 세 요청: " + p : before.equals(w.snapshot()) ? null : "거절했는데 상태가 바뀜";
        }
        CycleMatch c = all.get(rnd.nextInt(all.size()));
        String before = w.snapshot();
        if (!c.isPossible()) {
            String p = rejectedOrProblem(() -> w.exchange.confirmCycle(c.getRequests()));
            refused++;
            return p != null ? "제외인 고리: " + p : before.equals(w.snapshot()) ? null : "거절했는데 상태가 바뀜";
        }
        Set<String> violationsBefore = violations(w);
        List<String> codesBefore = allCodes(w);
        Map<Integer, RequestStatus> statusBefore = statuses(w);
        List<SwapRequest> cancelled = w.exchange.confirmCycle(c.getRequests());
        confirmed++;
        String problem = checkConfirmed(w, c, violationsBefore, codesBefore, statusBefore, cancelled);
        if (problem != null) return problem;
        String after = w.snapshot();
        String again = rejectedOrProblem(() -> w.exchange.confirmCycle(c.getRequests()));
        return again != null ? "같은 고리를 두 번 확정: " + again : after.equals(w.snapshot()) ? null : "두 번째 거절에서 상태가 바뀜";
    }

    static String malformed(W w, Random rnd) {
        List<SwapRequest> everything = new ArrayList<>(w.board.all());
        if (everything.isEmpty()) return null;
        List<CycleMatch> all = allCycles(w);
        List<SwapRequest> bad = new ArrayList<>();
        int kind = rnd.nextInt(9);
        switch (kind) {
            case 0 -> { }
            case 1 -> bad.add(everything.get(rnd.nextInt(everything.size())));
            case 2 -> { for (int i = 0; i < 2; i++) bad.add(everything.get(rnd.nextInt(everything.size()))); }
            case 3 -> { for (int i = 0; i < 4 + rnd.nextInt(3); i++) bad.add(everything.get(rnd.nextInt(everything.size()))); }
            case 4 -> { for (int i = 0; i < 3; i++) bad.add(everything.get(rnd.nextInt(everything.size()))); bad.set(rnd.nextInt(3), null); }
            case 5 -> { SwapRequest r = everything.get(rnd.nextInt(everything.size())); bad.addAll(Arrays.asList(r, r, r)); }
            case 6 -> {
                if (all.isEmpty()) return null;
                bad.addAll(all.get(rnd.nextInt(all.size())).getRequests());
                Collections.swap(bad, 1, 2);
            }
            case 7 -> {
                if (all.isEmpty()) return null;
                for (SwapRequest r : all.get(rnd.nextInt(all.size())).getRequests())
                    bad.add(new SwapRequest(r.getId(), r.getStudentId(), r.getGiveCourse(), r.getWantName(), r.getStatus()));
            }
            default -> {
                if (all.isEmpty()) return null;
                bad.addAll(all.get(rnd.nextInt(all.size())).getRequests());
                SwapRequest r = bad.get(rnd.nextInt(3));
                bad.set(bad.indexOf(r), new SwapRequest(r.getId(), r.getStudentId(), r.getGiveCourse(), r.getWantName(),
                        rnd.nextBoolean() ? RequestStatus.MATCHED : RequestStatus.CANCELLED));
            }
        }

        if (kind == 6 && bad.get(0) != null && w.tt(bad.get(0)) != null)
            for (CycleMatch c : w.matcher.matchCycles(bad.get(0), w.tt(bad.get(0)))) if (c.getRequests().equals(bad)) return null;
        String before = w.snapshot();
        String p = rejectedOrProblem(() -> w.exchange.confirmCycle(bad));
        if (p != null) return "잘못된 목록(종류 " + kind + ", " + ids(bad) + "): " + p;
        if (kind != 1 && kind != 0) {
            String q = rejectedOrProblem(() -> w.exchange.confirmCycle(null));
            if (q != null) return "null 목록: " + q;
        }
        return before.equals(w.snapshot()) ? null : "거절했는데 상태가 바뀜";
    }

    static String sequence(W w, Random rnd) {
        for (int step = 0; step < 12; step++) {
            List<Runnable> moves = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<Object> what = new ArrayList<>();
            for (SwapRequest mine : w.waiting()) {
                Timetable tt = w.tt(mine);
                if (tt == null) continue;
                for (MatchResult m : w.matcher.match(mine, tt))
                    if (m.isPossible() || m.isResolvable()) {
                        moves.add(() -> w.exchange.confirm(mine, m.getRequest(), m.isResolvable()));
                        labels.add("둘이서 #" + mine.getId() + " #" + m.getRequest().getId() + (m.isResolvable() ? " (빼고)" : ""));
                        what.add(m);
                    }
                for (CycleMatch c : w.matcher.matchCycles(mine, tt))
                    if (c.isPossible()) {
                        moves.add(() -> w.exchange.confirmCycle(c.getRequests()));
                        labels.add("세 명 " + ids(c.getRequests()));
                        what.add(c);
                    }
            }
            if (moves.isEmpty()) return null;
            int pick = rnd.nextInt(moves.size());
            Set<String> before = violations(w);
            List<String> codes = allCodes(w);
            try {
                moves.get(pick).run();
            } catch (RequestRejectedException e) {
                return "가능이라더니 확정이 거절됨: " + labels.get(pick) + " (" + e.getReason() + ")";
            }
            Set<String> added = violations(w);
            added.removeAll(before);
            if (!added.isEmpty()) return labels.get(pick) + " 뒤 어긋남: " + added;
            boolean dropped = what.get(pick) instanceof MatchResult && ((MatchResult) what.get(pick)).isResolvable();
            boolean unknown = false;
            for (SwapRequest r : w.board.all()) unknown |= w.tt(r) == null;
            if (!dropped && !unknown && !codes.equals(allCodes(w))) return labels.get(pick) + " 뒤 과목 수가 맞지 않음";
        }
        return null;
    }

    static int dirNo = 0;
    static String roundTrip(W w, Random rnd) throws Exception {
        if (w.dirty) return null;
        List<CycleMatch> possible = new ArrayList<>();
        for (CycleMatch c : allCycles(w)) if (c.isPossible()) possible.add(c);
        if (!possible.isEmpty() && rnd.nextBoolean()) w.exchange.confirmCycle(possible.get(rnd.nextInt(possible.size())).getRequests());

        Path dir = base.resolve("rt" + (dirNo++ % 50));
        Files.createDirectories(dir);
        List<String> lines = new ArrayList<>();
        lines.add("코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원");
        for (Course c : w.courses) {
            List<String> times = new ArrayList<>();
            for (TimeSlot t : c.getTimes()) times.add(t.getDay().getLabel() + t.getStart() + "-" + t.getEnd());
            lines.add(c.getCode() + "," + c.getName() + "," + c.getSection() + "," + c.getType().name() + ","
                    + String.join(";", times) + ",교수,방," + c.getCredits() + ",0,0");
        }
        Files.write(dir.resolve("courses.csv"), lines, StandardCharsets.UTF_8);
        FileStorage out = new FileStorage(dir.toString());
        out.saveStudents(w.registry.all());
        out.saveRequests(w.board.all());

        W again = new W();
        FileStorage in = new FileStorage(dir.toString());
        again.courses.addAll(in.loadCourses());
        for (Course c : again.courses) again.catalog.add(c);
        for (Student s : in.loadStudents(again.courses)) again.registry.put(s);
        for (SwapRequest r : in.loadRequests(again.courses)) again.board.put(r);
        if (!w.snapshot().equals(again.snapshot())) return "다시 읽은 상태가 다름";
        for (SwapRequest mine : w.board.all()) {
            SwapRequest twin = again.board.find(mine.getId()).orElse(null);
            if (twin == null) return "요청이 사라짐 #" + mine.getId();
            if (!describe(w, mine).equals(describe(again, twin))) return "다시 읽은 뒤 탐색 결과가 다름 #" + mine.getId();
        }
        return null;
    }

    static String boardChanges(W w, Random rnd) {
        List<CycleMatch> all = allCycles(w);
        if (all.isEmpty()) return null;
        CycleMatch c = all.get(rnd.nextInt(all.size()));
        SwapRequest mine = c.getRequests().get(0);
        SwapRequest gone = c.getRequests().get(1 + rnd.nextInt(2));
        w.board.cancel(gone.getId());
        for (CycleMatch x : w.matcher.matchCycles(mine, w.tt(mine)))
            if (x.getRequests().contains(gone)) return "취소한 요청이 고리에 남음 #" + gone.getId();
        List<String> found = new ArrayList<>(), expected = new ArrayList<>();
        for (CycleMatch x : w.matcher.matchCycles(mine, w.tt(mine))) found.add(ids(x.getRequests()));
        for (List<SwapRequest> x : oracleCycles(w, mine)) expected.add(ids(x));
        return found.equals(expected) ? null : "취소 뒤 고리 목록이 다름: " + found + " / 정답 " + expected;
    }

    public static void main(String[] args) throws Exception {
        for (String a : args) verbose |= a.equals("-v");
        base = Files.createTempDirectory("kookyohwan-cycle");

        cases("1. 탐색 = 전수 조사 (목록·순서·판정)", 15000, 1_000_000L, CycleTest::searchMatchesOracle);
        cases("2. 고리의 모양", 5000, 2_000_000L, CycleTest::cycleShape);
        cases("3. 누구 기준으로 봐도 같은 고리", 5000, 3_000_000L, CycleTest::rotation);
        cases("4. 확정: 가능이면 되고, 제외면 거절", 10000, 4_000_000L, CycleTest::confirmOne);
        cases("5. 잘못된 목록으로 확정", 5000, 5_000_000L, CycleTest::malformed);
        cases("6. 둘이서·세 명 교환을 섞어 끝까지", 5000, 6_000_000L, CycleTest::sequence);
        cases("7. 저장 후 다시 읽기", 2000, 7_000_000L, CycleTest::roundTrip);
        cases("8. 요청 취소 뒤 고리", 3000, 8_000_000L, CycleTest::boardChanges);

        System.out.println("\n==================================================");
        System.out.println("통과 " + pass + " / 실패 " + fail + "  (전체 " + (pass + fail) + ")");
        System.out.println("1번에서 본 고리 " + cyclesSeen + "개 (가능 " + possibleSeen + "), 4번에서 확정 " + confirmed
                + "번 / 제외라 거절 " + refused + "번");
        for (Map.Entry<String, Integer> e : failCount.entrySet())
            System.out.println("  [" + e.getValue() + "건] " + e.getKey() + "\n        예) " + failFirst.get(e.getKey()));
        System.exit(fail == 0 ? 0 : 1);
    }
}
