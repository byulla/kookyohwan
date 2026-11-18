package kookyohwan.service;

import kookyohwan.model.Course;
import kookyohwan.model.RequestStatus;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RequestBoard {
    private final Repository<Integer, SwapRequest> requests = new Repository<>();
    private final CourseCatalog catalog;
    private int nextId = 1;

    public RequestBoard(CourseCatalog catalog) {
        this.catalog = catalog;
    }

    public SwapRequest register(Student student, Course give, String wantName) {
        validate(student, give, wantName);
        SwapRequest request = new SwapRequest(nextId, student.getId(), give, wantName, RequestStatus.WAITING);
        requests.put(request.getId(), request);
        nextId++;
        return request;
    }

    public void validateGive(Student student, Course give) {
        if (!student.isTimetableConfirmed()) {
            throw new RequestRejectedException(student.getName(),
                    "시간표를 저장(확정)한 뒤에 교환 요청을 등록할 수 있습니다");
        }
        if (!student.getTimetable().contains(give.getCode())) {
            throw new RequestRejectedException(give.getName(), "내 시간표에 없는 과목입니다");
        }
        if (!give.isGeneral()) {
            throw new RequestRejectedException(give.getName(),
                    give.getType().getLabel() + " 과목은 교환 요청을 등록할 수 없습니다");
        }
    }

    public void validate(Student student, Course give, String wantName) {
        validateGive(student, give);
        if (!catalog.existsName(wantName)) {
            throw new RequestRejectedException(wantName, "과목 목록에 없습니다. 1번 메뉴에서 과목명을 확인하세요");
        }
        if (give.getName().equals(wantName)) {
            throw new RequestRejectedException(wantName, "내놓을 과목과 희망 과목이 같습니다");
        }

        boolean anyGeneral = false;
        for (Course section : catalog.findByName(wantName)) {
            if (!section.isGeneral()) {
                continue;
            }
            anyGeneral = true;
            if (student.getTimetable().findSameSubject(section, java.util.Collections.emptyList()).isPresent()) {
                throw new RequestRejectedException(wantName, "이미 듣고 있는 과목입니다");
            }
        }
        if (!anyGeneral) {
            throw new RequestRejectedException(wantName, "교양 과목만 교환할 수 있습니다");
        }
        for (SwapRequest r : waitingOf(student.getId())) {
            if (r.getGiveCourse().getCode().equals(give.getCode()) && r.getWantName().equals(wantName)) {
                throw new RequestRejectedException(give.getName() + " -> " + wantName,
                        "같은 내용의 대기 요청이 이미 있습니다 (#" + r.getId() + ")");
            }
        }
    }

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

    public List<SwapRequest> waitingOf(String studentId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest r : requests.all()) {
            if (r.isWaiting() && r.getStudentId().equals(studentId)) {
                result.add(r);
            }
        }
        return result;
    }

    public List<SwapRequest> waitingExcept(String studentId) {
        List<SwapRequest> result = new ArrayList<>();
        for (SwapRequest r : requests.all()) {
            if (r.isWaiting() && !r.getStudentId().equals(studentId)) {
                result.add(r);
            }
        }
        return result;
    }

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

    public boolean complete(int id) {
        Optional<SwapRequest> found = requests.find(id);
        if (found.isEmpty() || !found.get().isWaiting()) {
            return false;
        }
        found.get().complete();
        return true;
    }

    public boolean cancel(int id) {
        Optional<SwapRequest> found = requests.find(id);
        if (found.isEmpty() || !found.get().isWaiting()) {
            return false;
        }
        found.get().cancel();
        return true;
    }
}
