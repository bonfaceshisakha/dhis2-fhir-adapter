package org.dhis2.fhir.adapter.fhir.express;

/**
 *
 * @author Charles Chigoriwa (ITINordic)
 */
public class ApiException extends RuntimeException {

    private String status;
    private String content;

    public ApiException() {
    }

    public ApiException(String status, String content) {
        super("Status=" + status + ", Content=" + content);
        this.status = status;
        this.content = content;
    }

    public ApiException(String status, String content, Throwable cause) {
        super("Status=" + status + ", Content=" + content, cause);
        this.status = status;
        this.content = content;
    }

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApiException(Throwable cause) {
        super(cause);
    }

    public ApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
