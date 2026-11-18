package kookyohwan.service;

import kookyohwan.model.Student;
import kookyohwan.model.Timetable;

import java.util.List;
import java.util.Optional;

public class StudentRegistry {
    private final Repository<String, Student> students = new Repository<>();

    public Optional<Student> find(String id) {
        return students.find(id);
    }

    public Student register(String id, String name) {
        if (students.exists(id)) {
            throw new IllegalArgumentException("이미 등록된 학번입니다: " + id);
        }
        Student s = new Student(id, name, new Timetable());
        students.put(id, s);
        return s;
    }

    public boolean confirmTimetable(String id) {
        Optional<Student> found = students.find(id);
        if (found.isEmpty() || found.get().isTimetableConfirmed()) {
            return false;
        }
        found.get().confirmTimetable();
        return true;
    }

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
