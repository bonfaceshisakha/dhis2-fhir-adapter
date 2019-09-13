package org.dhis2.fhir.adapter.fhir.express;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.dhis2.fhir.adapter.fhir.client.*;
import org.dhis2.fhir.adapter.fhir.metadata.model.FhirClientResource;
import org.dhis2.fhir.adapter.fhir.metadata.model.FhirResourceType;
import org.dhis2.fhir.adapter.fhir.metadata.repository.FhirClientResourceRepository;
import org.dhis2.fhir.adapter.fhir.repository.FhirResourceRepository;
import org.dhis2.fhir.adapter.rest.RestResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.stream.Collectors;
import org.dhis2.fhir.adapter.auth.AuthorizationContext;
import org.dhis2.fhir.adapter.data.model.ProcessedItemInfo;
import org.dhis2.fhir.adapter.dhis.config.DhisEndpointConfig;
import org.dhis2.fhir.adapter.fhir.model.FhirVersion;
import org.dhis2.fhir.adapter.fhir.repository.FhirRepositoryOperationOutcome;
import org.dhis2.fhir.adapter.fhir.repository.FhirResource;
import org.dhis2.fhir.adapter.fhir.util.FhirParserException;
import org.dhis2.fhir.adapter.fhir.util.FhirParserUtils;
import org.dhis2.fhir.adapter.rest.RestUnauthorizedException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author Charles Chigoriwa (ITINordic)
 */
@RestController
@RequestMapping("/remote-fhir-express")
@ConditionalOnProperty(name = "dhis2.fhir-adapter.import-enabled")
public class FhirClientExpressController extends AbstractFhirClientController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final MediaType FHIR_JSON_MEDIA_TYPE = MediaType.parseMediaType("application/fhir+json;charset=UTF-8");

    private final FhirResourceRepository fhirResourceRepository;

    private final Map<FhirVersion, FhirContext> fhirContexts;

    private final FhirResourceExpressService fhirResourceExpressService;

    private final DhisEndpointConfig endpointConfig;

    public FhirClientExpressController(@Nonnull FhirClientResourceRepository resourceRepository, @Nonnull FhirClientRestHookProcessor processor,
            @Nonnull FhirResourceRepository fhirResourceRepository, @Nonnull Set<FhirContext> fhirContexts, FhirResourceExpressService fhirResourceExpressService, @Nonnull AuthorizationContext authorizationContext, @Nonnull DhisEndpointConfig endpointConfig) {
        super(resourceRepository, processor);
        this.fhirResourceRepository = fhirResourceRepository;
        this.fhirContexts = fhirContexts.stream().filter(fc -> (FhirVersion.get(fc.getVersion().getVersion()) != null))
                .collect(Collectors.toMap(fc -> FhirVersion.get(fc.getVersion().getVersion()), fc -> fc));
        this.fhirResourceExpressService = fhirResourceExpressService;
        this.endpointConfig = endpointConfig;
    }

    @RequestMapping(path = "/{fhirClientId}/**/{resourceType}/{resourceId}", method = RequestMethod.DELETE)
    public ResponseEntity<byte[]> delete(
            @PathVariable("fhirClientId") UUID fhirClientId, @PathVariable("resourceType") String resourceType, @PathVariable("resourceId") String resourceId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        final FhirResourceType fhirResourceType = FhirResourceType.getByResourceTypeName(resourceType);
        if (fhirResourceType == null) {
            return createBadRequestResponse("Unknown resource type: " + resourceType);
        }

        lookupFhirClientResource(fhirClientId, fhirResourceType, authorization);
        // not yet supported
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(path = "/{fhirClientId}/**/{resourceType}/{resourceId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<byte[]> receiveWithPayload(
            @PathVariable("fhirClientId") UUID fhirClientId, @PathVariable("resourceType") String resourceType, @PathVariable("resourceId") String resourceId,
            @RequestHeader(value = "Authorization", required = false) String authorization, @Nonnull HttpEntity<byte[]> requestEntity) {
        final FhirResourceType fhirResourceType = FhirResourceType.getByResourceTypeName(resourceType);
        if (fhirResourceType == null) {
            return createBadRequestResponse("Unknown resource type: " + resourceType);
        }

        final FhirClientResource fhirClientResource = lookupFhirClientResource(fhirClientId, fhirResourceType, authorization);
        return processPayload(fhirClientResource, resourceType, resourceId, requestEntity, authorization);
    }

    //This method can be invoked to check whether both the adapter and dhis are running
    @RequestMapping(path = "/authenticated", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getAuthenticated(@RequestHeader(value = "Authorization", required = true) String authorization) {
        try {
            if (checkAuthorization(authorization)) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                logger.warn("Authorization: " + authorization + " has failed.");
                return createUnauthorizedRequestResponse("Authorization: " + authorization + " has failed.");
            }
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
    
    @RequestMapping(path = "/getAuthorization", method = RequestMethod.GET)
    public String getAuthorization(@RequestHeader(value = "Authorization", required = true) String authorization) {
        logger.info("Authorization: " + authorization);
        return authorization;
    }

    //This method can be invoked to check whether the adapter is running
    @RequestMapping(path = "/runningAdapter", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getAdapterRunning() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Nonnull
    protected FhirClientResource lookupFhirClientResource(@Nonnull UUID fhirClientId, @Nonnull FhirResourceType fhirResourceType, @Nullable String authorization) {
        final FhirClientResource fhirClientResource = getResourceRepository().findFirstCached(fhirClientId, fhirResourceType)
                .orElseThrow(() -> new RestResourceNotFoundException("FHIR client data for resource " + fhirResourceType + " of FHIR client " + fhirClientId + " cannot be found."));
        validateRequest(fhirClientId, fhirClientResource, authorization);
        return fhirClientResource;
    }

    @Nonnull
    protected ResponseEntity<byte[]> processPayload(@Nonnull FhirClientResource fhirClientResource,
            @Nonnull String resourceType, @Nonnull String resourceId, HttpEntity<byte[]> requestEntity, @Nonnull String authorization) {
        if ((requestEntity.getBody() == null) || (requestEntity.getBody().length == 0)) {
            return createBadRequestResponse("Payload expected.");
        }

        final MediaType mediaType = requestEntity.getHeaders().getContentType();
        final String fhirResource = new String(requestEntity.getBody(), getCharset(mediaType));
        FhirRepositoryOperationOutcome outcome = processPayload(fhirClientResource, (mediaType == null) ? null : mediaType.toString(), resourceType, resourceId, fhirResource, authorization);
        if (outcome != null) {
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return createBadRequestResponse("No operation outcome from FhirRepository");
        }
    }

    public FhirRepositoryOperationOutcome processPayload(@Nonnull FhirClientResource fhirClientResource, @Nullable String contentType, @Nonnull String fhirResourceType, @Nonnull String fhirResourceId, @Nonnull String fhirResource, @Nonnull String authorization) {
        final FhirVersion fhirVersion = fhirClientResource.getFhirClient().getFhirVersion();
        final FhirContext fhirContext = fhirContexts.get(fhirVersion);
        if (fhirContext == null) {
            throw new IllegalStateException("No FHIR Context for FHIR version " + fhirVersion + " has been configured.");
        }

        final IBaseResource parsedFhirResource = FhirParserUtils.parse(fhirContext, fhirResource, contentType);
        final FhirResourceType parsedFhirResourceType = FhirResourceType.getByResource(parsedFhirResource);
        if (!fhirClientResource.getFhirResourceType().equals(parsedFhirResourceType)) {
            throw new FhirParserException("Received FHIR resource " + parsedFhirResourceType + " does not match FHIR resource type " + fhirClientResource.getFhirResourceType() + " of FHIR client resource.");
        }
        if (!fhirResourceType.equals(parsedFhirResourceType.getResourceTypeName())) {
            throw new FhirParserException("Received FHIR resource type " + parsedFhirResourceType + " does not match FHIR resource ID " + fhirResourceType + " of FHIR subscription notification.");
        }
        if (!fhirResourceId.equals(parsedFhirResource.getIdElement().getIdPart())) {
            throw new FhirParserException("Received FHIR resource type " + parsedFhirResource.getIdElement().getIdPart() + " does not match FHIR resource ID " + fhirResourceId + " of FHIR subscription notification.");
        }

        final ProcessedItemInfo processedItemInfo = ProcessedFhirItemInfoUtils.create(parsedFhirResource);
        FhirResource _fhirResource = new FhirResource(fhirClientResource.getGroupId(), processedItemInfo, false);
        return fhirResourceExpressService.receive(_fhirResource, authorization);
    }

    @Override
    protected void validateRequest(@Nonnull UUID fhirClientId, FhirClientResource fhirClientResource, String authorization) {

        if (!fhirClientResource.getFhirClient().getId().equals(fhirClientId)) {
            // do not give detail if the resource or the subscription cannot be found
            throw new RestResourceNotFoundException("FHIR client data for resource cannot be found: " + fhirClientResource);
        }
        if (fhirClientResource.isExpOnly()) {
            throw new RestResourceNotFoundException("FHIR client resource is intended for export only: " + fhirClientResource);
        }

        //Authenticate against Dhis2 Server
        if (!checkAuthorization(authorization)) {
            logger.warn("Authorization: " + authorization + " has failed.");
            throw new RestUnauthorizedException("Authentication has failed.");
        }
    }

    //TODO: Use template and put this code in dhis2-fhir-adapter-dhis module
    private boolean checkAuthorization(String authorization) {
        String url = getRootUri(endpointConfig, false) + "/me";
        try {
            ExpressHttpUtility.httpGet(url, authorization);
            return true;
        } catch (UnauthorizedApiException ex) {
            return false;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    @Nonnull
    private static String getRootUri(@Nonnull DhisEndpointConfig endpointConfig, boolean withoutVersion) {
        if (withoutVersion) {
            return endpointConfig.getUrl() + "/api";
        }
        return endpointConfig.getUrl() + "/api/" + endpointConfig.getApiVersion();
    }
    
     protected ResponseEntity<byte[]> createUnauthorizedRequestResponse( @Nonnull String message )
    {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType( MediaType.TEXT_PLAIN );
        return new ResponseEntity<>( message.getBytes( StandardCharsets.UTF_8 ), headers, HttpStatus.UNAUTHORIZED );
    }

}
