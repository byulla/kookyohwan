package kookyohwan.model;

/** 학생. 로그인은 학번, 이름은 출력용(계획서 16절 Q11). */
public class Student {
    private final String id;
    private final String name;
    private final Timetable timetable;

    public Student(String id, String name, Timetable timetable) {
        this.id = id;
        this.name = name;
        this.timetable = timetable;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Timetable getTimetable() {
        return timetable;
    }
}
