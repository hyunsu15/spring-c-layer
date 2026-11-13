# repo-facade — C 호환 데이터 계층 (실행 가능 데모)

Pro*C → Java(Spring + MyBatis) 전환에서 레거시 SQLW 래퍼 자리를 대신하는 데이터 계층이다.
공용 파사드(`RepoFacade`) 하나로 매퍼 호출을 감싸, 예외 대신 `DataResultWrapper<T>`(rc + 값)로 돌려준다.
목표는 C 개발자가 SQLW 호출 뒤 rc로 분기하던 흐름을 그대로 옮겨 쓰게 하는 것이다.

## 왜 필요한가

기존 전환 코드는 매퍼 호출을 보통 이렇게 감싼다.

```java
try {
    CommdaResult res = commdaMapper.sel(param);
    if (ObjectUtils.isEmpty(res)) { item.setCommdaNm(" "); }       // 0건
    else { item.setCommdaNm(res.getMdaCodeNm()); }
} catch (DataAccessException e) {                                  // DB 오류 — 라고 생각하지만
    throw new BizException("8000", e);
}
```

| 문제 | 실제로 생기는 일 |
|---|---|
| `catch (DataAccessException)`이 SQL 버그(컬럼명 오타, 매핑 오류)까지 잡는다 | "고객 없음" 응답, 목록에서 조용히 빠진 계좌로 나온다. 오류 로그도 없다 |
| 0건 검사를 빠뜨려도 컴파일러가 모른다 | 0건인데 그대로 진행하거나 NPE. C와 다르게 동작한다 |
| 0건과 장애가 같은 분기에 섞인다 | DB 연결 장애가 "데이터 없음"으로 처리된다 |
| MyBatis는 2건 이상을 미분류 예외(`MyBatisSystemException`)로 감싼다 | SQLOVF 분기를 옮기려면 cause를 매번 뒤져야 한다 |
| 분기가 try/catch와 if에 흩어진다 | `.pc` 원본과 줄 단위로 맞대어 보기 어렵다 |

## 빠른 시작

```java
@Autowired private RepoFacade facade;                 // 공용 빈 1개. 매퍼는 평소처럼 주입

DataResultWrapper<CommdaResult> r = facade.sel(() -> commdaMapper.sel(param));
if (r.noData()) {                                     // C: sqlw_err == SQL_NO_DATA
    item.setCommdaNm(" ");
} else if (r.fail()) {                                // C: 그 외 nRet != SUCCESS
    throw new BizException("8000", r.cause());        // 사내 프레임워크 예외 자리
} else {
    item.setCommdaNm(r.value().getMdaCodeNm());
}
```

- SQL 버그는 rc로 바뀌지 않고 그대로 터진다. DB 상태(0건·2건 이상·중복·연결 장애)만 rc가 된다.
- rc 확인 없이 `value()`를 꺼내면 그 자리에서 터진다 — 엉뚱한 곳의 NPE가 되지 않는다.
- **0건이 정상인 호출은 `noData()`를 `fail()`보다 먼저 본다.** `valueOr(기본값)`은 장애까지 삼키므로 쓰지 않는다.
- 실패를 위로 넘길 때는 `r.propagate()`, 새 실패는 `ResultWrapper.fail(...)`, 프레임워크 예외로 바꿀 때는 `r.cause()`를 넘긴다.
- C의 SELECT INTO 자리는 매퍼를 단건 타입으로 선언하고(2건 이상 → SQLOVF), 단일 컬럼·집계 조회는 SQL에서 NVL로 감싼다.

## 실행 방법

Maven/Gradle 없이 JDK만 있으면 된다. JDK는 `JAVA_HOME`, 없으면 IntelliJ가 받아 둔 `~/.jdks`에서 찾는다.

```powershell
powershell -ExecutionPolicy Bypass -File run.ps1
```

- 첫 실행 시 Maven Central에서 의존성 jar 14개(JUnit 콘솔 런처 포함)를 `lib/`에 내려받는다.
- 이후 main·test 컴파일 → JUnit 시나리오 테스트(`RepoFacadeScenarioTest`) 실행. 전부 통과하면 종료 코드 0.
- Gradle로는 `gradle test` (JDK 17 이상, Java 17 대상으로 컴파일). `run.ps1`과 달리 JDK를 자동으로 찾지 않으므로
  `JAVA_HOME`이 필요하다. Gradle 래퍼(`gradlew`)는 저장소에 없다. IntelliJ에서는 이 폴더를 Gradle 프로젝트로 import한다.

## 구조

```
core/  (계층 공통 — 프레임워크 무관)
  ResultStatus.java          rc 상태 인터페이스. 도메인이 enum으로 코드표를 더한다. 비교는 ==
                             ok() / fail() / noData() 판정을 상태가 갖는다
  CommonResultStatus.java    공통 상태 SUCCESS(유일한 성공) / FAIL
  ResultWrapper.java         중간모듈·서비스 사이의 일반 결과 (rc + 값 + detail + cause)

core/data/  (데이터 계층 — 기술 무관)
  DataResultWrapper.java   파사드 결과 (rc + 값 + detail + cause). 생성은 파사드만(package-private), 사용은 어디서나.
                           실패의 value() 접근은 즉시 터진다
  DataResultStatus.java    데이터 계층 실패 코드표 — C 레거시 코드명 (SQL_NO_DATA=10,
                           SQLOVF=11, SQLDUP=20, SQLNULL=21, SQLDISCON=30, SYSTEM_ERROR=99)
  RepoFacade.java          sel — 매퍼 호출(람다)을 DataResultWrapper로 감싸는 인터페이스 (단건·목록 구분 없이 하나)
                           (쓰기용 exec는 아직 불필요해 주석 처리 — 필요 시 해제)
  DefaultRepoFacade.java   구현체 — 애플리케이션 전체에 빈 1개 (@Component). 데이터 접근 객체를 들고 있지 않다
  FacadeCallHook.java      파사드 호출 전후 확장점 — 빈으로 등록하면 모든 호출에 적용 (기술별 감시 장치가 쓴다)
  ExceptionClassifier.java 스프링 DataAccessException → DataResultStatus 분류 (코드 버그는 다시 던짐)
  DataExceptionRule.java   기술별 예외 규칙 확장점 — 빈으로 등록하면 일반 규칙보다 먼저 적용 (분류 + 버그 판정)

core/data/mybatis/  (MyBatis + mybatis-spring이 클래스패스에 있을 때만 활성. MyBatis 관련 코드·주의사항은 여기에만 둔다)
  package-info.java                    MyBatis로 쓸 때 주의 (NVL 규칙, 단건·목록 선언, 예외 래핑)
  MyBatisRepoFacadeConfiguration.java  조건부 설정 — 예외 규칙·감시 장치 등록, 감시 스위치(GUARD_PROPERTY, 기본 off)
  MyBatisPresentCondition.java         활성 조건 (스프링 부트 @ConditionalOnClass 대용)
  MyBatisExceptionRule.java            TooManyResults(날것·래핑됨 모두) → SQLOVF. 파라미터·결과 매핑 오류(드라이버
                                       SQLException 포함)와 DB까지 못 간 MyBatis 내부 오류(매핑 누락 등) → 버그로 다시 던짐
  FacadeGuard.java                     감시 장치 — 파사드 호출 전후로 "파사드 안" 표시(FacadeCallHook), SqlSessionFactory에 인터셉터 부착
  FacadeGuardEnabledCondition.java     감시 스위치 조건 — 꺼져 있으면 감시 장치 빈 자체를 등록하지 않는다
  FacadeGuardInterceptor.java          파사드 밖 매퍼 호출을 SQL 실행 직전에 거부 (감시를 켰을 때만 붙는다)

src/test — demo/  (사용 예시 겸 시나리오 테스트. jar에 들어가지 않는다)
  OrderMapper.java             평범한 @Mapper 인터페이스 — 파사드는 이걸 건드리지 않는다 (데모용 함정 메서드 포함)
  OrderRow.java                테이블 모양의 Row 객체
  OrderService.java            파사드를 쓰는 서비스 예시 — @Autowired 필드 주입, rc 분기, 실패 전파, 집계
  AppConfig.java               H2 + MyBatis 조립 (파사드·MyBatis 연동은 스캔으로 자동)
  FailingParamTypeHandler.java 파라미터 세팅 단계 실패(드라이버 오류·연결 끊김) 흉내
  RepoFacadeScenarioTest.java  JUnit 시나리오 — 조회 결과 / 실패 분류 / 결과 객체 규칙 / 서비스 흐름 / 감시 / 호출 훅
  (resources) init.sql         데모 테이블
```

## 사용법

### 기본 — 새 매퍼를 추가할 때

1. `@Mapper` 인터페이스를 평소처럼 만든다. **파사드 관련 작업은 없다.**
2. 서비스는 매퍼를 평소처럼 주입받고, 파사드(공용 빈 1개)를 하나 더 받아 매퍼 호출을 감싼다:

```java
@Autowired private RepoFacade facade;
@Autowired private OrderMapper orderMapper;

DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(id));
if (r.fail()) {             // C 스타일 분기 — nRet != SUCCESS
    return r.propagate();   // rc·detail·cause 그대로 ResultWrapper로
}
return ResultWrapper.ok(r.value());
```

### 단건과 목록

목록도 같은 `sel`이다 — `DataResultWrapper<List<OrderRow>> rows = facade.sel(() -> orderMapper.selectAll());`
단건인지 목록인지는 **매퍼 메서드의 반환 타입**이 정한다(MyBatis가 그걸 보고 selectOne/selectList를 고른다).
C의 SELECT INTO처럼 2건 이상을 SQLOVF로 받아야 하는 SQLW는 매퍼를 단건 타입으로 선언한다 — List로 선언하면 2건도 정상이다.

2건 이상이 어떤 예외로 오든 SQLOVF다 — MyBatis 예외 표준화(번역기 교체 등)를 적용하든 안 하든 호출부는 같다.

| 2건 이상이 오는 형태 | 언제 | 잡는 곳 |
|---|---|---|
| `TooManyResultsException` (날것) | `SqlSession`을 직접 쓸 때 | `MyBatisExceptionRule` |
| `MyBatisSystemException` 안의 `TooManyResultsException` | mybatis-spring 기본 번역기 (지금 기본) | `MyBatisExceptionRule` |
| `TooManyResultsException`이 다른 예외에 한 번 더 감싸인 것 | 사내 프레임워크가 감쌀 때 | `MyBatisExceptionRule` (cause 체인) |
| `IncorrectResultSizeDataAccessException` (cause 유무 무관) | 예외 표준화 번역기를 쓸 때 | `ExceptionClassifier` (타입 일치) |

`IncorrectResultSizeDataAccessException`은 그대로 올 때만 SQLOVF다. 다른 예외에 감싸여 오면 감싼 예외에 따라
SYSTEM_ERROR(`DataAccessException` 계열)가 되거나 그대로 던져진다 — 0건 `EmptyResultDataAccessException`도 같다.

0건 표준 예외(`EmptyResultDataAccessException`)는 `IncorrectResultSizeDataAccessException`의 하위 타입이지만 SQLOVF가 아니라
SQL_NO_DATA다. 회사 고유 예외 타입으로 바꾸는 번역기를 쓴다면, 그 타입을 SQLOVF로 분류하는 `DataExceptionRule` 빈을 함께
등록한다 (등록하지 않으면 파사드가 모르는 예외라 코드 버그로 보고 그대로 던진다).

### 0건이 정상인 호출

결과가 0건이면 단건·목록 모두 SQL_NO_DATA(실패)다. 페이징 루프의 마지막 페이지, "없으면 공백" 같은 호출은
`noData()`를 `fail()`보다 **먼저** 본다 (예시는 위 "빠른 시작"). 순서를 바꾸면 빈 페이지가 조회 오류가 된다.
`valueOr(기본값)`으로 0건을 처리하지 않는다 — 연결 장애 같은 진짜 실패까지 기본값으로 삼킨다.

### 프레임워크 예외로 바꿀 때 — `detail()`과 `cause()`

최상위 서비스가 사내 프레임워크 예외로 바꿀 때는 `cause()`로 원래 예외를 잇는다. `detail()`은 로그용 한 줄 설명이다.

| 결과 | `detail()` | `cause()` |
|---|---|---|
| 성공 | null | null |
| 0건 (매퍼가 null·빈 결과를 반환) | "조회 결과 없음" | null — 예외 없이 판정했으므로 |
| SQLOVF / SQLDUP / SQLNULL / SQLDISCON / SYSTEM_ERROR | 원인 예외의 메시지 | 항상 있음 — 예외를 분류해서만 나온다 |
| 코드 버그 (잘못된 SQL, 매핑 오류 등) | — | 결과 객체가 없다. 예외가 그대로 던져진다 |

`propagate()`로 계층을 넘겨도 detail·cause는 유지된다. 실패 결과에서 `value()`를 부르면 나는 `IllegalStateException`에도
원인 예외가 이어진다. 중간모듈이 직접 판단한 실패에 원인을 붙일 때는 `ResultWrapper.fail(status, detail, cause)`를 쓴다.

### 파사드 밖 직접 호출 감시 (선택, 기본 off)

매퍼를 파사드 없이 부르면(`orderMapper.selectById(id)`) 예외가 rc로 바뀌지 않는다. 이런 호출을 찾으려면
설정에 `repo-facade.mybatis.guard.enabled=true`를 넣는다. 켜면 파사드 밖 호출은 SQL 실행 직전에
`IllegalStateException`(MyBatis가 감싸서 던짐, 메시지에 매퍼 메서드 ID)으로 거부된다.
공통 모듈이 내부에서 매퍼를 직접 부르거나 프레임워크가 직접 실행하는 SQL(페이징 건수 조회 등)이 있으면 그것도 잡히므로,
켜기 전에 확인한다 (아래 "실제 서비스에 적용할 때" 참고).

## 설계 결정

### 상태 코드 (`ResultStatus`)

- **상태 비교는 `==`로 한다.** `if (r.status() == DataResultStatus.SQLDUP)`. 상태는 전부 enum 상수라 `==`가 JVM 차원에서
  보장된다. 규칙은 둘: 코드표는 **enum으로만** 만들고, 코드표에 **SUCCESS를 두지 않는다** (성공은 `CommonResultStatus.SUCCESS` 하나뿐).
- **분기 수단은 `ok()` / `fail()` / `noData()` / `status()`다.** `noData()`는 `fail()`의 하위 판정이라 분기 수단이 둘로
  갈리지 않는다. 숫자 rc가 필요하면 `status().code()`로 꺼낸다 — enum이 원본이고 int는 파생이다
  (래퍼의 `rc()` 단축 메서드는 분기 수단이 둘로 갈리는 것을 막으려고 제거했다).
- **성공 여부는 번호가 아니라 정체로 판단한다.** `ok()`는 `this == CommonResultStatus.SUCCESS`다 — `code() == 0`이 아니다.
  다른 코드표가 우연히 0을 써도 성공이 되지 않고, 번호 체계가 바뀌면(예: 200) `CommonResultStatus.SUCCESS`의
  번호 한 줄만 고치면 된다. 반대로 성공 번호를 쓰는 실패 상태는 결과 객체 생성 시 거부된다
  (C 쪽은 rc만 보고 성공으로 읽으므로). 성공 종류가 여러 개 필요해지면 번호 대역(2xx)이 아니라
  상수별 성공 여부 선언으로 확장한다.
- **0건 판정도 상태가 정한다.** `ResultStatus.noData()`는 기본 false, `DataResultStatus.SQL_NO_DATA`만 true다.
  그래서 `ResultWrapper`로 전파된 뒤에도 `noData()`가 그대로 동작하고, 도메인 코드표도 "찾는 데이터 없음" 상태를
  noData로 선언할 수 있다. 연결 장애·시스템 오류 같은 진짜 실패를 true로 선언하면 0건 분기에 묻히므로 금지다.
- **SUCCESS 상수는 인터페이스(`ResultStatus`)에 두지 않는다.** 인터페이스 상수를 구현체(`CommonResultStatus`)에서
  가져와 초기화하면, default 메서드가 있는 인터페이스라서 구현체가 먼저 초기화되는 순간 상수가 영구히 null이 된다
  (클래스 초기화 순환 — 모든 `ok()`가 false, 멀티스레드에선 초기화 데드락 가능). 그래서 상수는 `CommonResultStatus`에만
  두고 `ResultStatus`는 `ok()` 안에서만 참조한다. 패키지 의존은 `core.data.mybatis → core.data → core` 한 방향이다.
- **rc 코드 대역.** 0~99는 공통·데이터 계층(SUCCESS=0, FAIL=1, DataResultStatus 10~99), 도메인 코드표는 100 이상.
  코드가 겹쳐도 `==` 비교는 틀리지 않지만 C 쪽으로 내보내는 rc가 모호해지므로 대역을 지킨다.
- **도메인 코드표 추가 방법.** `enum XxxResultStatus implements ResultStatus`에 `code()`만 구현하면 끝. 등록 절차 없음.
  "찾는 데이터 없음"을 뜻하는 상태가 있으면 `noData()`를 그 상태에서만 true로 재정의한다.

### 결과 객체 (`DataResultWrapper` / `ResultWrapper`)

- **DataResultWrapper는 파사드만 만든다.** 생성자와 생성용 팩토리(`ok(T)`, `fail(status, detail)`, `fail(status, detail, cause)`)는
  package-private이라 core.data 밖에서는 만들 수 없고, 받기·분기·값 꺼내기·`propagate()`는 public이다.
  이 타입을 보면 "파사드가 DB에서 받아 온 결과"임이 컴파일 단계에서 보장된다. record가 아닌 이유는 public record의
  생성자는 public일 수밖에 없어서다. 다른 패키지의 테스트에서 가짜 결과가 필요하면 core.data에 테스트 헬퍼를 둔다.
- **서비스·중간모듈은 `ResultWrapper`로 주고받는다.** 받은 실패를 넘길 때는 타입과 상관없이 `r.propagate()`
  (DataResultWrapper·ResultWrapper 모두 같은 이름·같은 규칙), 이 계층에서 판단한 새 실패를 만들 때만 `ResultWrapper.fail(...)`.
  같은 일을 두 이름으로 하지 않게 하려는 것이다. 성공 결과의 `propagate()`는 값이 사라지므로 거부된다.
- **실패 결과의 `value()`는 즉시 터진다.** rc 확인을 빠뜨린 코드가 엉뚱한 곳의 NPE로 나타나는 것을 막는다
  ("예외보단 객체로 내야하는 이유.md"의 원칙). 원인 예외가 있으면 그 예외의 cause로 이어진다.
- **실패에는 detail과 cause를 함께 둔다 (detail을 예외로 바꾸지 않았다).** detail은 C의 `sqlw_msg`처럼 모든 실패에
  있는 사람용 설명이고, cause는 rc로 바뀐 원래 예외다. 예외만 두면 0건(예외 없이 판정)이나 이 계층이 직접 판단한
  실패에 넣을 것이 없어 가짜 예외를 만들어야 한다. 반대로 detail만 두면 원인 예외를 프레임워크 예외에 넘기던
  기존 코드가 스택·SQLCODE를 잃는다. 분기에는 쓰지 않는다 — 분기는 상태로 한다. 성공 결과에 cause를 담으면 거부된다.
- **0건 판정 이름은 `noData()`다 (`isEmpty()`가 아니다).** 실제 서비스 코드를 검토해 보니 매퍼 호출의 절반 이상이
  0건을 오류가 아닌 별도 분기로 처리해서, `r.status() == DataResultStatus.SQL_NO_DATA`를 매번 쓰게 하는 대신 메서드로 뒀다.
  `isEmpty()`로 하지 않은 이유: 자바에서 `isEmpty()`는 "값이 없다"로 읽힌다(Optional.isEmpty). 실패 결과는 전부 값이
  없으므로 SQLDISCON도 "empty"처럼 읽혀, 막으려던 혼동(장애를 0건으로 처리)을 이름이 부른다. 또 값이 List일 때
  `r.isEmpty()`와 `r.value().isEmpty()`가 나란히 생긴다. `noData()`는 C의 SQL_NO_DATA와 이름이 맞고, `ok()`/`fail()`과 같은 꼴이다.

### 조회 (`sel`)

- **조회 메서드는 `sel` 하나다 (one / many에서 바꿈).** C의 `Sel…_SDB`처럼 단건·목록을 같은 이름으로 부른다.
  단건·목록 구분은 이미 매퍼 반환 타입에 있고, MyBatis가 그걸 보고 실행 방식과 SQLOVF 여부를 정하므로 파사드에서
  다시 나눌 이유가 없었다. 오히려 둘로 나누면 `one(() -> 목록)`이 컴파일되고 빈 목록이 성공으로 나오는 함정이 생겼다.
  (자바에서 `Supplier<R>`와 `Supplier<List<R>>`는 오버로드할 수 없어서, 결과를 보고 판정한다.)
- **0건 판정: null, 빈 컬렉션·맵·배열, 빈 Optional → SQL_NO_DATA.** 목록이 비어도 단건 0건과 같은 규칙이다(C의 NOT FOUND).
  비었는지 미리 알 수 없는 커서·스트림은 rc를 정할 수 없으므로 거부한다(코드 버그로 보고 예외).
- **단일 컬럼·집계 조회는 SQL에서 NVL(COALESCE)로 감싼다.** `sel()`은 단건 null을 SQL_NO_DATA로 보는데, MyBatis는
  "0건"과 "NULL 한 행"(0건 `MAX()`, 값이 NULL인 단일 컬럼)을 둘 다 null로 준다. 감싸지 않으면 Oracle·C에서는
  NOT FOUND가 아닌 경우도 SQL_NO_DATA가 된다. 행 수를 세는 플러그인으로 코드에서 가를 수도 있지만, 해당하는 경우가
  드물어(Result 객체는 모든 컬럼이 NULL일 때만 null) 모든 쿼리에 플러그인을 거는 것보다 규칙이 낫다고 판단했다.
  레거시 C도 indicator 없이 NULL을 fetch하면 -1405라서 같은 이유로 NVL을 썼다.

### 실패 분류 (`ExceptionClassifier`)

- **C가 rc로 받던 실패만 rc로 바꾼다.** 데이터 상태(SQL_NO_DATA / SQLOVF / SQLDUP / SQLNULL)와
  DB 장애(SQLDISCON, 그 외 DB 오류는 SYSTEM_ERROR). 코드가 원인인 실패는 그대로 던진다 — 복구가 아니라 수정 대상이므로:
  잘못된 SQL(`BadSqlGrammarException` — 스프링에선 이것도 DataAccessException이라 따로 걸러야 한다),
  매핑 누락(`BindingException`), 파라미터·결과 매핑 오류, NPE 등. 이런 버그는 레거시에 없던, 전환하며 생긴 실패라서
  rc로 바꾸면 "조회오류도 정상 진행" 같은 레거시 분기에 묻혀 조용한 장애가 된다 (예: 컬럼명 오타가 "고객 없음" 응답이나
  목록에서 조용히 빠진 계좌로 나타난다). 버그 판정은 분류보다 먼저 하고, 기술별 판정은 `DataExceptionRule.isCodeBug`로 붙인다.
- **매핑 오류는 드라이버가 SQLException을 냈어도 버그다.** 문자 컬럼을 숫자 필드로 받거나(그냥 두면 SQLState 22xxx라
  SQLNULL이 된다), null 파라미터에 jdbcType을 빠뜨려 Oracle이 "부적합한 열 유형: 1111"을 내는 경우(그냥 두면 SYSTEM_ERROR)다.
  파라미터 쪽은 `TypeException`이 체인에 남아 타입으로 잡는다. 결과 쪽은 mybatis-spring이 MyBatis 예외를 벗기고 안쪽
  SQLException만 변환해서 타입이 남지 않으므로, MyBatis가 붙이는 고정 문구(`Error attempting to get column`)로 잡는다.
  연결 끊김은 어느 단계에서 났든 이 판정보다 먼저 걸러져 SQLDISCON이 된다.
- **TooManyResults와 연결 실패는 cause 체인으로 판별한다.** mybatis-spring이
  `MyBatisSystemException`으로 감싸기 때문에 타입 검사만으로는 못 잡는다
  ("마이 바티스 예외표준화.md"의 문제를 파사드 계층에서 해결). 연결 획득 실패(`CannotGetJdbcConnectionException`,
  커넥션 풀 대기 초과 포함)도 같은 식으로 감싸여 오므로 체인에서 찾아 SQLDISCON으로 분류한다.
- **2건 이상은 MyBatis 형태와 스프링 표준 형태를 모두 SQLOVF로 잡는다.** MyBatis 예외 표준화("마이 바티스 예외표준화.md")를
  적용하면 `TooManyResultsException`이 `IncorrectResultSizeDataAccessException`으로 바뀌어 온다. 표준화를 하든 안 하든,
  일부 템플릿만 하든 분류 결과가 같도록 두 형태를 모두 본다. MyBatis 형태는 cause 체인으로, 스프링 표준 형태는
  타입 일치로 본다 (표준 형태는 감싸이지 않고 그대로 온다는 전제).
- **MyBatis 연동은 조건부다.** core.data는 스프링 DAO 예외 계층만 알고, MyBatis 고유 처리는
  core.data.mybatis에 모여 있다. MyBatis가 없으면 그 패키지는 통째로 비활성 (조건은 클래스 로드 전에 판정).
  파사드 자체는 람다만 실행하므로 기술 무관이다. 다른 기술(JPA, JdbcTemplate 등)은 `DataExceptionRule`만 붙이면 된다.

### 파사드 구조와 감시

- **파사드는 빈 하나다 (매퍼별 빈 자동 등록에서 바꿈).** 예전에는 빈 후처리기가 매퍼마다 `RepoFacade<M>` 빈을
  만들었는데, 세 가지 이유로 버렸다.
  (1) 기동 비용: 파사드가 전부 같은 타입(`RepoFacade`)이고 제네릭으로만 구별되니, 스프링이 주입 지점마다 파사드 빈
  N개 전부와 제네릭을 비교한다. 측정 결과 매퍼 1000개 0.45초, 3000개 5초로 매퍼 수의 제곱으로 늘었다
  (SQL 하나당 매퍼 하나인 사내 구조라 매퍼 수 = SQLW 함수 수). 매퍼 직접 주입은 타입 캐시로 거의 0이다.
  (2) mybatis-spring 내부 의존: `MapperFactoryBean` 클래스명 문자열과 빈 정의 속성을 읽어야 해서, 사내 프레임워크가
  이를 상속하거나 버전이 바뀌면 파사드가 등록되지 않는다.
  (3) 사내 관례가 `@Autowired` 필드 주입이라 매퍼는 어차피 서비스가 직접 받는다 — 파사드 하나만 더 받으면 된다.
  잃은 것은 "서비스가 매퍼를 직접 쥐지 않는다"는 구조적 강제인데, 이는 감시 인터셉터로 대신한다.
- **감시 인터셉터는 기본 off, 스위치로 켠다.** 감시 장치(`FacadeGuard`)가 파사드 호출 전후 확장점(`FacadeCallHook`)으로
  "파사드 안" 표시를 켜고 끄고, 인터셉터는 SQL 실행 직전 그 표시가 없으면 거부한다. 꺼져 있으면 감시 장치 빈 자체가
  등록되지 않아 파사드 호출 경로에 아무것도 끼지 않는다. 훅은 등록 순서대로 들어가 역순으로 나오고, `before()`를 마친
  훅의 `after()`는 뒤쪽 훅이나 본문이 던져도 반드시 불린다 — 표시가 스레드에 남아 감시가 뚫리는 일을 막는다.
  파사드(core.data)는 확장점만 알고 감시 장치가 있는지 모른다 — MyBatis 관련 코드는 core.data.mybatis에만 있다.
  인터셉터를 빈으로 내놓지 않고 SqlSessionFactory에 직접 붙이는 이유는, 스프링 부트의 MyBatis 자동 설정이
  Interceptor 빈을 알아서 붙여서 스위치가 무시되거나 두 번 붙기 때문이다.

## 주의: 트랜잭션과 exec()

(exec는 현재 주석 처리 상태다. 쓰기가 필요해져 해제할 때 아래를 반드시 읽을 것.)

실패를 DataResultWrapper로 받으면 예외가 트랜잭션 경계를 넘지 않으므로 `@Transactional`의
**자동 롤백이 일어나지 않는다.** 이 데모는 단건 쓰기(autocommit)라 문제없지만,
여러 쓰기를 한 트랜잭션으로 묶는 코드라면 규칙이 필요하다:

- 되돌릴 이전 쓰기가 있는 실패 → DataResultWrapper가 아니라 **예외로 던진다** (롤백 필요), 또는
- rc를 보고 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` 호출.

"예외보단 객체로 내야하는 이유.md" Q3의 규칙과 같은 내용이다. 쓰기를 하는 중간모듈이 `ResultWrapper`를 반환하게
바꿀 때도 같은 문제가 생긴다.

## 실제 서비스에 적용할 때 확인할 것

기존 전환 서비스(`try { mapper.sel(p) } catch (DataAccessException e)` 형태)를 파사드로 바꿀 때 나오는 항목이다.

- **동작이 바뀐다.** 지금은 SQL 버그가 업무 응답코드(예: "고객 없음")나 조용한 누락으로 나가지만, 바꾸면 예외로 터져
  시스템 오류가 된다. 의도한 변화지만 테스트·운영 쪽에 미리 알린다.
- **0건 분기 순서.** 0건이 정상인 호출은 `noData()`를 먼저 본다. 페이징 루프에서 순서를 틀리면 마지막 페이지가 오류가 된다.
- **원인 예외 넘기기.** 기존 코드가 프레임워크 예외에 원인 예외를 넘기고 있었다면 `r.cause()`로 바꾼다 (0건이면 null).
- **페이징 매퍼.** 페이징 정보를 함께 받는 매퍼를 람다 안에서 불러도 다음 페이지 여부가 정상으로 세팅되는지 확인한다.
- **SQL 확인.** Y/N·건수·집계 조회는 NVL 규칙, C의 SELECT INTO 자리는 매퍼 단건 타입 선언을 확인한다.
- **감시는 공통 모듈 전환 전에는 켜지 않는다.** 정적 유틸·중간모듈이 내부에서 매퍼를 파사드 없이 부르면 켜는 순간 전부 거부된다.
- **레거시 C와 대조한다.** 파사드 분기는 C의 `if (nRet != SUCCESS) { if (sqlw_err == SQL_NO_DATA) … }`와 1:1로 맞으므로,
  `.pc` 원본을 옆에 두고 분기마다 같은 결과인지 본다. 기존 전환 코드에서 0건 검사 누락이나 0건 NPE가 이 과정에서 드러난다.

## 다음 단계

- **중간모듈 시범 전환 (검토안).** 조회 중간모듈부터 파사드 위에 다시 지어 `ResultWrapper`를 반환하게 한다 —
  `facade.sel` → `propagate` → 서비스로 이어지는 rc 전달을 처음으로 실제 코드에서 검증하는 단계다.
  쓰기 중간모듈은 위 트랜잭션 규칙을 먼저 정한다. 기존 예외 기반 모듈을 호출부에서 예외 → rc로 바꾸는 어댑터는
  서비스가 결국 다시 예외로 바꾸므로 왕복일 뿐이라 두지 않는다. 프레임워크 정적 유틸은 감싸지 않는다.
- **감시 경고 모드와 대상 지정.** 거부 대신 로그만 남기는 모드, 감시할 매퍼 네임스페이스·패키지 지정 —
  공통 모듈 전환이 끝나기 전에도 감시를 켤 수 있게.
- **테스트 헬퍼.** 서비스 단위 테스트에서 매퍼를 mock으로 두고 실제 파사드를 조립하거나, 가짜 결과를 만드는 core.data 헬퍼.
- 부트타임 검증: 기동 시 모든 매퍼 메서드의 스테이트먼트 존재 확인.
- Row → Domain 변환, 호출 감사 로깅: 필요해지면 DefaultRepoFacade 한 곳에 추가하면 전 매퍼에 적용된다.
- 호출부를 더 C처럼 만들고 싶어지면: 애너테이션 프로세서로 매퍼마다 SQLW 래퍼 클래스(`SelClntSdb.sel(p)`)를
  컴파일 시점에 생성하는 안. 래퍼마다 타입이 달라 주입 비용은 선형이고, 서비스가 매퍼를 몰라도 된다.
  대신 빌드 인프라가 필요하다.
- 운영 적용 전: Oracle 등 실제 DB의 에러코드로 ExceptionClassifier 분류 검증, 커스텀 `sql-error-codes.xml` 확장 검토.
