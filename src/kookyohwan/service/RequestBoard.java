package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.RequestStatus;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 교환 요청 목록. 요청 번호 발급·등록 조건 검사·상태 변경은 여기서만 한다(UI는 검사하지 않는다). */
public class RequestBoard {
    private final Repository<Integer, SwapRequest> requests = new Repository<>();
    private final CourseCatalog catalog; // 희망 과목명 존재 확인용
    private int nextId = 1;

    public RequestBoard(CourseCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * R3 요청 등록. 조건을 하나라도 어기면 RequestRejectedException을 던지고 목록은 바꾸지 않는다.
     * wantName은 이미 과목명으로 해석된 값(코드 -> 과목명 변환은 UI가 CourseCatalog.resolveName으로 먼저 한다).
     */
    public SwapRequest register(Student student, Course give, String wantName) {
        validate(student, give, wantName);
        SwapRequest request = new SwapRequest(nextId, student.getId(), give, wantName, RequestStatus.WAITING);
        requests.put(request.getId(), request);
        nextId++;
        return request;
    }

    /** 내놓을 과목만 먼저 검사 (콘솔이 번호를 고른 직후 거절할 수 있게, AC3). 목록은 바꾸지 않는다. */
    public void validateGive(Student student, Course give) {
        if (!student.getTimetable().contains(give.getCode())) {
            throw new RequestRejectedException(give.getName(), "내 시간표에 없는 과목입니다");
        }
        if (!give.isGeneral()) {
            throw new RequestRejectedException(give.getName(),
                    give.getType().getLabel() + " 과목은 교환 요청을 등록할 수 없습니다"); // 전공·교직·군사
        }
    }

    /**
     * 등록 조건 전체 검사. register가 쓰는 것과 같은 검사라서
     * 콘솔이 (y/n) 확인 전에 미리 불러도 기준이 어긋나지 않는다. 목록은 바꾸지 않는다.
     */
    public void validate(Student student, Course give, String wantName) {
        validateGive(student, give);
        if (!catalog.existsName(wantName)) {
            throw new RequestRejectedException(wantName, "과목 목록에 없습니다. 1번 메뉴에서 과목명을 확인하세요");
        }
        if (give.getName().equals(wantName)) {
            throw new RequestRejectedException(wantName, "내놓을 과목과 희망 과목이 같습니다");
        }
        for (SwapRequest r : waitingOf(student.getId())) {
            if (r.getGiveCourse().getCode().equals(give.getCode()) && r.getWantName().equals(wantName)) {
                throw new RequestRejectedException(give.getName() + " -> " + wantName,
                        "같은 내용의 대기 요청이 이미 있습니다 (#" + r.getId() + ")");
            }
        }
    }

    /** 복원용: 파일에서 읽은 요청을 번호 그대로 넣고, 다음 번호를 그 뒤로 맞춘다. */
    public void put(SwapRequest request) {
        requests.put(request.getId(), request);
        if (request.getId() >= nextId) {
            nextId = request.getId() + 1;
        }
    }

    public Optional<SwapRequest> find(int id) {
        return requests.find(id);
    }

    public List<SwapRequest> all() {
        return requests.all();
    }

    public int size() {
        return requests.size();
    }

    /** 내 대기 요청 (메뉴 5에서 고를 목록) */
    public List<SwapRequest> waitingOf(String studentId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest r : requests.all()) {
            if (r.isWaiting() && r.getStudentId().equals(studentId)) {
                result.add(r);
            }
        }
        return result;
    }

    /** 나를 뺀 다른 사람의 대기 요청 (매칭 후보 풀). 자기 요청끼리는 매칭하지 않는다(5.3). */
    public List<SwapRequest> waitingExcept(String studentId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest r : requests.all()) {
            if (r.isWaiting() && !r.getStudentId().equals(studentId)) {
                result.add(r);
            }
        }
        return result;
    }

    /** 이 학생의 대기 요청 중 courseCode를 내놓는 요청 (시간표 교체 전 확인용, AC5-2) */
    public Optional<SwapRequest> findGiving(String studentId, String courseCode) {
        for (SwapRequest r : waitingOf(studentId)) {
            if (r.getGiveCourse().getCode().equals(courseCode)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public boolean isGiving(String studentId, String courseCode) {
        return findGiving(studentId, courseCode).isPresent();
    }

    /** R7 요청 취소(추가 후보). 대기 중인 요청만 취소할 수 있다. */
    public boolean cancel(int id) {
        Optional<SwapRequest> found = requests.find(id);
        if (found.isEmpty() || !found.get().isWaiting()) {
            return false;
        }
        found.get().cancel();
        return true;
    }
}
