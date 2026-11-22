package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.Student;

public class EnrollmentService {
    private final StudentRegistry registry;

    public EnrollmentService(StudentRegistry registry) {
        this.registry = registry;
    }

    public int enrolledOf(Course course) {
        long count = course.getBaseEnrolled();
        for (Student s : registry.all()) {
            if (s.getTimetable().contains(course.getCode())) {
                count++;
            }
        }
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    public boolean hasLimit(Course course) {
        return course.getCapacity() > 0;
    }

    public int vacancyOf(Course course) {
        return Math.max(0, course.getCapacity() - enrolledOf(course));
    }

    public boolean isFull(Course course) {
        return hasLimit(course) && vacancyOf(course) == 0;
    }

    public String describe(Course course) {
        if (!hasLimit(course)) {
            return "제한 없음";
        }
        int enrolled = Math.min(enrolledOf(course), course.getCapacity());
        String ratio = " (" + enrolled + "/" + course.getCapacity() + ")";
        return isFull(course) ? "마감" + ratio : "빈자리 " + vacancyOf(course) + ratio;
    }

    public String seatNote(Course course) {
        return hasLimit(course) ? "  (" + course.getName() + " " + course.getSection() + ": " + describe(course) + ")" : "";
    }

    public void validateEnroll(Student student, Course course) {
        if (student.isTimetableConfirmed() && isFull(course)) {
            throw new RequestRejectedException(course.getName() + " " + course.getSection(),
                    "정원이 마감되었습니다 (" + course.getCapacity() + "명). 마감된 과목은 교환 요청으로 상대를 찾으세요");
        }
    }
}
