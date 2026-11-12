package com.example.repofacade.core.data;

import java.util.Objects;

import com.example.repofacade.core.CommonResultStatus;
import com.example.repofacade.core.ResultStatus;
import com.example.repofacade.core.ResultWrapper;

/**
 * 파사드(RepoFacade)의 데이터 접근 결과. C의 (rc, out 파라미터) 관례에 대응한다.
 *
 * 상태는 성공이면 {@link CommonResultStatus#SUCCESS}, 실패면 {@link DataResultStatus} 중 하나다.
 * 비교는 {@code r.status() == DataResultStatus.SQLDUP}처럼 {@code ==}로 한다.
 *
 * <p><b>생성은 파사드만 한다.</b> 생성자와 생성용 팩토리({@code ok(T)}, {@code fail(DataResultStatus, String)})는
 * package-private이라 core.data 밖에서는 만들 수 없다 — 이 타입을 보면 "파사드가 DB에서 받아 온 결과"임이 보장된다.
 * 사용(받기·분기·값 꺼내기·전파)은 어디서나 된다: {@code ok()} / {@code fail()} / {@code status()} / {@code value()} /
 * {@code detail()} / {@code valueOr()} / {@code propagate()}는 public이다.
 * (인스턴스 {@code ok()}(상태 확인)와 정적 {@code ok(T)}(생성)는 이름만 같은 별개 메서드다.)
 *
 * <p><b>서비스·중간모듈은 이 타입을 반환하지 않는다.</b> 받은 결과를 {@link ResultWrapper}로 바꿔 내보낸다.
 * <pre>{@code
 * DataResultWrapper<OrderRow> r = facade.sel(() -> orderMapper.selectById(id));
 * if (r.fail()) {
 *     return r.propagate();                  // 실패: rc·detail 그대로 ResultWrapper로
 * }
 * return ResultWrapper.ok(r.value());       // 성공: 값을 담아 ResultWrapper로
 * }</pre>
 *
 * <p>다른 패키지의 테스트에서 가짜 결과가 필요하면 core.data 패키지에 테스트 전용 헬퍼를 두고 거기서 만든다.
 *
 * <p>실패 결과에서 value()를 읽으면 그 자리에서 IllegalStateException을 던진다
 * — rc 확인을 빠뜨린 코드가 엉뚱한 곳에서 NPE로 터지는 것을 막기 위함.
 * (record가 아닌 이유: public record는 생성자를 public으로 둘 수밖에 없어 생성을 막을 수 없다.)
 */
public final class DataResultWrapper<T> {

    private final ResultStatus status;
    private final T value;
    private final String detail;

    /** package-private — 생성은 core.data(파사드·분류기) 안에서만. */
    DataResultWrapper(ResultStatus status, T value, String detail) {
        Objects.requireNonNull(status, "status");
        // 실패인데 번호가 성공 번호면 C 쪽은 rc만 보고 성공으로 읽는다 — 코드표의 번호 실수를 첫 사용에서 잡는다
        if (!status.ok() && status.code() == CommonResultStatus.SUCCESS.code()) {
            throw new IllegalArgumentException("실패 상태가 성공 번호(rc=" + status.code() + ")를 쓴다: "
                    + status.getClass().getName() + "." + status.name());
        }
        this.status = status;
        this.value = value;
        this.detail = detail;
    }

    /** 성공 결과 생성 — package-private, 파사드 전용. */
    static <T> DataResultWrapper<T> ok(T value) {
        return new DataResultWrapper<>(CommonResultStatus.SUCCESS, value, null);
    }

    /** 실패 결과 생성 — package-private, 파사드·분류기 전용. */
    static <T> DataResultWrapper<T> fail(DataResultStatus status, String detail) {
        return new DataResultWrapper<>(status, null, detail);
    }

    /**
     * 실패를 rc·detail 그대로 중간모듈 결과({@link ResultWrapper})로 넘긴다. 반환 값 타입은 호출부에 맞춰진다.
     * {@link ResultWrapper#propagate()}와 같은 이름·같은 규칙 — 받은 실패는 어느 타입이든 propagate로 넘긴다.
     * 성공 결과에 쓰면 값이 사라지므로 IllegalStateException — 성공은 {@code ResultWrapper.ok(r.value())}로 넘긴다.
     */
    public <U> ResultWrapper<U> propagate() {
        if (ok()) {
            throw new IllegalStateException("성공 결과는 propagate할 수 없다 — ResultWrapper.ok(r.value())를 쓸 것");
        }
        return ResultWrapper.fail(status, detail);
    }

    public ResultStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public boolean ok() {
        return status.ok();
    }

    public boolean fail() {
        return status.fail();
    }

    public T value() {
        if (!ok()) {
            throw new IllegalStateException(
                    "실패 결과(rc=" + status.code() + ", " + status.name() + ")에서 value 접근: " + detail);
        }
        return value;
    }

    /** rc 확인 없이 기본값으로 대체하고 싶을 때. */
    public T valueOr(T fallback) {
        return ok() ? value : fallback;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataResultWrapper<?> other
                && status == other.status
                && Objects.equals(value, other.value)
                && Objects.equals(detail, other.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, value, detail);
    }

    @Override
    public String toString() {
        return "DataResultWrapper[status=" + status.name() + "(" + status.code() + "), value=" + value
                + ", detail=" + detail + "]";
    }
}
