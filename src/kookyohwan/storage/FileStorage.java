package kookyohwan.storage;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.RequestStatus;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.TimeSlot;
import kookyohwan.model.Timetable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileStorage implements Storage {
    public static final String COURSES_FILE = "courses.csv";
    public static final String STUDENTS_FILE = "students.csv";
    public static final String REQUESTS_FILE = "requests.csv";

    private static final String STUDENTS_HEADER = "학번,이름,과목코드,시간표상태";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String EDITING = "EDITING";
    private static final String REQUESTS_HEADER = "번호,학번,내놓는과목코드,희망과목명,상태";
    private static final int MAX_REQUEST_ID = SwapRequest.MAX_ID;
    private static final int MAX_CREDITS_VALUE = 99;
    private static final int MAX_PEOPLE = 999_999;
    private static final String BOM = "\uFEFF";

    private final Path dir;

    public FileStorage(String dataDir) {
        this.dir = Paths.get(dataDir);
    }

    @Override
    public List<Course> loadCourses() throws IOException {
        List<Course> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row row : readRows(COURSES_FILE, "코드", 5, 10, true)) {
            if (row.cells[0].isEmpty()) {
                throw row.error("과목 코드가 비어 있습니다");
            }
            if (!seen.add(row.cells[0])) {
                throw row.error("과목 코드가 중복됩니다 " + row.cells[0]);
            }
            if (row.cells[0].matches(".*[,;\"].*")) {
                throw row.error("과목 코드에는 쉼표·세미콜론·큰따옴표를 쓸 수 없습니다 (" + row.cells[0] + ")");
            }
            if (row.cells[1].isEmpty()) {
                throw row.error("과목명이 비어 있습니다");
            }
            try {
                List<TimeSlot> times = new ArrayList<>();
                for (String t : splitList(row.cells[4])) {
                    times.add(TimeSlot.parse(t));
                }
                String professor = row.cells.length > 5 ? row.cells[5] : "";
                String room = row.cells.length > 6 ? row.cells[6] : "";
                int credits = row.cells.length > 7 ? number(row.cells[7], "학점", MAX_CREDITS_VALUE) : 0;
                int capacity = row.cells.length > 8 ? number(row.cells[8], "정원", MAX_PEOPLE) : 0;
                int enrolled = row.cells.length > 9 ? number(row.cells[9], "수강인원", MAX_PEOPLE) : 0;
                result.add(new Course(row.cells[0], row.cells[1], row.cells[2],
                        choice(CourseType.class, row.cells[3], "구분"), times, professor, room,
                        credits, capacity, enrolled));
            } catch (RuntimeException e) {
                throw row.error(e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<Student> loadStudents(List<Course> courses) throws IOException {
        Map<String, Course> byCode = indexByCode(courses);
        List<Student> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row row : readRows(STUDENTS_FILE, "학번", 3, 4, false)) {
            if (row.cells[0].isEmpty()) {
                throw row.error("학번이 비어 있습니다");
            }
            if (!row.cells[0].matches("[0-9]+")) {
                throw row.error("학번은 숫자여야 합니다 (" + row.cells[0] + ")");
            }
            if (!seen.add(row.cells[0])) {
                throw row.error("학번이 중복됩니다 " + row.cells[0]);
            }
            if (row.cells[1].isEmpty()) {
                throw row.error("이름이 비어 있습니다");
            }
            if (row.cells.length > 3 && !CONFIRMED.equals(row.cells[3]) && !EDITING.equals(row.cells[3])) {
                throw row.error("시간표상태는 " + CONFIRMED + " 또는 " + EDITING + " 이어야 합니다 (" + row.cells[3] + ")");
            }
            Timetable timetable = new Timetable();
            for (String code : splitList(row.cells[2])) {
                Course course = byCode.get(code);
                if (course == null) {
                    throw row.error("과목 목록에 없는 코드 " + code);
                }
                if (!timetable.restore(course)) {
                    throw row.error("중복되거나 시간이 겹치는 과목 " + code);
                }
            }

            boolean confirmed = row.cells.length > 3 ? CONFIRMED.equals(row.cells[3]) : timetable.size() > 0;
            result.add(new Student(row.cells[0], row.cells[1], timetable, confirmed));
        }
        return result;
    }

    @Override
    public List<SwapRequest> loadRequests(List<Course> courses) throws IOException {
        Map<String, Course> byCode = indexByCode(courses);
        List<SwapRequest> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Row row : readRows(REQUESTS_FILE, "번호", 5, 5, false)) {
            Course give = byCode.get(row.cells[2]);
            if (give == null) {
                throw row.error("과목 목록에 없는 코드 " + row.cells[2]);
            }
            if (!row.cells[1].matches("[0-9]+")) {
                throw row.error("학번은 숫자여야 합니다 (" + row.cells[1] + ")");
            }
            if (row.cells[3].isEmpty()) {
                throw row.error("희망 과목명이 비어 있습니다");
            }
            try {
                int id = number(row.cells[0], "요청 번호", Integer.MAX_VALUE);
                if (id < 1 || id > MAX_REQUEST_ID) {
                    throw new IllegalArgumentException("요청 번호는 1 이상 " + MAX_REQUEST_ID
                            + " 이하여야 합니다 (" + id + ")");
                }
                if (!seen.add(id)) {
                    throw new IllegalArgumentException("요청 번호가 중복됩니다 #" + id);
                }
                result.add(new SwapRequest(id, row.cells[1], give, row.cells[3],
                        choice(RequestStatus.class, row.cells[4], "상태")));
            } catch (RuntimeException e) {
                throw row.error(e.getMessage());
            }
        }
        return result;
    }

    @Override
    public void saveStudents(List<Student> students) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(STUDENTS_HEADER);
        for (Student s : students) {
            List<String> codes = new ArrayList<>();
            for (Course c : s.getTimetable().getCourses()) {
                codes.add(c.getCode());
            }
            lines.add(s.getId() + "," + quote(clean(s.getName())) + "," + String.join(";", codes) + ","
                    + (s.isTimetableConfirmed() ? CONFIRMED : EDITING));
        }
        write(STUDENTS_FILE, lines);
    }

    @Override
    public void saveRequests(List<SwapRequest> requests) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(REQUESTS_HEADER);
        for (SwapRequest r : requests) {
            lines.add(r.getId() + "," + r.getStudentId() + "," + r.getGiveCourse().getCode() + ","
                    + quote(clean(r.getWantName())) + "," + r.getStatus().name());
        }
        write(REQUESTS_FILE, lines);
    }

    private static int number(String cell, String what, int max) {
        int value;
        try {
            value = Integer.parseInt(cell);
            if (value < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " 값은 0 이상의 숫자여야 합니다 (" + cell + ")");
        }
        if (value > max) {
            throw new IllegalArgumentException(what + " 값이 너무 큽니다 (" + cell + ", 최대 " + max + ")");
        }
        return value;
    }

    private static <E extends Enum<E>> E choice(Class<E> type, String cell, String what) {
        try {
            return Enum.valueOf(type, cell);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + " 값이 잘못되었습니다 (" + cell + "). 가능한 값: "
                    + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }

    private List<Row> readRows(String fileName, String headerKey, int minColumns, int maxColumns,
                               boolean required) throws IOException {
        Path file = dir.resolve(fileName);
        List<Row> rows = new ArrayList<>();
        if (!required && !Files.exists(file)) {
            return rows;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            throw new IOException(fileName + ": UTF-8 인코딩이 아닙니다. UTF-8로 다시 저장하세요");
        } catch (java.nio.file.AccessDeniedException e) {
            throw new IOException(fileName + ": 읽을 권한이 없습니다");
        }
        if (!lines.isEmpty()) {
            String first = lines.get(0).startsWith(BOM) ? lines.get(0).substring(1) : lines.get(0);
            if (!splitCsv(first)[0].equals(headerKey)) {
                throw new IOException(fileName + " 1번째 줄: 헤더가 없습니다. 첫 줄은 '" + headerKey
                        + ",...' 로 시작하는 헤더여야 합니다");
            }
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] cells = splitCsv(line);
            Row row = new Row(fileName, i + 1, cells);
            if (cells.length < minColumns || cells.length > maxColumns) {
                throw row.error("칸 수가 맞지 않습니다 (" + cells.length + "칸)");
            }
            rows.add(row);
        }
        return rows;
    }

    private void write(String fileName, List<String> lines) throws IOException {
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), lines, StandardCharsets.UTF_8);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new IOException(fileName + ": 쓸 권한이 없습니다");
        }
    }

    private static String[] splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString().trim());
        return cells.toArray(new String[0]);
    }

    private static String clean(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                sb.append(ch).append(value.charAt(++i));
            } else if (Character.isSurrogate(ch)) {
                sb.append('?');
            } else {
                sb.append(ch == '\n' || ch == '\r' ? ' ' : ch);
            }
        }
        return sb.toString();
    }

    private static String quote(String value) {
        if (!value.contains(",") && !value.contains("\"")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static List<String> splitList(String cell) {
        List<String> result = new ArrayList<>();
        for (String part : cell.split(";")) {
            if (!part.trim().isEmpty()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static Map<String, Course> indexByCode(List<Course> courses) {
        Map<String, Course> byCode = new HashMap<>();
        for (Course c : courses) {
            byCode.put(c.getCode(), c);
        }
        return byCode;
    }

    private static final class Row {
        private final String fileName;
        private final int lineNo;
        private final String[] cells;

        private Row(String fileName, int lineNo, String[] cells) {
            this.fileName = fileName;
            this.lineNo = lineNo;
            this.cells = cells;
        }

        private IOException error(String detail) {
            return new IOException(fileName + " " + lineNo + "번째 줄: " + detail);
        }
    }
}
