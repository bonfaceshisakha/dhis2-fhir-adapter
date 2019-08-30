package org.dhis2.fhir.adapter.fhir.express;

import org.dhis2.fhir.adapter.fhir.repository.FhirRepositoryOperationOutcome;
import org.dhis2.fhir.adapter.fhir.repository.FhirResource;

/**
 *
 * @author Charles Chigoriwa
 */
public interface FhirResourceExpressService {

    public  FhirRepositoryOperationOutcome receive(FhirResource fhirResource, String authorization);

}
