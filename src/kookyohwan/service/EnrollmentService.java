package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.Student;

/**
 * 정원과 빈자리. 수강 인원 = 파일의 수강인원(이 프로그램에 없는 다른 학생들) + 등록된 학생 중 그 과목을 듣는 사람 수.
 * 시간표를 확정(저장)한 학생은 빈자리가 있는 과목만 바로 수강신청할 수 있고, 마감된 과목은 교환 요청으로만 얻는다 —
 * 과목 목록에서 아무 과목이나 넣을 수 있으면 교환 상대를 찾을 이유가 없어지기 때문.
 */
public class EnrollmentService {
    private final StudentRegistry registry;

    public EnrollmentService(StudentRegistry registry) {
        this.registry = registry;
    }

    public int enrolledOf(Course course) {
        int count = course.getBaseEnrolled();
        for (Student s : registry.all()) {
            if (s.getTimetable().contains(course.getCode())) {
                count++;
            }
        }
        return count;
    }

    /** 정원이 0이면 정원 정보가 없는 과목 — 제한 없이 신청할 수 있는 것으로 본다. */
    public boolean hasLimit(Course course) {
        return course.getCapacity() > 0;
    }

    public int vacancyOf(Course course) {
        return Math.max(0, course.getCapacity() - enrolledOf(course));
    }

    public boolean isFull(Course course) {
        return hasLimit(course) && vacancyOf(course) == 0;
    }

    /** 화면용: "마감 (30/30)", "빈자리 3 (27/30)", "제한 없음" */
    public String describe(Course course) {
        if (!hasLimit(course)) {
            return "제한 없음";
        }
        int enrolled = Math.min(enrolledOf(course), course.getCapacity());
        String ratio = " (" + enrolled + "/" + course.getCapacity() + ")";
        return isFull(course) ? "마감" + ratio : "빈자리 " + vacancyOf(course) + ratio;
    }

    /**
     * 이 학생이 이 과목을 시간표에 넣을 수 있는지(정원 기준). 안 되면 RequestRejectedException.
     * 시간표 편집 중(확정 전)에는 이미 듣고 있는 과목을 옮겨 적는 단계라서 정원을 보지 않는다.
     */
    public void validateEnroll(Student student, Course course) {
        if (student.isTimetableConfirmed() && isFull(course)) {
            throw new RequestRejectedException(course.getName() + " " + course.getSection(),
                    "정원이 마감되었습니다 (" + course.getCapacity() + "명). 마감된 과목은 교환 요청으로 상대를 찾으세요");
        }
    }
}
