package kookyohwan.model;

/** 학생. 로그인은 학번, 이름은 출력용(계획서 16절 Q11). */
public class Student {
    private final String id;
    private final String name;
    private final Timetable timetable;
    private boolean timetableConfirmed; // false = 시간표 편집 중(지금 듣는 과목을 옮겨 적는 단계), true = 저장해서 확정

    public Student(String id, String name, Timetable timetable) {
        this(id, name, timetable, false);
    }

    public Student(String id, String name, Timetable timetable, boolean timetableConfirmed) {
        this.id = id;
        this.name = name;
        this.timetable = timetable;
        this.timetableConfirmed = timetableConfirmed;
    }

    public boolean isTimetableConfirmed() {
        return timetableConfirmed;
    }

    /** 시간표 확정. 되돌릴 수 없다. StudentRegistry.confirmTimetable을 거쳐서만 호출한다. */
    public void confirmTimetable() {
        this.timetableConfirmed = true;
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
