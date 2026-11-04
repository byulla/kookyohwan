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

    public Course(String code, String name, String section, CourseType type, List<TimeSlot> times) {
        this.code = code;
        this.name = name;
        this.section = section;
        this.type = type;
        this.times = Collections.unmodifiableList(new ArrayList<>(times));
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
