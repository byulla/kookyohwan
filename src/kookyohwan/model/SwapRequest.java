package kookyohwan.model;

/** 교환 요청 한 건. 내놓는 쪽은 분반(Course), 희망 쪽은 과목명(분반 무관) — 계획서 16절 Q2. */
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

    /** R7(요청 취소). 상태 변경은 RequestBoard.cancel을 거쳐서만 호출한다. */
    public void cancel() {
        this.status = RequestStatus.CANCELLED;
    }

    /** 교환 확정. 상태 변경은 RequestBoard.complete를 거쳐서만 호출한다. */
    public void complete() {
        this.status = RequestStatus.MATCHED;
    }

    /** "프랑스어 입문 (화 1-2) -> 생명과 나눔" */
    public String summary() {
        return giveCourse.getName() + " (" + giveCourse.timesToString() + ") -> " + wantName;
    }
}
