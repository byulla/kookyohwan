package kookyohwan.gui;

import kookyohwan.model.Course;
import kookyohwan.model.Student;
import kookyohwan.service.CourseCatalog;
import kookyohwan.service.EnrollmentService;
import kookyohwan.service.ExchangeService;
import kookyohwan.service.MatchService;
import kookyohwan.service.RequestBoard;
import kookyohwan.service.StudentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 화면들이 같이 쓰는 것: 서비스들, 현재 학생, 저장 동작, 화면 갱신 알림.
 * 콘솔(ConsoleUI)과 같은 서비스를 그대로 쓴다 — 규칙 검사·겹침 판정·매칭은 GUI에 다시 만들지 않는다.
 * 저장은 GuiMain이 넘겨준 동작을 부르기만 해서 Storage를 모른다.
 */
public class Session {
    /** 버튼이 있는 줄의 위아래 여백. macOS 버튼은 테두리 밖까지 그려져서 0이면 위아래가 잘린다. */
    static final int ROW_GAP = 6;

    private final CourseCatalog catalog;
    private final StudentRegistry registry;
    private final RequestBoard board;
    private final MatchService matcher;
    private final ExchangeService exchange;
    private final EnrollmentService enrollment;
    private final Function<Student, String> saver; // 저장하고 결과 메시지를 돌려준다
    private final List<Runnable> listeners = new ArrayList<>();
    private Consumer<String> statusSink = message -> { };
    private Student me;

    public Session(Student me, CourseCatalog catalog, StudentRegistry registry, RequestBoard board,
                   MatchService matcher, Function<Student, String> saver) {
        this.me = me;
        this.catalog = catalog;
        this.registry = registry;
        this.board = board;
        this.matcher = matcher;
        this.exchange = new ExchangeService(board, registry, matcher);
        this.enrollment = new EnrollmentService(registry);
        this.saver = saver;
    }

    public CourseCatalog getCatalog() {
        return catalog;
    }

    public StudentRegistry getRegistry() {
        return registry;
    }

    public RequestBoard getBoard() {
        return board;
    }

    public MatchService getMatcher() {
        return matcher;
    }

    public EnrollmentService getEnrollment() {
        return enrollment;
    }

    public ExchangeService getExchange() {
        return exchange;
    }

    public Student getMe() {
        return me;
    }

    /** 사용자 전환. 모든 화면을 새 학생 기준으로 다시 그린다. */
    public void setMe(Student student) {
        this.me = student;
        fireChanged();
    }

    /** 시간표·요청·사용자가 바뀌면 호출할 동작을 등록한다. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink;
    }

    /** 창 아래 상태 줄에 "[등록] …" 같은 안내를 남긴다. */
    public void say(String message) {
        statusSink.accept(message);
    }

    public void save() {
        say(saver.apply(me));
    }

    /** 학번 -> 이름. students.csv에 없는 학번이면 학번 그대로. */
    public String nameOf(String studentId) {
        return registry.find(studentId).map(Student::getName).orElse(studentId);
    }

    /** "생명과 나눔 006 (수 5-6)" */
    public static String label(Course c) {
        return c.getName() + " " + c.getSection() + " (" + c.timesToString() + ")";
    }
}
