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
import java.util.List;
import java.util.Map;

/**
 * CSV 구현(계획서 14절 형식). UTF-8, 첫 줄 헤더, 구분자 쉼표, 한 칸에 값이 여러 개면 세미콜론.
 * 값 안에 쉼표가 있으면 큰따옴표로 감싼다.
 * 형식이 틀린 줄은 건너뛰지 않고 IOException으로 알린다 — 일부만 읽은 채 종료하면 저장 때 그 줄이 사라지기 때문.
 */
public class FileStorage implements Storage {
    public static final String COURSES_FILE = "courses.csv";
    public static final String STUDENTS_FILE = "students.csv";
    public static final String REQUESTS_FILE = "requests.csv";

    private static final String STUDENTS_HEADER = "학번,이름,과목코드,시간표상태";
    private static final String CONFIRMED = "CONFIRMED"; // 시간표 확정
    private static final String EDITING = "EDITING";     // 시간표 편집 중
    private static final String REQUESTS_HEADER = "번호,학번,내놓는과목코드,희망과목명,상태";

    private final Path dir;

    public FileStorage(String dataDir) {
        this.dir = Paths.get(dataDir);
    }

    /** courses.csv가 없으면 NoSuchFileException(IOException) — 과목 없이는 아무것도 못 하므로 Main이 안내 후 종료. */
    @Override
    public List<Course> loadCourses() throws IOException {
        List<Course> result = new ArrayList<>();
        for (Row row : readRows(COURSES_FILE, 5, 10, true)) { // 교수·강의실·학점·정원·수강인원 칸은 없어도 읽는다
            try {
                List<TimeSlot> times = new ArrayList<>();
                for (String t : splitList(row.cells[4])) {
                    times.add(TimeSlot.parse(t));
                }
                String professor = row.cells.length > 5 ? row.cells[5] : "";
                String room = row.cells.length > 6 ? row.cells[6] : "";
                int credits = row.cells.length > 7 ? Integer.parseInt(row.cells[7]) : 0;
                int capacity = row.cells.length > 8 ? Integer.parseInt(row.cells[8]) : 0;
                int enrolled = row.cells.length > 9 ? Integer.parseInt(row.cells[9]) : 0;
                result.add(new Course(row.cells[0], row.cells[1], row.cells[2],
                        CourseType.valueOf(row.cells[3]), times, professor, room, credits, capacity, enrolled));
            } catch (IllegalArgumentException e) { // 요일·교시·구분 형식 오류 (NumberFormatException 포함)
                throw row.error(e.getMessage());
            }
        }
        return result;
    }

    /** students.csv가 없으면 빈 목록(빈 상태로 시작). */
    @Override
    public List<Student> loadStudents(List<Course> courses) throws IOException {
        Map<String, Course> byCode = indexByCode(courses);
        List<Student> result = new ArrayList<>();
        for (Row row : readRows(STUDENTS_FILE, 3, 4, false)) {
            Timetable timetable = new Timetable();
            for (String code : splitList(row.cells[2])) {
                Course course = byCode.get(code);
                if (course == null) {
                    throw row.error("과목 목록에 없는 코드 " + code);
                }
                if (!timetable.restore(course)) { // 학점 제한은 보지 않고 저장된 그대로 읽는다
                    throw row.error("중복되거나 시간이 겹치는 과목 " + code);
                }
            }
            // 상태 칸이 없는 예전 파일: 과목이 들어 있으면 확정된 시간표로 본다
            boolean confirmed = row.cells.length > 3 ? CONFIRMED.equals(row.cells[3]) : timetable.size() > 0;
            result.add(new Student(row.cells[0], row.cells[1], timetable, confirmed));
        }
        return result;
    }

    /** requests.csv가 없으면 빈 목록. 요청자 학번이 students.csv에 없어도 읽는다(16절 Q8). */
    @Override
    public List<SwapRequest> loadRequests(List<Course> courses) throws IOException {
        Map<String, Course> byCode = indexByCode(courses);
        List<SwapRequest> result = new ArrayList<>();
        for (Row row : readRows(REQUESTS_FILE, 5, 5, false)) {
            Course give = byCode.get(row.cells[2]);
            if (give == null) {
                throw row.error("과목 목록에 없는 코드 " + row.cells[2]);
            }
            try {
                result.add(new SwapRequest(Integer.parseInt(row.cells[0]), row.cells[1], give,
                        row.cells[3], RequestStatus.valueOf(row.cells[4])));
            } catch (IllegalArgumentException e) { // 번호가 숫자가 아니거나 상태 이름이 enum에 없음
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
            lines.add(s.getId() + "," + quote(s.getName()) + "," + String.join(";", codes) + ","
                    + (s.isTimetableConfirmed() ? CONFIRMED : EDITING));
        }
        write(STUDENTS_FILE, lines);
    }

    @Override
    public void saveRequests(List<SwapRequest> requests) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(REQUESTS_HEADER);
        for (SwapRequest r : requests) {
            // 상태는 enum 이름으로 저장 — 화면 라벨("대기")을 바꿔도 파일이 깨지지 않게
            lines.add(r.getId() + "," + r.getStudentId() + "," + r.getGiveCourse().getCode() + ","
                    + quote(r.getWantName()) + "," + r.getStatus().name());
        }
        write(REQUESTS_FILE, lines);
    }

    /** 헤더와 빈 줄을 뺀 데이터 줄. 칸 수가 다르면 줄 번호와 함께 오류. */
    private List<Row> readRows(String fileName, int minColumns, int maxColumns, boolean required)
            throws IOException {
        Path file = dir.resolve(fileName);
        List<Row> rows = new ArrayList<>();
        if (!required && !Files.exists(file)) {
            return rows;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) { // 0번 줄은 헤더
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
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), lines, StandardCharsets.UTF_8); // 통째로 다시 쓴다
    }

    /**
     * CSV 한 줄을 칸으로 나눈다. 큰따옴표로 감싼 칸 안의 쉼표는 구분자가 아니다("" 는 따옴표 한 개).
     * 처음에는 '과목명에 쉼표 금지'로 정했지만(16절 Q9), 실제 교과목명에 쉼표가 있어서
     * (예: P-실무프로젝트 (4주/융합,메이커스)) 이름을 그대로 쓰기 위해 따옴표 처리를 넣었다.
     * 마지막 칸이 비어도(빈 시간표) 칸 수는 유지된다.
     */
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

    /** 쉼표나 따옴표가 든 값만 큰따옴표로 감싼다 (splitCsv의 반대). */
    private static String quote(String value) {
        if (!value.contains(",") && !value.contains("\"")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** "CS101-01;GE201-01" -> [CS101-01, GE201-01]. 빈 칸이면 빈 목록. */
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

    /** 파일의 한 줄. 오류 메시지에 파일명과 줄 번호를 붙이기 위해 들고 다닌다. */
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
