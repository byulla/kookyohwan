import kookyohwan.model.*;
import kookyohwan.service.*;
import kookyohwan.storage.FileStorage;
import kookyohwan.ui.TextUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeirdTest {
    static int pass = 0, fail = 0;
    static boolean verbose = false;
    static final Map<String, Integer> failCount = new LinkedHashMap<>();
    static final Map<String, String> failFirst = new LinkedHashMap<>();
    static Path base;
    static int dirNo = 0;

    static final String[] ODD = {"", " ", "  ", "\t", "　", " ", "\"", "\"\"", ",", ";", "-", "#", "\\", "/", "'",
            "%s", "%n", "%d", "$1", "null", "NaN", "0", "-0", "00", "１２３", "٣", "9999999999999999999", "2147483647",
            "2147483648", "-2147483649", "1e9", "0x1F", "1.5", "+5", "😀", "👨‍👩‍👧", "\uD83D", "\uDE00", "é",
            "​", "‮", "﻿", "한", "漢字", "ａｂｃ", "월", "월3-4", "월99", "일1", "CONFIRMED", "WAITING",
            "GENERAL", "학번", "코드", "번호", "y", "n", "ㅛ", "ㅜ", "저장", "<b>", "&amp;", "'; DROP TABLE--", "../..",
            "\u0000", "\u0007", "\u001B[31m", "교양A", "교양B", "전공0", "20001001", "-20001001", "#1", "#0", "101",
            "999", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"};

    static String weird(Random rnd) {
        StringBuilder sb = new StringBuilder();
        int parts = rnd.nextInt(4);
        for (int i = 0; i < parts; i++) sb.append(ODD[rnd.nextInt(ODD.length)]);
        if (rnd.nextInt(4) == 0) {
            int n = rnd.nextInt(8);
            for (int i = 0; i < n; i++) {
                switch (rnd.nextInt(5)) {
                    case 0 -> sb.append((char) (0x20 + rnd.nextInt(95)));
                    case 1 -> sb.append((char) (0xAC00 + rnd.nextInt(11172)));
                    case 2 -> sb.appendCodePoint(0x1F300 + rnd.nextInt(0x300));
                    case 3 -> sb.append((char) (0x4E00 + rnd.nextInt(2000)));
                    default -> sb.append((char) rnd.nextInt(0x3000));
                }
            }
        }
        return sb.toString();
    }

    static String oneLine(String s) {
        return s.replaceAll("[\\n\\r\\u0085\\u2028\\u2029\\u000B\\u000C]", " ");
    }

    static boolean wellFormed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    interface Check { String run(long seed, Random rnd) throws Exception; }

    static void cases(String group, int count, long seedBase, Check check) {
        int before = fail;
        long started = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            long seed = seedBase + i;
            String problem;
            try {
                problem = check.run(seed, new Random(seed * 131 + 17));
            } catch (Throwable t) {
                StackTraceElement at = null;
                for (StackTraceElement e : t.getStackTrace()) if (e.getClassName().startsWith("kookyohwan")) { at = e; break; }
                problem = "예외 " + t.getClass().getSimpleName() + (at == null ? " @ " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "?")
                        : " @ " + at.getFileName() + ":" + at.getLineNumber());
            }
            if (problem == null) {
                pass++;
            } else {
                fail++;
                String kind = group + " / " + problem.replaceAll("'[^']*'", "'..'").replaceAll("-?\\d+", "N");
                kind = oneLine(kind.length() > 140 ? kind.substring(0, 140) : kind);
                failCount.merge(kind, 1, Integer::sum);
                failFirst.putIfAbsent(kind, "씨앗 " + seed + ": " + oneLine(problem.length() > 300 ? problem.substring(0, 300) : problem));
                if (verbose) System.out.println("  FAIL " + group + " 씨앗 " + seed + ": " + oneLine(problem));
            }
        }
        System.out.printf("%-52s %6d개  실패 %-5d (%.1f초)%n", group, count, fail - before, (System.currentTimeMillis() - started) / 1000.0);
    }

    static String csv(String v) {
        return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    static List<String> courseLines(List<Course> courses) {
        List<String> lines = new ArrayList<>();
        lines.add("코드,과목명,분반,구분,시간,교수,강의실,학점,정원,수강인원");
        for (Course c : courses) {
            List<String> times = new ArrayList<>();
            for (TimeSlot t : c.getTimes()) times.add(t.getDay().getLabel() + t.getStart() + "-" + t.getEnd());
            lines.add(csv(c.getCode()) + "," + csv(c.getName()) + "," + csv(c.getSection()) + "," + c.getType().name() + ","
                    + String.join(";", times) + ",교수,방," + c.getCredits() + "," + c.getCapacity() + "," + c.getBaseEnrolled());
        }
        return lines;
    }

    static Path freshDir() throws IOException {
        Path dir = base.resolve("w" + (dirNo++ % 64));
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> old = Files.newDirectoryStream(dir)) {
                for (Path p : old) Files.delete(p);
            }
        }
        Files.createDirectories(dir);
        return dir;
    }

    static void write(Path dir, CycleTest.W w) throws IOException {
        Files.write(dir.resolve("courses.csv"), courseLines(w.courses), StandardCharsets.UTF_8);
        FileStorage out = new FileStorage(dir.toString());
        out.saveStudents(w.registry.all());
        out.saveRequests(w.board.all());
    }

    static CycleTest.W load(Path dir) throws IOException {
        CycleTest.W w = new CycleTest.W();
        FileStorage in = new FileStorage(dir.toString());
        w.courses.addAll(in.loadCourses());
        for (Course c : w.courses) w.catalog.add(c);
        for (Student s : in.loadStudents(w.courses)) w.registry.put(s);
        for (SwapRequest r : in.loadRequests(w.courses)) w.board.put(r);
        return w;
    }

    static CycleTest.W clean(long seed) {
        long s = seed;
        CycleTest.W w = CycleTest.world(s);
        while (w.dirty) w = CycleTest.world(s += 1_000_003L);
        return w;
    }

    static synchronized String[] console(Path dir, List<String> lines) {
        PrintStream originalOut = System.out;
        InputStream originalIn = System.in;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Throwable thrown = null;
        try {
            System.setIn(new ByteArrayInputStream((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            kookyohwan.Main.main(new String[]{dir.toString()});
        } catch (Throwable e) {
            thrown = e;
        } finally {
            System.setOut(originalOut);
            System.setIn(originalIn);
        }
        return new String[]{buffer.toString(StandardCharsets.UTF_8), thrown == null ? null : thrown.toString()};
    }

    static String sane(CycleTest.W w) throws IOException {
        return sane(w, true);
    }

    static String sane(CycleTest.W w, boolean roundTrip) throws IOException {
        EnrollmentService enroll = new EnrollmentService(w.registry);
        for (Student s : w.registry.all()) {
            long sum = 0;
            for (Course c : s.getTimetable().getCourses()) sum += c.getCredits();
            if (s.getTimetable().getTotalCredits() != Math.min(sum, Integer.MAX_VALUE)) return "학점 합이 틀림(넘침): " + s.getTimetable().getTotalCredits() + " / 실제 " + sum;
        }
        for (Course c : w.courses) {
            long count = c.getBaseEnrolled();
            for (Student s : w.registry.all()) if (s.getTimetable().contains(c.getCode())) count++;
            if (enroll.enrolledOf(c) != Math.min(count, Integer.MAX_VALUE)) return "수강인원이 틀림(넘침): " + enroll.enrolledOf(c) + " / 실제 " + count;
            if (enroll.vacancyOf(c) != Math.max(0, c.getCapacity() - count)) return "빈자리가 틀림: " + enroll.vacancyOf(c);
            if (enroll.describe(c).contains("-")) return "빈자리 설명에 음수: " + enroll.describe(c);
        }
        for (SwapRequest r : w.board.all()) {
            Timetable tt = w.tt(r);
            if (tt == null) continue;
            w.matcher.match(r, tt);
            w.matcher.matchCycles(r, tt);
        }
        if (!roundTrip) return null;
        Path again = base.resolve("again");
        Files.createDirectories(again);
        write(again, w);
        CycleTest.W back;
        try {
            back = load(again);
        } catch (IOException e) {
            return "읽힌 것을 저장했더니 다시 못 읽음: " + e.getMessage();
        }
        return w.snapshot().equals(back.snapshot()) ? null : "저장 후 다시 읽은 상태가 다름";
    }

    static String textUtil(long seed, Random rnd) {
        String s = weird(rnd);
        int width = rnd.nextInt(46) - 5;
        int own = TextUtil.displayWidth(s);
        String cut = TextUtil.cut(s, width);
        if (own <= width && !cut.equals(s)) return "들어가는데 잘림: '" + s + "' 폭 " + width;
        if (own > width && TextUtil.displayWidth(cut) > Math.max(0, width)) return "cut 결과가 폭을 넘음: 폭 " + width + ", 결과 폭 " + TextUtil.displayWidth(cut);
        if (wellFormed(s) && !wellFormed(cut)) return "cut이 글자를 반으로 쪼갬 (이모지 등)";
        String pad = TextUtil.padRight(s, width);
        if (!pad.startsWith(s) || TextUtil.displayWidth(pad) != Math.max(own, width)) return "padRight 폭이 틀림";
        String center = TextUtil.center(s, width);
        if (!center.contains(s) || TextUtil.displayWidth(center) != Math.max(own, width)) return "center 폭이 틀림";
        return null;
    }

    static final Pattern GOOD_TIME = Pattern.compile("([월화수목금토일])([0-9]{1,2})(?:-([0-9]{1,2}))?");

    static String timeSlot(long seed, Random rnd) {
        String text;
        String day = rnd.nextInt(5) == 0 ? weird(rnd) : String.valueOf("월화수목금토일월화수".charAt(rnd.nextInt(10)));
        switch (rnd.nextInt(6)) {
            case 0 -> text = weird(rnd);
            case 1 -> text = day + rnd.nextInt(120);
            case 2 -> text = day + rnd.nextInt(20) + "-" + rnd.nextInt(20);
            case 3 -> text = (rnd.nextBoolean() ? " " : "") + day + weird(rnd) + (rnd.nextBoolean() ? "-" + weird(rnd) : "");
            case 4 -> text = day + (rnd.nextInt(30) - 5) + "-" + (rnd.nextInt(30) - 5) + (rnd.nextBoolean() ? "" : weird(rnd));
            default -> text = day + rnd.nextInt(15) + (rnd.nextBoolean() ? "-" : "~") + rnd.nextInt(15) + (rnd.nextInt(4) == 0 ? "\t" : "");
        }
        Matcher m = GOOD_TIME.matcher(text.trim());
        boolean valid = false;
        int start = 0, end = 0;
        if (m.matches()) {
            start = Integer.parseInt(m.group(2));
            end = m.group(3) == null ? start : Integer.parseInt(m.group(3));
            valid = start >= 1 && end >= start;
        }
        TimeSlot slot;
        try {
            slot = TimeSlot.parse(text);
        } catch (IllegalArgumentException e) {
            return valid ? "맞는 표기를 거절: '" + text + "'" : null;
        }
        if (!valid) return "틀린 표기를 받아들임: '" + text + "' -> " + slot;
        if (slot.getStart() != start || slot.getEnd() != end || !slot.getDay().getLabel().equals(m.group(1))) return "다르게 읽음: '" + text + "' -> " + slot;
        TimeSlot back = TimeSlot.parse(slot.toString().replace(" ", ""));
        return back.getDay() == slot.getDay() && back.getStart() == start && back.getEnd() == end ? null : "toString을 다시 읽으면 다름";
    }

    static List<Course> oddCourses(Random rnd) {
        int[] credits = {0, 1, 2, 3, 3, 6, 9, 18, 19};
        String[] names = {"가", "나", "다", "가", "라 ,\"", "😀"};
        List<Course> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            List<TimeSlot> times = new ArrayList<>();
            int slots = rnd.nextInt(4);
            for (int k = 0; k < slots; k++) {
                int start = rnd.nextInt(10) == 0 ? 90 + rnd.nextInt(8) : 1 + rnd.nextInt(8);
                times.add(new TimeSlot(Day.values()[rnd.nextInt(7)], start, start + rnd.nextInt(3)));
            }
            String name = names[rnd.nextInt(names.length)];
            String section = rnd.nextInt(5) == 0 ? "" : String.format("%03d", 1 + rnd.nextInt(3));
            String prefix = rnd.nextBoolean() ? "7" + names.length + name.hashCode() % 7 : "7" + i;
            out.add(new Course(prefix + "x" + i + section, name, section, CourseType.values()[rnd.nextInt(4)], times,
                    "", "", credits[rnd.nextInt(credits.length)], 0, 0));
        }
        return out;
    }

    static String timetableModel(long seed, Random rnd) {
        List<Course> pool = oddCourses(rnd);
        Timetable tt = new Timetable();
        List<Course> ref = new ArrayList<>();
        boolean restored = false;
        for (int step = 0; step < 30; step++) {
            Course c = pool.get(rnd.nextInt(pool.size()));
            int op = rnd.nextInt(10);
            boolean got, want;
            String what;
            if (op < 4) {
                what = "add";
                want = canReplace(ref, Collections.emptyList(), c);
                if (want) ref.add(c);
                got = tt.add(c);
            } else if (op < 7) {
                List<Course> out = new ArrayList<>();
                for (Course x : pool) if (rnd.nextInt(4) == 0) out.add(x);
                what = "replace";
                want = canReplace(ref, out, c);
                if (want) {
                    ref.removeIf(x -> hasCode(out, x.getCode()));
                    ref.add(c);
                }
                got = tt.replace(out, c);
            } else if (op < 9) {
                what = "remove";
                want = ref.removeIf(x -> x.getCode().equals(c.getCode()));
                got = tt.remove(c);
            } else {
                what = "restore";
                restored = true;
                want = !hasCode(ref, c.getCode());
                for (Course x : ref) want &= !CycleTest.clash(x, c);
                if (want) ref.add(c);
                got = tt.restore(c);
            }
            if (got != want) return what + " 결과가 다름: " + got + " / 정답 " + want + " (" + step + "번째)";
            if (!codes(ref).equals(codes(tt.getCourses()))) return what + " 뒤 시간표가 다름";
            long sum = 0;
            for (Course x : ref) sum += x.getCredits();
            if (tt.getTotalCredits() != sum) return "학점 합이 다름";
            if (!restored && sum > Timetable.MAX_CREDITS) return "18학점을 넘김";
            if (tt.size() != ref.size()) return "과목 수가 다름";
        }
        return null;
    }

    static boolean hasCode(List<Course> list, String code) {
        for (Course c : list) if (c.getCode().equals(code)) return true;
        return false;
    }
    static List<String> codes(List<Course> list) {
        List<String> out = new ArrayList<>();
        for (Course c : list) out.add(c.getCode());
        return out;
    }
    static boolean canReplace(List<Course> ref, List<Course> out, Course in) {
        long credits = in.getCredits();
        for (Course x : ref) {
            if (hasCode(out, x.getCode())) continue;
            credits += x.getCredits();
            if (CycleTest.subject(x).equals(CycleTest.subject(in)) || CycleTest.clash(x, in)) return false;
        }
        return credits <= Timetable.MAX_CREDITS;
    }

    static final String[] FILES = {"courses.csv", "students.csv", "requests.csv"};

    static String brokenCsv(long seed, Random rnd) throws Exception {
        Path dir = freshDir();
        write(dir, clean(seed));
        int hits = 1 + rnd.nextInt(3);
        for (int h = 0; h < hits; h++) {
            Path file = dir.resolve(FILES[rnd.nextInt(3)]);
            List<String> lines;
            try {
                lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException alreadyBroken) {
                break;
            }
            int at = lines.isEmpty() ? 0 : rnd.nextInt(lines.size());
            switch (rnd.nextInt(14)) {
                case 0 -> { if (!lines.isEmpty()) lines.remove(at); }
                case 1 -> { if (!lines.isEmpty()) lines.add(at, lines.get(rnd.nextInt(lines.size()))); }
                case 2 -> lines.add(at, weird(rnd));
                case 3 -> { if (!lines.isEmpty()) lines.set(at, cell(lines.get(at), rnd, weird(rnd))); }
                case 4 -> { if (!lines.isEmpty()) lines.set(at, cell(lines.get(at), rnd, ODD[rnd.nextInt(ODD.length)])); }
                case 5 -> { if (!lines.isEmpty()) { String l = lines.get(at); lines.set(at, l.substring(0, rnd.nextInt(l.length() + 1))); } }
                case 6 -> { if (!lines.isEmpty()) lines.set(0, "﻿" + lines.get(0)); }
                case 7 -> { if (!lines.isEmpty()) lines.set(at, lines.get(at) + "," + weird(rnd)); }
                case 8 -> { if (!lines.isEmpty()) lines.set(at, lines.get(at).replace(",", rnd.nextBoolean() ? ";" : ",,")); }
                case 9 -> { if (!lines.isEmpty()) { String l = lines.get(at); int p = rnd.nextInt(l.length() + 1); lines.set(at, l.substring(0, p) + "\"" + l.substring(p)); } }
                case 10 -> Collections.shuffle(lines, rnd);
                case 11 -> lines.clear();
                case 12 -> { if (!lines.isEmpty()) lines.set(at, cell(lines.get(at), rnd, String.valueOf(rnd.nextBoolean() ? Integer.MAX_VALUE : rnd.nextInt(2_000_000_000)))); }
                default -> { if (!lines.isEmpty()) lines.set(at, lines.get(at).toLowerCase(Locale.ROOT)); }
            }
            if (rnd.nextInt(12) == 0) {
                byte[] raw = String.join(rnd.nextBoolean() ? "\r\n" : "\r", lines).getBytes(StandardCharsets.UTF_8);
                if (raw.length > 0 && rnd.nextBoolean()) raw[rnd.nextInt(raw.length)] = (byte) rnd.nextInt(256);
                Files.write(file, raw);
            } else if (rnd.nextInt(40) == 0) {
                Files.delete(file);
            } else {
                Files.write(file, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        CycleTest.W loaded;
        try {
            loaded = load(dir);
        } catch (IOException e) {
            return e.getMessage() == null || e.getMessage().isEmpty() ? "거절은 했는데 이유가 없음" : null;
        }
        return sane(loaded);
    }

    static String cell(String line, Random rnd, String value) {
        String[] cells = line.split(",", -1);
        cells[rnd.nextInt(cells.length)] = value;
        return String.join(",", cells);
    }

    static String oddNames(long seed, Random rnd) throws Exception {
        CycleTest.W w = clean(seed);
        Path dir = freshDir();
        Map<String, String> expect = new LinkedHashMap<>();
        boolean viaApi = rnd.nextInt(3) == 0;
        for (int i = 0; i < 3; i++) {
            String raw = weird(rnd) + (rnd.nextInt(3) == 0 ? weird(rnd) : "");
            if (viaApi && rnd.nextBoolean()) raw = raw + (rnd.nextBoolean() ? "\n" : "\r\n") + weird(rnd) + ",";

            String name = viaApi ? raw : raw.trim();
            String flat = whole(oneLineStrict(name)).trim();
            if (flat.isEmpty() || (!viaApi && (name.contains(",") || !name.equals(flat)))) continue;
            String id = String.valueOf(500 + i) + (rnd.nextInt(4) == 0 ? "00000000000000000000" : "");
            w.registry.register(id, name);
            expect.put(id, flat);
        }
        if (rnd.nextInt(4) == 0) {
            String code = (rnd.nextBoolean() ? "9" : "") + ODD[rnd.nextInt(ODD.length)] + (rnd.nextBoolean() ? "7" : "");
            if (!oneLineStrict(code).equals(code) || !wellFormed(code) || code.trim().isEmpty() || w.catalog.findByCode(code.trim()).isPresent()) return null;
            Course odd = new Course(code.trim(), "교양A", "001", CourseType.GENERAL, Collections.emptyList(), "교수", "방", 1, 0, 0);
            w.courses.add(odd);
            Files.write(dir.resolve("courses.csv"), courseLines(w.courses), StandardCharsets.UTF_8);
            CycleTest.W got;
            try {
                got = load(dir);
            } catch (IOException e) {
                return null;
            }
            Course read = got.catalog.findByCode(code.trim()).orElse(null);
            if (read == null) return "받아들인 과목 코드가 사라짐 '" + code + "'";
            Student s = got.registry.register("777", "코드시험");
            s.getTimetable().restore(read);
            s.confirmTimetable();
            got.board.put(new SwapRequest(900, "777", read, "교양B", RequestStatus.WAITING));
            write(dir, got);
            try {
                CycleTest.W back = load(dir);
                return got.snapshot().equals(back.snapshot()) ? null : "과목 코드 '" + code + "' 저장 후 상태가 다름";
            } catch (IOException e) {
                return "과목 코드 '" + code + "'를 받아 놓고 저장한 파일을 못 읽음: " + e.getMessage();
            }
        }
        write(dir, w);
        CycleTest.W back;
        try {
            back = load(dir);
        } catch (IOException e) {
            return "이름 때문에 저장한 파일을 못 읽음: " + e.getMessage() + " / 이름들 " + expect.values();
        }
        for (Map.Entry<String, String> e : expect.entrySet()) {
            Student s = back.registry.find(e.getKey()).orElse(null);
            if (s == null) return "학생이 사라짐";
            if (!s.getName().equals(e.getValue())) return "이름이 바뀜: '" + e.getValue() + "' -> '" + s.getName() + "'";
        }
        return back.registry.size() == w.registry.size() ? null : "학생 수가 다름";
    }

    static String whole(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) sb.append(ch).append(s.charAt(++i));
            else sb.append(Character.isSurrogate(ch) ? '?' : ch);
        }
        return sb.toString();
    }

    static String oneLineStrict(String s) {
        return s.replaceAll("[\\n\\r]", " ");
    }

    static String requestIds(long seed, Random rnd) throws Exception {
        CycleTest.W w = clean(seed);
        int[] edges = {999_999_999, 999_999_998, 999_999_990, 1, 500_000_000, 999_999_999};
        int id = edges[rnd.nextInt(edges.length)];
        if (w.board.find(id).isPresent()) return null;
        Course any = w.courses.get(rnd.nextInt(w.courses.size()));
        w.board.put(new SwapRequest(id, "101", any, "교양A", rnd.nextBoolean() ? RequestStatus.WAITING : RequestStatus.CANCELLED));
        Path dir = freshDir();
        write(dir, w);
        CycleTest.W loaded = load(dir);
        int registered = 0;
        for (int i = 0; i < 3; i++) {
            Student s = loaded.registry.register(String.valueOf(600 + i), "새학생" + i);
            Course give = loaded.catalog.findByCode("20003001").get();
            s.getTimetable().add(give);
            s.confirmTimetable();
            try {
                SwapRequest r = loaded.board.register(s, give, i % 2 == 0 ? "교양D" : "교양E");
                registered++;
                if (r.getId() < 1) return "요청 번호가 0 이하: " + r.getId();
            } catch (RequestRejectedException e) {

            }
        }
        write(dir, loaded);
        try {
            CycleTest.W back = load(dir);
            return loaded.snapshot().equals(back.snapshot()) ? null : "다시 읽은 상태가 다름";
        } catch (IOException e) {
            return "요청 #" + id + " 뒤에 " + registered + "건 등록하고 저장했더니 못 읽음: " + e.getMessage();
        }
    }

    static String bigNumbers(long seed, Random rnd) throws Exception {
        int[] big = {0, 1, 3, 18, 19, 99, 100, 1000, 65536, 1 << 30, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            courses.add(new Course("8000" + i + "001", "큰과목" + i, "001", CourseType.GENERAL,
                    Collections.singletonList(new TimeSlot(Day.values()[i], 1, 2)), "교수", "방",
                    big[rnd.nextInt(big.length)], big[rnd.nextInt(big.length)], big[rnd.nextInt(big.length)]));
        CycleTest.W w = new CycleTest.W();
        boolean fromFile = rnd.nextBoolean();
        if (fromFile) {
            Path dir = freshDir();
            Files.write(dir.resolve("courses.csv"), courseLines(courses), StandardCharsets.UTF_8);
            try {
                w = load(dir);
            } catch (IOException e) {
                return null;
            }
        } else {
            w.courses.addAll(courses);
            for (Course c : courses) w.catalog.add(c);
        }
        for (int i = 0; i < 3; i++) {
            Student s = w.registry.register(String.valueOf(700 + i), "큰" + i);
            for (Course c : w.courses) {
                if (rnd.nextBoolean()) continue;
                long before = 0;
                for (Course x : s.getTimetable().getCourses()) before += x.getCredits();
                boolean over = before + c.getCredits() > Timetable.MAX_CREDITS;
                if (s.getTimetable().exceedsLimit(c, Collections.emptyList()) != over) return "학점 초과 판정이 틀림: " + before + " + " + c.getCredits();
                if (s.getTimetable().add(c) == over) return "학점 " + before + " + " + c.getCredits() + " 인데 추가 결과가 반대";
                if (rnd.nextInt(3) == 0) s.getTimetable().restore(c);
            }
        }
        return sane(w, fromFile);
    }

    static String consoleMash(long seed, Random rnd) throws Exception {
        CycleTest.W before = clean(seed);
        Path dir = freshDir();
        write(dir, before);
        List<String> pool = new ArrayList<>(Arrays.asList("1", "2", "2", "3", "3", "4", "5", "5", "9", "0", "y", "y", "n", "", "",
                "저장", "ㅛ", "ㅜ", "6", "7", "8", "-1", "99", "１", "2 ", " 3", "y y", "Y", "N", "yes"));
        for (Course c : before.courses) { pool.add(c.getCode()); pool.add("-" + c.getCode()); pool.add(c.getName()); }
        for (Student s : before.registry.all()) pool.add(s.getId());
        for (SwapRequest r : before.board.all()) { pool.add(String.valueOf(r.getId())); pool.add("#" + r.getId()); }
        List<String> script = new ArrayList<>();
        script.add(rnd.nextInt(5) == 0 ? String.valueOf(800 + rnd.nextInt(5)) : String.valueOf(101 + rnd.nextInt(before.registry.size())));
        if (rnd.nextInt(5) == 0) script.set(0, oneLine(weird(rnd)));
        int length = 10 + rnd.nextInt(50);
        while (script.size() < length)
            script.add(rnd.nextInt(3) == 0 ? oneLine(weird(rnd)) : pool.get(rnd.nextInt(pool.size())));
        if (rnd.nextBoolean()) script.add("0");

        String[] run = console(dir, script);
        if (run[1] != null) return "프로그램이 죽음: " + run[1];
        if (run[0].contains("Exception") || run[0].contains("\tat ")) return "화면에 예외가 보임";
        CycleTest.W after;
        try {
            after = load(dir);
        } catch (IOException e) {
            return "실행 뒤 파일을 못 읽음: " + e.getMessage();
        }
        Set<String> added = CycleTest.violations(after);
        added.removeAll(CycleTest.violations(before));
        if (!added.isEmpty()) return "실행 뒤 어긋남: " + added;
        for (Student s : before.registry.all()) if (after.registry.find(s.getId()).isEmpty()) return "학생이 사라짐";
        for (SwapRequest r : before.board.all()) if (after.board.find(r.getId()).isEmpty()) return "요청이 사라짐";
        String[] again = console(dir, Arrays.asList(after.registry.all().get(0).getId(), "0"));
        if (again[1] != null) return "다시 실행하니 죽음: " + again[1];
        if (!again[0].contains("구교환을 종료합니다.")) return "다시 실행했는데 정상 종료하지 않음";
        return after.snapshot().equals(load(dir).snapshot()) ? null : "아무것도 안 했는데 상태가 바뀜";
    }

    static String oddArguments(long seed, Random rnd) {
        CycleTest.W w = CycleTest.world(seed);
        List<Student> students = w.registry.all();
        List<SwapRequest> requests = w.board.all();
        Student s = students.get(rnd.nextInt(students.size()));
        Course any = w.courses.get(rnd.nextInt(w.courses.size()));
        String odd = rnd.nextInt(10) == 0 ? null : weird(rnd);
        String before = w.snapshot();
        Set<String> violationsBefore = CycleTest.violations(w);
        int[] oddIds = {0, -1, 1, Integer.MAX_VALUE, Integer.MIN_VALUE, 999_999_999, rnd.nextInt()};
        switch (rnd.nextInt(8)) {
            case 0 -> {
                try {
                    SwapRequest r = w.board.register(s, any, odd);
                    if (!r.isWaiting() || !any.isGeneral() || !s.getTimetable().contains(any.getCode()) || !w.catalog.existsName(r.getWantName()))
                        return "말이 안 되는 요청이 등록됨: " + r.summary();
                } catch (RequestRejectedException e) {
                    if (!before.equals(w.snapshot())) return "거절했는데 상태가 바뀜";
                }
            }
            case 1 -> {
                if (odd == null) return null;
                List<Course> found = w.catalog.search(odd);
                String key = odd.toLowerCase(Locale.ROOT);
                int expected = 0;
                for (Course c : w.courses)
                    if (c.getName().toLowerCase(Locale.ROOT).contains(key) || c.getCode().toLowerCase(Locale.ROOT).startsWith(key)
                            || c.getProfessor().toLowerCase(Locale.ROOT).contains(key)) expected++;
                if (found.size() != expected) return "검색 결과 수가 다름 '" + odd + "'";
                if (w.catalog.search("").size() != w.courses.size()) return "빈 검색어가 전체가 아님";
                Optional<String> name = w.catalog.resolveName(odd);
                if (name.isPresent() && !w.catalog.existsName(name.get())) return "없는 과목명으로 바꿔 줌";
            }
            case 2, 3 -> {
                SwapRequest made = new SwapRequest(oddIds[rnd.nextInt(oddIds.length)], rnd.nextBoolean() ? s.getId() : String.valueOf(odd),
                        any, rnd.nextBoolean() ? NAME(rnd) : String.valueOf(odd), RequestStatus.values()[rnd.nextInt(3)]);
                Timetable tt = rnd.nextInt(5) == 0 ? new Timetable() : s.getTimetable();
                List<MatchResult> pairs = w.matcher.match(made, tt);
                for (MatchResult m : pairs) {
                    if (m.getRequest().getStudentId().equals(made.getStudentId())) return "자기 요청과 매칭됨";
                    if (!m.getRequest().isWaiting()) return "대기가 아닌 요청과 매칭됨";
                    if (m.isPossible() && !tt.contains(any.getCode())) return "내놓는 과목이 없는데 가능";
                }
                for (CycleMatch c : w.matcher.matchCycles(made, tt)) {
                    if (c.getRequests().size() != 3 || c.getRequests().get(0) != made) return "지어낸 요청의 고리 모양이 이상함";
                    if (c.isPossible() && !tt.contains(any.getCode())) return "내놓는 과목이 없는데 고리가 가능";
                    Set<String> who = new HashSet<>();
                    for (SwapRequest r : c.getRequests()) if (!who.add(r.getStudentId())) return "같은 학생이 고리에 두 번";
                }
                if (!before.equals(w.snapshot())) return "매칭이 상태를 바꿈";
            }
            case 4 -> {
                if (requests.isEmpty()) return null;
                SwapRequest other = requests.get(rnd.nextInt(requests.size()));
                SwapRequest made = rnd.nextBoolean() ? requests.get(rnd.nextInt(requests.size()))
                        : new SwapRequest(oddIds[rnd.nextInt(oddIds.length)], s.getId(), any, other.getGiveCourse().getName(), RequestStatus.WAITING);
                boolean onBoard = requests.contains(made);
                try {
                    w.exchange.confirm(made, other, rnd.nextBoolean());
                    if (!onBoard) return "요청 목록에 없는 요청으로 교환이 확정됨";
                } catch (RequestRejectedException e) {
                    if (!before.equals(w.snapshot())) return "거절했는데 상태가 바뀜";
                }
            }
            case 5 -> {
                if (requests.size() < 2) return null;
                List<SwapRequest> three = new ArrayList<>();
                three.add(new SwapRequest(oddIds[rnd.nextInt(oddIds.length)], s.getId(), any, NAME(rnd), RequestStatus.WAITING));
                three.add(requests.get(rnd.nextInt(requests.size())));
                three.add(requests.get(rnd.nextInt(requests.size())));
                Collections.shuffle(three, rnd);
                try {
                    w.exchange.confirmCycle(three);
                    return "요청 목록에 없는 요청이 낀 세 명 교환이 확정됨";
                } catch (RequestRejectedException e) {
                    if (!before.equals(w.snapshot())) return "거절했는데 상태가 바뀜";
                }
            }
            case 6 -> {
                int id = oddIds[rnd.nextInt(oddIds.length)];
                boolean exists = w.board.find(id).isPresent();
                boolean waiting = exists && w.board.find(id).get().isWaiting();
                boolean done = rnd.nextBoolean() ? w.board.cancel(id) : w.board.complete(id);
                if (done != waiting) return "없는/끝난 요청 번호 " + id + " 처리 결과가 이상함";
            }
            default -> {
                EnrollmentService enroll = new EnrollmentService(w.registry);
                try {
                    enroll.validateEnroll(s, any);
                } catch (RequestRejectedException e) {
                    if (!enroll.isFull(any)) return "빈자리가 있는데 마감이라고 거절";
                }
                if (w.registry.find(String.valueOf(odd)).isPresent() && odd != null && !odd.matches("[0-9]+")) return "이상한 학번이 찾아짐";
                try {
                    w.registry.register(s.getId(), "덮어쓰기");
                    return "있는 학번을 다시 등록함";
                } catch (IllegalArgumentException e) {
                    if (!before.equals(w.snapshot())) return "거절했는데 상태가 바뀜";
                }
            }
        }
        Set<String> added = CycleTest.violations(w);
        added.removeAll(violationsBefore);
        return added.isEmpty() ? null : "어긋남이 생김: " + added;
    }

    static String NAME(Random rnd) {
        return CycleTest.NAMES[rnd.nextInt(CycleTest.NAMES.length)];
    }

    static String supposeEqualsReal(long seed, Random rnd) {
        CycleTest.W w = clean(seed);
        List<Student> students = w.registry.all();
        Student s = students.get(rnd.nextInt(students.size()));
        Course give = CycleTest.anyGeneral(s, rnd);
        List<SwapRequest> others = new ArrayList<>();
        for (SwapRequest r : w.waiting()) if (!r.getStudentId().equals(s.getId())) others.add(r);
        if (give == null || others.isEmpty()) return null;
        String want = others.get(rnd.nextInt(others.size())).getGiveCourse().getName();
        SwapRequest made = new SwapRequest(0, s.getId(), give, want, RequestStatus.WAITING);
        String before = w.snapshot();
        String supposed = picture(w, made, s);
        if (!before.equals(w.snapshot())) return "가정만 했는데 상태가 바뀜";
        int next = 1;
        for (SwapRequest r : w.board.all()) next = Math.max(next, r.getId() + 1);
        SwapRequest real = new SwapRequest(next, s.getId(), give, want, RequestStatus.WAITING);
        w.board.put(real);
        String actual = picture(w, real, s);
        return supposed.equals(actual) ? null : "가정한 결과와 실제 등록한 결과가 다름: " + supposed + " / " + actual;
    }

    static String picture(CycleTest.W w, SwapRequest mine, Student s) {
        StringBuilder sb = new StringBuilder();
        for (MatchResult m : w.matcher.match(mine, s.getTimetable()))
            sb.append("#").append(m.getRequest().getId()).append(m.getVerdict()).append(m.isResolvable()).append(m.getReason()).append(";");
        for (CycleMatch c : w.matcher.matchCycles(mine, s.getTimetable()))
            sb.append("#").append(c.getRequests().get(1).getId()).append("#").append(c.getRequests().get(2).getId())
                    .append(c.getVerdict()).append(c.getReason()).append(";");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        for (String a : args) verbose |= a.equals("-v");
        base = Files.createTempDirectory("kookyohwan-weird");

        cases("1. 화면 폭 계산에 이상한 글자", 10000, 10_000_000L, WeirdTest::textUtil);
        cases("2. 이상한 시간 표기", 10000, 11_000_000L, WeirdTest::timeSlot);
        cases("3. 이상한 과목으로 시간표 아무 조작", 10000, 12_000_000L, WeirdTest::timetableModel);
        cases("4. CSV 파일 망가뜨리기", 15000, 13_000_000L, WeirdTest::brokenCsv);
        cases("5. 이름·과목 코드에 이상한 글자", 10000, 14_000_000L, WeirdTest::oddNames);
        cases("6. 요청 번호의 끝", 5000, 15_000_000L, WeirdTest::requestIds);
        cases("7. 터무니없이 큰 숫자", 5000, 16_000_000L, WeirdTest::bigNumbers);
        cases("8. 콘솔을 아무렇게나 두드리기", 15000, 17_000_000L, WeirdTest::consoleMash);
        cases("9. 서비스에 엉뚱한 인자", 10000, 18_000_000L, WeirdTest::oddArguments);
        cases("10. 가정한 요청 = 실제 등록한 요청", 10000, 19_000_000L, WeirdTest::supposeEqualsReal);

        System.out.println("\n==================================================");
        System.out.println("통과 " + pass + " / 실패 " + fail + "  (전체 " + (pass + fail) + ")");
        for (Map.Entry<String, Integer> e : failCount.entrySet())
            System.out.println("  [" + e.getValue() + "건] " + e.getKey() + "\n        예) " + failFirst.get(e.getKey()));
        System.exit(fail == 0 ? 0 : 1);
    }
}
