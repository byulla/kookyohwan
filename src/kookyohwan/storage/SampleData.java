package kookyohwan.storage;

import kookyohwan.model.Course;
import kookyohwan.model.CourseType;
import kookyohwan.model.RequestStatus;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.model.TimeSlot;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.StudentRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 1단계 전용: 계획서 14절 준비 데이터를 코드로 하드코딩. 3단계에서 data/*.csv로 옮기고 FileStorage가 대신한다.
 * 완료 기준 AC1~AC11의 시작 상태이므로 값을 임의로 바꾸지 말 것. (학생F와 요청 #8은 2차 AC8에서 추가)
 */
public final class SampleData {
    private SampleData() {
    }

    public static void loadCourses(CourseCatalog catalog) {
        catalog.add(course("CS101-01", "컴퓨터공학개론", "01", CourseType.MAJOR, "월3-4"));
        catalog.add(course("CS102-01", "C프로그래밍", "01", CourseType.MAJOR, "수1-2", "금3-4"));
        catalog.add(course("GE101-01", "생명과 나눔", "01", CourseType.GENERAL, "수5-6"));
        catalog.add(course("GE101-02", "생명과 나눔", "02", CourseType.GENERAL, "월3-4"));
        catalog.add(course("GE101-03", "생명과 나눔", "03", CourseType.GENERAL, "화1-2"));
        catalog.add(course("GE201-01", "프랑스어 입문", "01", CourseType.GENERAL, "화1-2"));
        catalog.add(course("GE301-01", "심리학의 이해", "01", CourseType.GENERAL, "목3-4"));
        catalog.add(course("GE401-01", "일본어 입문", "01", CourseType.GENERAL, "금1-2"));
        catalog.add(course("GE501-01", "영화의 이해", "01", CourseType.GENERAL, "화5-6"));
        catalog.add(course("GE601-01", "글쓰기 기초", "01", CourseType.GENERAL, "화1-2"));
    }

    public static void loadStudents(StudentRegistry registry, CourseCatalog catalog) {
        student(registry, catalog, "202612345", "학생A", "CS101-01", "CS102-01", "GE201-01");
        student(registry, catalog, "202612346", "학생B", "GE101-01", "CS101-01");
        student(registry, catalog, "202612347", "학생C", "GE101-01");
        student(registry, catalog, "202612348", "학생D", "GE301-01");
        student(registry, catalog, "202612349", "학생E", "GE101-02");
        student(registry, catalog, "202612350", "학생G", "GE101-03");
    }

    public static void loadRequests(RequestBoard board, CourseCatalog catalog) {
        request(board, catalog, 1, "202612346", "GE101-01", "프랑스어 입문");
        request(board, catalog, 2, "202612347", "GE101-01", "일본어 입문");
        request(board, catalog, 3, "202612348", "GE301-01", "프랑스어 입문");
        request(board, catalog, 4, "202612349", "GE101-02", "프랑스어 입문");
        request(board, catalog, 5, "202612350", "GE101-03", "프랑스어 입문");
    }

    private static Course course(String code, String name, String section, CourseType type, String... times) {
        List<TimeSlot> slots = new ArrayList<>();
        for (String t : times) {
            slots.add(TimeSlot.parse(t));
        }
        return new Course(code, name, section, type, slots);
    }

    private static void student(StudentRegistry registry, CourseCatalog catalog,
                                String id, String name, String... codes) {
        Student s = registry.register(id, name);
        for (String code : codes) {
            s.getTimetable().add(catalog.findByCode(code).orElseThrow());
        }
    }

    private static void request(RequestBoard board, CourseCatalog catalog,
                                int id, String studentId, String giveCode, String wantName) {
        Course give = catalog.findByCode(giveCode).orElseThrow();
        board.put(new SwapRequest(id, studentId, give, wantName, RequestStatus.WAITING));
    }
}
