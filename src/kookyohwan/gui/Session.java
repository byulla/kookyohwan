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

public class Session {
    static final int ROW_GAP = 6;

    private final CourseCatalog catalog;
    private final StudentRegistry registry;
    private final RequestBoard board;
    private final MatchService matcher;
    private final ExchangeService exchange;
    private final EnrollmentService enrollment;
    private final Function<Student, String> saver;
    private final List<Runnable> listeners = new ArrayList<>();
    private Consumer<String> statusSink = message -> { };
    private Consumer<String> wantSink = wantName -> { };
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

    public void setMe(Student student) {
        this.me = student;
        fireChanged();
    }

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

    public void say(String message) {
        statusSink.accept(message);
    }

    public void save() {
        say(saver.apply(me));
    }

    public void saveQuietly() {
        String result = saver.apply(me);
        if (result.startsWith("[오류]")) {
            say(result);
        }
    }

    public void setWantSink(Consumer<String> wantSink) {
        this.wantSink = wantSink;
    }

    public void requestExchange(String wantName) {
        wantSink.accept(wantName);
    }

    public String nameOf(String studentId) {
        return registry.find(studentId).map(Student::getName).orElse(studentId);
    }

    public static String label(Course c) {
        return c.getName() + " " + c.getSection() + " (" + c.timesToString() + ")";
    }
}
