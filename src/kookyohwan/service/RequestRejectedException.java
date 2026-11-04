package kookyohwan.service;

/**
 * 요청 등록 거절(R3). 거절해도 요청 목록은 그대로이므로 예외로 알리고 UI가 "[거절] 대상: 사유" 형식으로 출력한다.
 * RuntimeException을 상속해 호출부에 throws 선언을 강제하지 않는다.
 */
public class RequestRejectedException extends RuntimeException {
    private final String target;
    private final String reason;

    public RequestRejectedException(String target, String reason) {
        super(target + ": " + reason);
        this.target = target;
        this.reason = reason;
    }

    public String getTarget() {
        return target;
    }

    public String getReason() {
        return reason;
    }
}
