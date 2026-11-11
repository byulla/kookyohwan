package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 과목 한 분반. 코드(GE101-02)가 유일한 식별자이고, 과목명은 분반끼리 같을 수 있다. */
public class Course {
    private final String code;
    private final String name;
    private final String section;
    private final CourseType type;
    private final List<TimeSlot> times;
    private final String professor; // 담당교수. 시간표 화면 표시용 (없으면 "")
    private final String room;      // 강의실. 시간표 화면 표시용 (없으면 "")
    private final int credits;      // 학점. 최대 학점 제한에 쓴다 (파일에 없으면 0)
    private final int capacity;     // 정원. 0이면 정원 정보 없음(제한 없음)
    private final int baseEnrolled; // 이 프로그램에 등록되지 않은 다른 학생들의 수강 인원(준비 데이터)

    public Course(String code, String name, String section, CourseType type, List<TimeSlot> times) {
        this(code, name, section, type, times, "", "");
    }

    public Course(String code, String name, String section, CourseType type, List<TimeSlot> times,
                  String professor, String room) {
        this(code, name, section, type, times, professor, room, 0);
    }

    public Course(String code, String name, String section, CourseType type, List<TimeSlot> times,
                  String professor, String room, int credits) {
        this(code, name, section, type, times, professor, room, credits, 0, 0);
    }

    public Course(String code, String name, String section, CourseType type, List<TimeSlot> times,
                  String professor, String room, int credits, int capacity, int baseEnrolled) {
        this.code = code;
        this.name = name;
        this.section = section;
        this.type = type;
        this.times = Collections.unmodifiableList(new ArrayList<>(times));
        this.professor = professor;
        this.room = room;
        this.credits = credits;
        this.capacity = capacity;
        this.baseEnrolled = baseEnrolled;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBaseEnrolled() {
        return baseEnrolled;
    }

    public int getCredits() {
        return credits;
    }

    public String getProfessor() {
        return professor;
    }
    public String getRoom() {
        return room;
    }

    public String getCode() {
        return code;
    }
    public String getName() {
        return name;
    }
    public String getSection() {
        return section;
    }
    public CourseType getType() {
        return type;
    }
    public List<TimeSlot> getTimes() {
        return times;
    }
    public boolean isGeneral() {
        return type == CourseType.GENERAL;
    }

    /** 분반을 뺀 학수번호 앞부분 ("11393006" -> "11393"). 코드가 분반으로 끝나지 않으면 코드 그대로. */
    public String getSubjectCode() {
        return code.endsWith(section) ? code.substring(0, code.length() - section.length()) : code;
    }

    /**
     * 같은 과목(분반만 다르거나 같은 분반)인지. 과목명만 보면 학과가 다른 동명 과목(데이터베이스 등)까지
     * 같다고 해 버려서 학수번호 앞부분도 같이 본다.
     */
    public boolean isSameSubject(Course other) {
        return name.equals(other.name) && getSubjectCode().equals(other.getSubjectCode());
    }

    /** 이 과목의 시간 중 하나라도 other와 겹치면 true. */
    public boolean overlaps(Course other) {
        for (TimeSlot mine : times) {
            for (TimeSlot theirs : other.times) {
                if (mine.overlaps(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "수 1-2, 금 3-4" */
    public String timesToString() {
        StringBuilder sb = new StringBuilder();
        for (TimeSlot t : times) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return code + " " + name + " " + section + " (" + timesToString() + ")";
    }
}
