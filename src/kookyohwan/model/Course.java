package kookyohwan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Course {
    private final String code;
    private final String name;
    private final String section;
    private final CourseType type;
    private final List<TimeSlot> times;
    private final String professor;
    private final String room;
    private final int credits;
    private final int capacity;
    private final int baseEnrolled;

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

    public String getSubjectCode() {
        return code.endsWith(section) ? code.substring(0, code.length() - section.length()) : code;
    }

    public boolean isSameSubject(Course other) {
        return name.equals(other.name) && getSubjectCode().equals(other.getSubjectCode());
    }

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
