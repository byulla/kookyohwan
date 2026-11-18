package kookyohwan.service;

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
