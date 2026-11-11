package kookyohwan.storage;

import kookyohwan.model.Course;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.io.IOException;
import java.util.List;

/**
 * 저장 방식의 경계. Main만 구현체(FileStorage)를 알고, service와 ui는 이 인터페이스도 모른다.
 * 나중에 DB 구현체를 추가해도 매칭·요청 관리 코드는 바뀌지 않는다(계획서 6절 결정).
 * 복원 순서는 과목 -> 학생 -> 요청 (학생과 요청이 과목을 참조하므로 과목 목록을 넘겨받는다).
 */
public interface Storage {
    List<Course> loadCourses() throws IOException;

    List<Student> loadStudents(List<Course> courses) throws IOException;

    List<SwapRequest> loadRequests(List<Course> courses) throws IOException;

    void saveStudents(List<Student> students) throws IOException;

    void saveRequests(List<SwapRequest> requests) throws IOException;
}
