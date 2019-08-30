package org.dhis2.fhir.adapter.fhir.express;

/**
 *
 * @author Charles Chigoriwa
 */
public class NoOutcomeOperationException extends RuntimeException {

    public NoOutcomeOperationException(String message) {
        super(message);
    }

    public NoOutcomeOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
