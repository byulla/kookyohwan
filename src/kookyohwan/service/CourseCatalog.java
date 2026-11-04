package kookyohwan.service;

import kookyohwan.model.Course;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 과목 목록. 과목 존재 확인과 코드->과목명 변환은 여기 한 곳에서만 한다. */
public class CourseCatalog {
    private final Repository<String, Course> courses = new Repository<>(); // 코드 -> 과목

    public void add(Course course) {
        courses.put(course.getCode(), course);
    }

    public List<Course> all() {
        return courses.all();
    }

    public int size() {
        return courses.size();
    }

    public Optional<Course> findByCode(String code) {
        return courses.find(code);
    }

    /** 같은 과목명의 분반 목록 (생명과 나눔 -> 01, 02, 03) */
    public List<Course> findByName(String name) {
        List<Course> result = new ArrayList<>();
        for (Course c : courses.all()) {
            if (c.getName().equals(name)) {
                result.add(c);
            }
        }
        return result;
    }

    public boolean existsName(String name) {
        return !findByName(name).isEmpty();
    }

    /**
     * 희망 과목 입력을 과목명으로 해석(계획서 5.3).
     * 코드(GE101-01)면 그 과목명으로, 과목명이면 그대로, 둘 다 아니면 empty. 공백 정규화는 하지 않는다.
     */
    public Optional<String> resolveName(String input) {
        Optional<Course> byCode = findByCode(input);
        if (byCode.isPresent()) {
            return Optional.of(byCode.get().getName());
        }
        return existsName(input) ? Optional.of(input) : Optional.empty();
    }
}
