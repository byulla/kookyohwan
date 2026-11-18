package kookyohwan.storage;

import kookyohwan.model.Course;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.io.IOException;
import java.util.List;

public interface Storage {
    List<Course> loadCourses() throws IOException;

    List<Student> loadStudents(List<Course> courses) throws IOException;

    List<SwapRequest> loadRequests(List<Course> courses) throws IOException;

    void saveStudents(List<Student> students) throws IOException;

    void saveRequests(List<SwapRequest> requests) throws IOException;
}
