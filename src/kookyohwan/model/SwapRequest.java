package kookyohwan.model;

public class SwapRequest {
    private final int id;
    private final String studentId;
    private final Course giveCourse;
    private final String wantName;
    private RequestStatus status;

    public SwapRequest(int id, String studentId, Course giveCourse, String wantName, RequestStatus status) {
        this.id = id;
        this.studentId = studentId;
        this.giveCourse = giveCourse;
        this.wantName = wantName;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public Course getGiveCourse() {
        return giveCourse;
    }

    public String getWantName() {
        return wantName;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public boolean isWaiting() {
        return status == RequestStatus.WAITING;
    }

    public void cancel() {
        this.status = RequestStatus.CANCELLED;
    }

    public void complete() {
        this.status = RequestStatus.MATCHED;
    }

    public String summary() {
        return giveCourse.getName() + " (" + giveCourse.timesToString() + ") -> " + wantName;
    }
}
