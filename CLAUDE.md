# 구교환 (求교환) — Java 기말 프로젝트

수강신청 정정기간에 정원이 마감된 교양 과목을 **1:1로 교환할 상대를 찾아 주는 콘솔 프로그램**.
한 명이 자기 PC 터미널에서 쓰는 로컬 도구이며, 데이터는 CSV 파일에 저장·복원한다.
원본 계획서: `src/kookyohwan/docs/plan.pdf` (설계·흐름·완료 기준 AC1~AC11·시연 스크립트). 이 문서와 충돌하면 계획서를 따르되, 코딩 규칙은 이 문서가 우선.

## 실행 환경

- JDK 17 이상, **외부 라이브러리 없음** (표준 API만). 빌드 도구 없이 `javac`/`java`로 실행.
- 소스·데이터 모두 UTF-8. 컴파일 시 `-encoding UTF-8` 필수.
- `data/courses.csv`처럼 **상대 경로**로 파일을 읽으므로 반드시 프로젝트 루트(`kookyohwan/`)에서 실행. IDE Working directory도 루트.
- `out/`은 javac 결과물 (제출 제외).

```bash
cd kookyohwan
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -cp out kookyohwan.Main
```

Windows cmd: `dir /s /b src\*.java > sources.txt` → 같은 javac → `chcp 65001` → `java -Dfile.encoding=UTF-8 -cp out kookyohwan.Main`

## 패키지 구조

소스 루트는 `src/`, 루트 패키지는 `kookyohwan`. 폴더 = 패키지.

```
kookyohwan/
├── CLAUDE.md
├── README.md                    실행 안내
├── data/                        CSV (준비 데이터 + 저장 파일)
│   ├── courses.csv              과목 목록 — 읽기 전용. 가천대 2026-2학기 개설 과목 3,242개 (docs의 xlsx 4개에서 변환)
│   ├── students.csv             학생 시간표 — 종료 시 저장
│   └── requests.csv             교환 요청 — 종료 시 저장
├── data-ac8/                    AC8(S13) 시연용 데이터: 학생F + 요청 #6~#8 포함. `java -cp out kookyohwan.Main data-ac8`
├── out/                         javac 결과 (제출 제외)
└── src/kookyohwan/
    ├── Main.java                콘솔 진입점: 복원 → login() → ConsoleUI.run() → 저장
    ├── GuiMain.java             GUI 진입점(Swing): 복원 → 학번 입력 → 창 → 닫을 때 저장. `java -cp out kookyohwan.GuiMain`
    ├── gui/                     Swing 화면. 콘솔과 같은 service를 그대로 쓴다 (규칙·겹침·매칭을 다시 만들지 않음)
    │   ├── Session.java         서비스들 + 현재 학생 + 저장 동작 + 화면 갱신 알림
    │   ├── MainPanel.java       위 사용자/전환/저장, 탭 4개(내 시간표·과목 목록·교환 요청·매칭), 아래 상태 줄
    │   ├── TimetableCanvas.java 시간표 앱 모양 격자(docs/img.png): 파스텔 블록, 과목명+교수·강의실, 1교시=오전 9시
    │   ├── TimetablePanel / CoursePanel / RequestPanel / MatchPanel
    │   ├── TimetableActions.java 시간표 추가·교체·거절 흐름 (두 탭이 공유)
    │   └── LoginGate.java       학번 입력 대화상자 (시작·전환 공용)
    ├── docs/                    plan.pdf 계획서 + 학교 시간표 xlsx 원본 4개 (패키지 아님, 자바 소스 넣지 말 것)
    ├── model/                   순수 데이터 + 자기 상태에 대한 판정. 다른 패키지에 의존하지 않음
    │   ├── Day.java             enum 요일 MON~SUN, 한글 라벨
    │   ├── TimeSlot.java        요일 + 교시 범위, overlaps()
    │   ├── CourseType.java      enum MAJOR(전공) / GENERAL(교양) / TEACHING(교직) / MILITARY(군사)
    │   ├── Course.java
    │   ├── Timetable.java       findConflicts() — 겹침 판정은 여기 한 곳
    │   ├── Student.java
    │   ├── RequestStatus.java   enum WAITING / MATCHED / CANCELLED
    │   ├── SwapRequest.java
    │   ├── MatchVerdict.java    enum POSSIBLE / EXCLUDED_MINE / EXCLUDED_PARTNER
    │   └── MatchResult.java
    ├── service/                 보관·조회·등록·매칭 로직. UI와 파일을 모름
    │   ├── Repository.java      제네릭 Repository<K, V>
    │   ├── CourseCatalog.java   과목 목록, resolveName()
    │   ├── StudentRegistry.java 학생 목록 (로그인·전환·상대 시간표)
    │   ├── RequestBoard.java    요청 등록·조회·상태 변경
    │   └── MatchService.java    교차 일치 + 겹침 검사 → 가능/제외
    ├── storage/                 CSV 읽기·쓰기
    │   ├── Storage.java         인터페이스
    │   └── FileStorage.java     CSV 구현 (1단계용 SampleData는 3단계에서 data/*.csv로 옮기고 삭제)
    └── ui/                      메뉴, 입력, 출력 문자열
        ├── ConsoleUI.java       메뉴 루프
        ├── TimetableGrid.java   요일×교시 격자 출력
        └── TextUtil.java        padRight()/cut()/center() 한글 폭 보정
```

의존 방향: `ui → service → model`, `storage → model`, `Main`만 `storage` 구현체(FileStorage)를 안다. `service`와 `ui`는 `Storage` 인터페이스조차 몰라야 한다(파일 저장 방식이 바뀌어도 핵심 로직 불변).

## 클래스 설계

### model

| 클래스 | 상태 (필드) | 동작 |
|---|---|---|
| `Day` (enum) | MON("월") … SUN("일") (토·일은 실제 시간표에 주말 수업이 있어 추가), `label` | `getLabel()`, `static fromLabel("월")` |
| `TimeSlot` | `day`, `start`, `end` (끝 교시 포함) | `overlaps(other)`, `static parse("월3-4")`, `toString()` → `"월 3-4"` |
| `CourseType` (enum) | MAJOR("전공"), GENERAL("교양"), TEACHING("교직"), MILITARY("군사"), `label` | `getLabel()` |
| `Course` | `code`, `name`, `section`(분반), `type`, `List<TimeSlot> times` | `isGeneral()`, `timesToString()` → `"수 1-2, 금 3-4"` |
| `Timetable` | `List<Course> courses` | `add(course)`, `findConflicts(incoming, giving)` → `List<Course>`, `findConflict(incoming, giving)` → `Optional<Course>`, `replace(toRemove, course)`, `contains(code)`, `getCourses()` (unmodifiable) |
| `Student` | `id`(학번), `name`, `Timetable timetable` | getter만 |
| `RequestStatus` (enum) | WAITING("대기"), MATCHED("매칭완료"), CANCELLED("취소") | `getLabel()` |
| `SwapRequest` | `id`(요청 번호), `studentId`, `Course giveCourse`, `wantName`(희망 과목명), `status` | `isWaiting()`, `summary()` → `"프랑스어 입문 (화 1-2) -> 생명과 나눔"` |
| `MatchVerdict` (enum) | POSSIBLE("가능"), EXCLUDED_MINE("제외"), EXCLUDED_PARTNER("제외") | `getLabel()` |
| `MatchResult` | 후보 `SwapRequest`, 받을 `Course`, `MatchVerdict`, 사유 문자열 | `isPossible()`, 정적 팩토리 `possible(...)`, `excluded(...)` |

### service

| 클래스 | 상태 | 동작 |
|---|---|---|
| `Repository<K, V>` | `Map<K, V>` — `LinkedHashMap` (입력 순서 유지) | `put`, `find(key)` → `Optional<V>`, `exists(key)`, `all()`, `size()` |
| `CourseCatalog` | `Repository<String, Course>` (코드 → 과목) | `all()`, `findByCode(code)`, `findByName(name)` → 분반 목록, `search(keyword)` 메뉴 1 검색, `existsName(name)`, `resolveName(input)` → 코드면 과목명으로, 과목명이면 그대로, 아니면 empty |
| `StudentRegistry` | `Repository<String, Student>` (학번 → 학생) | `find(id)` → `Optional<Student>`, `register(id, name)` 빈 시간표로 등록, `all()` |
| `RequestBoard` | `Repository<Integer, SwapRequest>`, 다음 요청 번호 | `register(student, give, wantName)`, `waitingOf(studentId)`, `waitingExcept(studentId)`, `isGiving(studentId, courseCode)`, `cancel(id)` |
| `MatchService` | `RequestBoard`, `StudentRegistry`(상대 시간표 조회용) | `match(myRequest, myTimetable)` → `List<MatchResult>` |

### storage

| 클래스 | 내용 |
|---|---|
| `Storage` (interface) | `loadCourses`, `loadStudents`, `loadRequests`, `saveStudents`, `saveRequests` |
| `FileStorage` | 데이터 폴더 경로를 가짐. CSV 한 줄 ↔ 객체 변환. `courses.csv` 없으면 안내 후 종료, `students`/`requests` 없으면 빈 상태로 시작 |
| ~~`SampleData`~~ | 1단계 전용 하드코딩 데이터. 3단계에서 `data/*.csv`로 옮기고 삭제함 (storage가 service에 의존하던 것도 같이 없어짐) |

### ui

| 클래스 | 내용 |
|---|---|
| `ConsoleUI` | `Scanner`, 현재 학생, 서비스들. `run()` 메뉴 루프, `showCourses`, `showTimetable`, `registerRequest`, `showRequests`, `runMatch`, `switchUser()`(저장 → `login()` 재호출) |
| `TimetableGrid` | 상수 `CELL=14`, `MAX_PERIOD=14`, `BASE_PERIOD=9`(늦은 수업이 없으면 9교시까지만), `USE_COLOR=false`. 토·일 열은 주말 수업이 있을 때만. `print(timetable)`: `EnumMap<Day, Course[]>` 격자, `border(grid, p)` 가로선 |
| `TextUtil` | `displayWidth`, `padRight`, `cut`, `center` — 한글·한자는 2칸으로 계산 |
| `Main` | 복원 → `login()`(학번 입력, 없으면 이름 입력 후 새 학생) → `ConsoleUI.run()` → 저장. `login()`은 메뉴 9에서도 재사용 |

### 상태 변경 책임 (반드시 지킬 것)

- 시간표 변경은 **`Timetable`만** (`add`, `replace`). 요청 목록 변경은 **`RequestBoard`만**. 학생 목록 변경은 **`StudentRegistry`만**. 파일 쓰기는 **`FileStorage`만**.
- **`MatchService`와 `ConsoleUI`는 어떤 상태도 바꾸지 않는다.** 매칭은 조회 전용 — 요청 상태를 `MATCHED`로 자동 변경하지 않는다.
- 겹침 판정 코드는 **`Timetable.findConflicts` 한 곳**. R2(시간표 추가 거절)와 R5(매칭 제외), R5-2(상대 시간표)가 전부 이 메소드를 호출한다. `MatchService`에 시간 비교 코드를 두지 말 것.
- 과목 존재 확인·코드→과목명 변환은 `CourseCatalog` 한 곳. 요청 등록 조건 검사는 `RequestBoard`가 담당 (UI에서 직접 검사하지 않는다).
- 학생 생성은 `login()` 한 곳 (시작 때와 메뉴 9가 같은 메소드).

## 코딩 규칙

### 이름 짓기

- **getter는 `getXxx()` 스타일.** 계획서 부록 A의 `name()`, `code()`, `day()`, `courses()`, `label()` 같은 짧은 접근자는 **그대로 쓰지 말고** `getName()`, `getCode()`, `getDay()`, `getCourses()`, `getLabel()`로 바꿔 구현한다.
- boolean 판정은 `isXxx()` (`isGeneral()`, `isWaiting()`, `isPossible()`, `isGiving()`).
- 필드 접근자가 아닌 조회·동작 메소드는 동사형 그대로 (`find`, `exists`, `all`, `register`, `match`, `overlaps`, `parse`, `summary`).
- 클래스는 PascalCase, 메소드·필드는 camelCase, 상수는 UPPER_SNAKE (`CELL`, `MAX_PERIOD`, `USE_COLOR`).
- enum 상수는 영문 대문자, 화면 라벨은 enum 안의 `label` 필드에 둔다. 파일에는 enum 이름(`WAITING`)을 저장하고, 화면에는 `getLabel()`(“대기”)을 쓴다.

### 자바 기능 사용 방침

- **enum**: 요일·구분·상태·판정은 문자열이 아니라 enum으로. `==` 비교. 라벨↔enum 변환(`fromLabel`)은 enum 안에.
- **컬렉션**: 시간 목록·과목 목록은 `List`, 코드로 찾기는 `Map`, 격자는 `EnumMap<Day, Course[]>` (열 순서가 enum 선언 순서로 보장).
- **제네릭**: `Repository<K, V>`를 과목(String 키)·학생(String 키)·요청(Integer 키)이 공유.
- **Optional**: ‘없음’은 `null`이 아니라 `Optional.empty()`로 반환 (`find`, `findConflict`, `resolveName`).
- **인터페이스**: `Storage ← FileStorage`. 나중에 DB 구현체를 추가해도 service 코드는 한 줄도 바뀌지 않아야 한다.
- **예외**: `NumberFormatException`(입력 파싱), `IOException`(파일)은 잡아서 메시지 출력 후 계속 진행. 잘못된 입력으로 프로그램이 종료되면 안 된다(AC9).
- 필드는 `private final` 우선, 생성자 주입. 불변 컬렉션 반환은 `Collections.unmodifiableList`.
- `Scanner`는 **`nextLine()`만** 사용하고 `trim()`. `nextInt()`와 섞지 말 것(개행 문제).
- 조건 분기는 Java 17 `switch` 화살표 문법 사용 가능.

### 도메인 규칙 (계획서 5.3·16절 결정)

- 교환 대상은 **교양 과목만**. 전공은 시간표에는 넣되 교환 요청 불가.
- 자기 자신의 요청끼리는 매칭하지 않는다(같은 학번 제외).
- 희망 과목은 **과목명 단위**(분반 무관). 매칭 때는 상대가 내놓는 **분반의 실제 시간**으로 겹침 검사.
- 희망 과목란에 학수번호(`11393001`)를 넣으면 과목명으로 변환해 등록하고 `[확인] 11393001 -> 생명과 나눔`처럼 변환 사실을 보여 준다. 코드도 과목명도 아니면 거절.
- 공백 정규화는 하지 않는다(입력 그대로 비교).
- 겹침 검사 때 **내놓는 과목은 비교에서 제외**한다(내놓으면 그 자리가 비므로). `findConflicts(incoming, giving)`의 `giving`이 이 역할.
- 같은 과목을 내놓는 요청을 여러 건 올릴 수 있다. 내놓는 과목 + 희망 과목이 **완전히 같은** 내 대기 요청만 중복 거절.
- 가능한 상대가 여러 명이면 요청 번호(등록 순)대로 전부 보여 주고 선택은 사용자가 한다.
- 시간표 추가 시 겹치면 겹치는 과목을 전부 보여 주고 `(y/n)` 교체 확인. 빠질 과목이 내 대기 요청의 내놓는 과목이면 교체 대신 거절.
- 상대 시간표가 `students.csv`에 없으면 내 쪽만 검사하고 사유에 “(상대 시간표 미확인)” 표시.
- 로그인은 학번. 이름은 출력용.
- **최대 18학점**(`Timetable.MAX_CREDITS`). 넘으면 시간표 추가·교체를 거절하고(`[거절] …: 최대 18학점을 넘습니다`), 매칭에서도 교환 후 내 쪽이나 상대 쪽이 넘으면 `[제외] 내 학점 초과` / `상대 학점 초과`. 학점 판정은 `Timetable.exceedsLimit` 한 곳. 파일 복원(`Timetable.restore`)은 학점을 보지 않는다(제한 전에 저장한 시간표도 읽히게).

## 터미널 UI 규칙

- 한 줄 **80칸 안팎** (한글은 2칸). `String.format`의 `%-10s`는 글자 수 기준이라 한글이 섞이면 어긋남 → 표 정렬은 반드시 `TextUtil.padRight`로.
- **ASCII와 한글만.** 화살표는 `->`, `<->`. 이모지·특수 화살표(→ ↔ ✔)·박스 문자 금지 (Windows cmd 호환).
- 모든 안내는 줄 머리에 `[태그]`:

| 태그 | 언제 | 예 |
|---|---|---|
| `[복원]` `[저장]` | 파일 읽기·쓰기 결과 | `[복원] requests.csv 교환 요청 5건` |
| `[확인]` | 입력 요약, 정상 조회 | `[확인] 프랑스어 입문 (화 1-2) -> 생명과 나눔` |
| `[등록]` | 상태 변경 성공 | `[등록] 요청 #6 등록 완료  상태: 대기` |
| `[거절]` | 거절 조건에 걸림 (상태 유지) | `[거절] 컴퓨터공학개론: 전공 과목은 교환 요청을 등록할 수 없습니다.` |
| `[취소]` | y/n에서 n | `[취소] 시간표를 변경하지 않았습니다.` |
| `[오류]` | 입력 형식 오류 | `[오류] 숫자를 입력하세요.` |
| `[안내]` | 진행할 수 없는 상태 | `[안내] 대기 중인 내 교환 요청이 없습니다. …` |
| `[결과]` | 매칭 요약 | `[결과] 교환 가능한 상대 2명` |
| `[가능]` `[제외]` | 매칭 표의 판정 열 | `[제외] 겹침: 컴퓨터공학개론` |

- 거절 형식 통일: **`[거절] 대상: 사유. …을 변경하지 않았습니다.`** 그리고 같은 입력을 다시 받는다.
- 상태를 크게 바꾸는 동작(교체, 등록)은 `(y/n)`으로 먼저 확인. n이면 `[취소]`.
- 하위 입력에서 빈 줄(Enter)이면 메뉴로 복귀. 목록 출력 후에는 메뉴를 다시 표시.
- 프롬프트는 `이름 > ` 형식으로 무엇을 넣는지 명시 (예: `희망 과목명 (분반 무관) > `).
- 메뉴 번호 고정: `1` 과목 목록(3천 개가 넘어 전체 출력 대신 검색어로 조회, 최대 30줄), `2` 내 시간표/과목 추가, `3` 요청 등록, `4` 요청 목록, `5` 매칭 실행, `9` 사용자 전환, `0` 저장하고 종료. 그 밖의 번호는 `[오류] 메뉴에 있는 번호(0~5, 9)를 입력하세요.`
- 시간표 격자(메뉴 2): 열 = 요일(월~금), 행 = 교시(1~`MAX_PERIOD`). 칸 폭 14, 교시 열 4칸. 블록 첫 교시 줄에 과목명, 둘째 줄에 과목 코드. 가로선은 **위·아래 칸의 과목이 다른 곳에만** 긋는다(연속 교시가 한 덩어리로 보이게). 과목명이 14칸을 넘으면 12칸에서 자르고 `..`. ANSI 색은 `USE_COLOR` 옵션, 기본 끔. 격자 아래 `전공: … 교양: …` 한 줄.
- 목표 화면은 계획서 13절(S-min, S1~S14)에 있다. 구현 후 실제 출력과 비교하고, 다르면 코드나 문서를 고친다.

## 데이터 파일 형식 (`data/`)

- UTF-8, 첫 줄 헤더, 구분자 쉼표. 한 칸에 값이 여러 개면 **세미콜론(`;`)** (과목 코드 목록, 시간 목록).
- 시간 표기 `월3-4` = 월요일 3교시부터 4교시까지(교시 번호만, 실제 시각 변환은 범위 밖).
- 상태는 enum 이름(`WAITING`)으로 저장.
- 값에 쉼표가 있으면 큰따옴표로 감싼다 (`FileStorage.splitCsv`/`quote`). 처음에는 '과목명에 쉼표 금지'였지만 실제 교과목명에 쉼표가 있어(예: `P-실무프로젝트 (4주/융합,메이커스)`) 이름을 그대로 쓰려고 바꿈.
- **과목 코드 = 학교 학수번호 8자리 그대로**(`11393001`), 분반 = 끝 3자리(`001`), 과목명 = 교과목명 그대로(앞뒤 공백만 제거). 구분은 이수 구분을 묶음: 전필·전선 → MAJOR, 교필·교선·계교 → GENERAL, 교직 → TEACHING, 군사 → MILITARY. 시간은 `화1 ,화2` 같은 교시 나열을 `화1-2` 구간으로 합침.
- 복원 순서: `courses.csv` → `students.csv` → `requests.csv` (요청이 과목·학생을 참조하므로).
- 저장 시점: 메뉴 0과 메뉴 9. `students.csv`, `requests.csv`를 통째로 다시 쓴다. `courses.csv`는 읽기만.

```
courses.csv   코드,과목명,분반,구분,시간,교수,강의실,학점   예) 09392004,이산수학,004,MAJOR,월4-6,이경복,AI관-415,3  (교수·강의실·학점 칸은 없어도 읽힘. 학점이 없으면 0)
students.csv  학번,이름,과목코드                   예) 202612345,학생A,09392004;08306001;05306002
requests.csv  번호,학번,내놓는과목코드,희망과목명,상태  예) 6,202612345,05306002,생명과 나눔,WAITING
```

준비 데이터(학생 6명, 요청 #1~#5)는 계획서 14절의 구조를 실제 과목으로 옮긴 것. 완료 기준 AC1~AC11의 시작 상태이므로 임의로 바꾸지 말 것. 기본 사용자는 학생A(`202612345`).

계획서의 가상 과목 → 실제 과목 대응 (계획서 화면과 비교할 때 이 표로 읽을 것):

| 계획서 | 실제 과목 (학수번호, 시간) | 역할 |
|---|---|---|
| 프랑스어 입문 GE201-01 | 프랑스어1 `05306002` (화 3-4) | 학생A가 내놓는 교양 |
| 컴퓨터공학개론 CS101-01 | 이산수학 `09392004` (월 4-6) | 학생A 전공, 학생E 분반과 겹침 |
| C프로그래밍 CS102-01 | 운영체제 `08306001` (목 4-5, 금 3) | 학생A 전공 |
| 생명과 나눔 01 | 생명과 나눔 `11393006` (수 5-6) | 학생B·C·F가 내놓음 — 가능 |
| 생명과 나눔 02 | 생명과 나눔 `11393002` (월 3-4) | 학생E — 내 시간표 겹침 |
| 생명과 나눔 03 | 생명과 나눔 `11393003` (화 3-4) | 학생G — 프랑스어1 자리라 가능 |
| 심리학의 이해 | 심리학과 예술 `11934001` (화 5-6) | 학생D가 내놓음 (후보 아님) |
| 일본어 입문 | 일본어1 | 학생C의 희망 (후보 아님) |
| 영화의 이해 | 영화의 이해 `14868001` | AC6 후보 없음 |
| 글쓰기 기초 GE601-01 | 대학생을 위한 학술적글쓰기 `14632001` (화 3-4) | AC5-2, 학생F 시간표(AC8) |
| (학생B 전공) CS101-01 | 데이터통신 `07251001` (월 5-6, 목 5) | 학생B 시간표 2과목 |

## 구현 순서

| 단계 | 범위 | 통과할 완료 기준 |
|---|---|---|
| 1 최소 동작 | `model` 전부 + `Repository` + `CourseCatalog` + `RequestBoard.register` + `Timetable.findConflicts` + `MatchService.match`. 데이터는 `SampleData` 하드코딩. `Main`에서 희망 과목명 한 번 입력받아 매칭 결과 표 출력(메뉴 없음) | AC1, AC2 (S-min 화면) |
| 2 메뉴 연결 | `ConsoleUI` 메뉴 루프, 과목 목록, `TimetableGrid`, 시간표 추가·교체·거절, 요청 등록 거절 조건 전부, 요청 2건 이상 선택, 입력 오류 처리 | AC3, AC4, AC5, AC6, AC9, AC10 |
| 3 저장·복원 + 상대 검사 | `Storage`/`FileStorage`, `SampleData` → CSV 이전, R5-2 상대 시간표 검사와 사유 | AC7, AC8 |
| 4 시험·수정 | AC1~AC11 전체 재실행, 메시지 문구 통일. R7(요청 취소)은 여유 있을 때만 | 전체 AC 통과 |
| 5 정리 | README(실행 명령·데이터 파일 설명), 시연 연습 | 실행 명령 한 번에 성공 |

범위 밖(구현하지 말 것): 알림·연락, 실제 수강신청 시스템 연동, 로그인 인증, DB. 3자 순환 교환(R8)·요청 취소(R7)·1·2·3지망은 추가 후보로 보류 중.

## 작업 시 유의

- 교안 요구: 결정에는 이유를 붙이고, 미결 사항은 지우지 말고 질문으로 남긴다(계획서 16절). 설계를 바꾸면 바꾼 이유를 기록할 수 있게 한 줄 남겨 둘 것.
- 개발 문서(노션)에 옮길 때 문장은 사용자가 본인 말로 다시 쓴다. 코드 주석은 한국어로, 짧게, ‘왜’ 위주.
