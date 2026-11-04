package kookyohwan;

import kookyohwan.model.Course;
import kookyohwan.model.MatchResult;
import kookyohwan.model.Student;
import kookyohwan.model.SwapRequest;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.MatchService;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.RequestRejectedException;
import kookyohwan.service.StudentRegistry;
import kookyohwan.storage.SampleData;
import kookyohwan.ui.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * 1단계 최소 버전(계획서 13절 S-min): 메뉴·파일 없이
 * 희망 과목명 한 번 입력 -> 요청 등록 -> 매칭 -> 결과 표. 2단계에서 ConsoleUI 메뉴 루프로 바뀐다.
 */
public class Main {
    private static final String DEFAULT_STUDENT_ID = "202612345"; // 학생A
    private static final String DEFAULT_GIVE_CODE = "GE201-01";   // 프랑스어 입문

    public static void main(String[] args) {
        // 1) 준비 데이터 (SampleData 하드코딩)
        CourseCatalog catalog = new CourseCatalog();
        StudentRegistry registry = new StudentRegistry();
        RequestBoard board = new RequestBoard(catalog);
        SampleData.loadCourses(catalog);
        SampleData.loadStudents(registry, catalog);
        SampleData.loadRequests(board, catalog);
        MatchService matcher = new MatchService(board, registry);

        Student me = registry.find(DEFAULT_STUDENT_ID).orElseThrow();
        Course give = catalog.findByCode(DEFAULT_GIVE_CODE).orElseThrow();
        System.out.println("[시작 상태] " + me.getName() + " 시간표 " + me.getTimetable().size() + "과목"
                + " / 다른 학생의 대기 요청 " + board.waitingExcept(me.getId()).size() + "건 (SampleData)");
        System.out.println("내놓을 과목: " + give.getName() + " (" + give.timesToString() + ")");

        // 2) 희망 과목명 입력 -> 요청 등록 (거절이면 같은 입력을 다시 받음)
        Scanner in = new Scanner(System.in);
        SwapRequest mine = null;
        while (mine == null) {
            System.out.print("희망 과목명 > ");
            if (!in.hasNextLine()) {
                return;
            }
            String input = in.nextLine().trim();
            if (input.isEmpty()) {
                System.out.println("[안내] 입력이 없어 종료합니다.");
                return;
            }
            Optional<String> wantName = catalog.resolveName(input);
            if (wantName.isEmpty()) {
                System.out.println("[거절] " + input + ": 과목 목록에 없습니다. 요청 목록을 변경하지 않았습니다.");
                continue;
            }
            if (!wantName.get().equals(input)) {
                System.out.println("[확인] " + input + " -> " + wantName.get()); // 코드를 과목명으로 변환(5.3)
            }
            try {
                mine = board.register(me, give, wantName.get());
            } catch (RequestRejectedException e) {
                System.out.println("[거절] " + e.getMessage() + ". 요청 목록을 변경하지 않았습니다.");
            }
        }
        System.out.println("[등록] 요청 #" + mine.getId() + ": " + mine.summary());
        System.out.println();

        // 3) 매칭 -> 결과 표
        List<MatchResult> results = matcher.match(mine, me.getTimetable());
        printResults(results, registry);
    }

    private static void printResults(List<MatchResult> results, StudentRegistry registry) {
        if (results.isEmpty()) {
            System.out.println("[안내] 조건에 맞는 상대가 없습니다.");
            return;
        }
        System.out.println(row("요청", "요청자", "받을 과목", "판정", "사유"));
        List<String> possibleNames = new ArrayList<>();
        for (MatchResult r : results) {
            SwapRequest req = r.getRequest();
            String who = registry.find(req.getStudentId()).map(Student::getName).orElse(req.getStudentId());
            Course c = r.getReceiveCourse();
            String course = c.getName() + " " + c.getSection() + " (" + c.timesToString() + ")";
            System.out.println(row("#" + req.getId(), who, course, "[" + r.getVerdict().getLabel() + "]", r.getReason()));
            if (r.isPossible()) {
                possibleNames.add(who);
            }
        }
        System.out.println();
        System.out.println("[결과] 교환 가능한 상대 " + possibleNames.size() + "명"
                + (possibleNames.isEmpty() ? "" : " (" + String.join(", ", possibleNames) + ")"));
    }

    /** 표 한 줄. 한글 폭 때문에 String.format 대신 TextUtil.padRight로 맞춘다(계획서 12절). */
    private static String row(String id, String who, String course, String verdict, String reason) {
        return " " + TextUtil.padRight(id, 6) + TextUtil.padRight(who, 8)
                + TextUtil.padRight(course, 27) + TextUtil.padRight(verdict, 8) + reason;
    }
}
