package kookyohwan.service;

import kookyohwan.model.Student;
import kookyohwan.model.Timetable;

import java.util.List;
import java.util.Optional;

/** 학생 목록. 로그인·사용자 전환(login)과 매칭의 상대 시간표 조회(R5-2)가 사용한다. */
public class StudentRegistry {
    private final Repository<String, Student> students = new Repository<>(); // 학번 -> 학생

    public Optional<Student> find(String id) {
        return students.find(id);
    }

    /** 처음 보는 학번: 이름만 받아 빈 시간표로 등록. 학생 생성은 여기 한 곳. */
    public Student register(String id, String name) {
        Student s = new Student(id, name, new Timetable());
        students.put(id, s);
        return s;
    }

    /** 복원용: 파일에서 읽은 학생(시간표 포함)을 그대로 넣는다. */
    public void put(Student student) {
        students.put(student.getId(), student);
    }

    public List<Student> all() {
        return students.all();
    }

    public int size() {
        return students.size();
    }
}
