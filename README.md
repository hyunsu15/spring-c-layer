# repo-facade — 매퍼 파사드 자동 등록 (실행 가능 데모)

MyBatis 매퍼마다 `RepoFacade<M>` 빈을 자동 등록해서, 예외 대신 `DataResultWrapper<T>`(rc + 값)로
반환하는 C 호환 데이터 계층 구성. 상위 폴더의 스케치(.java 낱장 파일)를 실제 동작하는
코드로 완성한 것이다.

## 실행 방법

Maven/Gradle 없이 동작한다. JDK만 있으면 된다(JAVA_HOME 또는 IntelliJ `.jdks` 자동 탐지).

```powershell
powershell -ExecutionPolicy Bypass -File run.ps1
```

- 첫 실행 시 Maven Central에서 의존성 jar 13개를 `lib/`에 내려받는다 (약 12MB).
- 이후 컴파일 → 데모 시나리오 7종 실행. 전부 통과하면 종료 코드 0.
- IntelliJ로 열려면 이 폴더를 Gradle 프로젝트로 import (`build.gradle` 있음).

## 구조

```
core/data/  (프레임워크 — 그대로 재사용 가능)
  DataResultWrapper.java   rc + 값. 실패 결과의 value() 접근은 그 자리에서 터진다
  DataResultStatus.java    rc 코드표 — C 레거시 코드명 (SUCCESS=0, SQL_NO_DATA=10,
                           SQLOVF=11, SQLDUP=20, SQLNULL=21, SQLDISCON=30, SYSTEM_ERROR=99)
  RepoFacade.java          one / many — 매퍼 호출을 DataResultWrapper로 감싸는 인터페이스
                           (쓰기용 exec는 아직 불필요해 주석 처리 — 필요 시 해제)
  DefaultRepoFacade.java   공용 제네릭 구현체 (SimpleJpaRepository와 같은 자리)
  ExceptionClassifier.java 데이터 접근 예외 → DataResultStatus 분류 (버그성 예외는 다시 던짐)
  RepoFacadeRegistrar.java 매퍼 정의를 스캔해 파사드 빈 정의를 양산하는 등록기

demo/  (사용 예시)
  OrderMapper.java         평범한 @Mapper 인터페이스 — 파사드는 이걸 건드리지 않는다
  OrderRow.java            테이블 모양의 Row 객체
  OrderService.java        파사드를 쓰는 서비스 예시 — 주입, rc 분기, 실패 전파, 집계
  AppConfig.java           H2 + MyBatis + 등록기 조립
  DemoMain.java            시나리오 검증 (실행 진입점)
```

## 사용법 — 새 매퍼를 추가할 때

1. `@Mapper` 인터페이스를 평소처럼 만든다. **파사드 관련 작업은 없다** — 등록기가
   매퍼마다 `RepoFacade<M>` 빈을 자동 생성한다.
2. 서비스에서 제네릭 타입으로 주입받아 쓴다:

```java
private final RepoFacade<OrderMapper> orders;

DataResultWrapper<OrderRow> r = orders.one(m -> m.selectById(id));
if (r.fail()) {             // C 스타일 분기 — r.status().code() != 0 과 동일
    return DataResultWrapper.fail(r.status(), r.detail());
}
OrderRow row = r.value();
```

## 설계 결정 요약

- **rc는 enum이 원본, int는 파생.** `DataResultStatus`가 원본이고 `code()`가 숫자를 파생시킨다.
  분기는 `ok()` / `fail()`로 하고, 숫자 rc가 필요하면 `status().code()`로 꺼낸다
  (래퍼의 `rc()` 단축 메서드는 제거 — 분기 수단이 둘로 갈리는 것을 막기 위함).
- **실패 결과의 `value()`는 즉시 터진다.** rc 확인을 빠뜨린 코드가 엉뚱한 곳의 NPE로
  나타나는 것을 막는다 ("예외보단 객체로 내야하는 이유.md"의 원칙).
- **데이터가 원인인 실패만 DataResultWrapper로 바꾼다.** SQL_NO_DATA / SQLOVF / SQLDUP /
  SQLNULL / SQLDISCON. 코드가 원인인 실패(NPE, 매핑 누락,
  잘못된 SQL 등 비-데이터접근 예외)는 그대로 던진다 — 복구가 아니라 수정 대상이므로.
  전부 삼키면 버그가 조용한 장애로 바뀐다.
- **TooManyResults는 cause 체인으로 판별한다.** mybatis-spring이
  `MyBatisSystemException`으로 감싸기 때문에 타입 검사만으로는 못 잡는다
  ("마이 바티스 예외표준화.md"의 문제를 파사드 계층에서 해결).
- **등록기는 BeanFactoryPostProcessor다.** 매퍼 정의를 만드는 MapperScannerConfigurer가
  BeanDefinitionRegistryPostProcessor라서, 같은 단계에 끼면 실행 순서가 보장되지 않는다.
  BFPP 단계는 모든 BDRPP 이후 실행이 보장된다.
- **파사드 빈은 제네릭으로 구분된다.** 빈 정의에 `ResolvableType`으로 제네릭을 실어서
  `RepoFacade<OrderMapper>` 주입 지점에 타입 매칭된다. 매퍼가 100개면 파사드 100개가
  공짜로 생긴다.

## 주의: 트랜잭션과 exec()

(exec는 현재 주석 처리 상태다. 쓰기가 필요해져 해제할 때 아래를 반드시 읽을 것.)

실패를 DataResultWrapper로 받으면 예외가 트랜잭션 경계를 넘지 않으므로 `@Transactional`의
**자동 롤백이 일어나지 않는다.** 이 데모는 단건 쓰기(autocommit)라 문제없지만,
여러 쓰기를 한 트랜잭션으로 묶는 코드라면 규칙이 필요하다:

- 되돌릴 이전 쓰기가 있는 실패 → DataResultWrapper가 아니라 **예외로 던진다** (롤백 필요), 또는
- rc를 보고 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` 호출.

"예외보단 객체로 내야하는 이유.md" Q3의 규칙과 같은 내용이다.

## 다음 단계 (승격 경로)

- 부트타임 검증 추가: 기동 시 모든 매퍼 메서드의 스테이트먼트 존재 확인.
- Row → Domain 변환, 호출 감사 로깅: 필요해지면 DefaultRepoFacade 한 곳에 추가하면
  전 매퍼에 적용된다.
- 호출부 시그니처가 아쉬워지면: 매퍼별 진짜 인터페이스(B/C안 — 코드 생성) 승격.
- 운영 적용 전: Oracle 등 실제 DB의 에러코드로 ExceptionClassifier 분류 검증,
  커스텀 `sql-error-codes.xml` 확장 검토.


## 이후 세부 디테일 밎 유지보수는 회사 사정에 맞게 변경

큰 틀은 다음과 같은 코드이면 될 것 같고, 이것을 이제 회사 상황에 맞게 커스텀을 하면 될 것같다.
