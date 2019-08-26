package org.dhis2.fhir.adapter.fhir.express;

/**
 *
 * @author Charles Chigoriwa
 */
public class UnauthorizedApiException extends ApiException {

    public UnauthorizedApiException() {
    }

    public UnauthorizedApiException(String status, String content) {
        super(status, content);
    }

    public UnauthorizedApiException(String message) {
        super(message);
    }

    public UnauthorizedApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnauthorizedApiException(Throwable cause) {
        super(cause);
    }

    public UnauthorizedApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
