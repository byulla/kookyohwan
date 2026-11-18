package kookyohwan.model;

public class Student {
    private final String id;
    private final String name;
    private final Timetable timetable;
    private boolean timetableConfirmed;

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
