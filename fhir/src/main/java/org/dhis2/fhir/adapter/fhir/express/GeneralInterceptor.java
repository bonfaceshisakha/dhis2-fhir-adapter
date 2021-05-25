package org.dhis2.fhir.adapter.fhir.express;

import ca.uhn.fhir.rest.client.apache.ApacheHttpResponse;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Charles Chigoriwa
 */
public class GeneralInterceptor implements IClientInterceptor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void interceptRequest(IHttpRequest theRequest) {

    }

    @Override
    public void interceptResponse(IHttpResponse theResponse) throws IOException {
        logger.info("GeneralInterceptor.interceptResponse(IHttpResponse theResponse)");
        InputStream respEntity = null;
        try {
            if (theResponse instanceof ApacheHttpResponse) {

                respEntity = theResponse.readEntity();
                String mimeType = theResponse.getMimeType();
                ContentType contentType = null;
                if (mimeType != null) {
                    contentType = ContentType.getByMimeType(mimeType);
                }

                if (respEntity != null) {
                    final byte[] bytes;
                    try {
                        bytes = IOUtils.toByteArray(respEntity);
                    } catch (IllegalStateException e) {
                        throw new InternalErrorException(e);
                    }
                    String newBody = new String(bytes, "UTF-8");
                    if (newBody.contains("&nbsp;")) {
                        newBody = newBody.replaceAll("&nbsp;", "");
                    }

                    contentType = contentType == null ? ContentType.APPLICATION_JSON : contentType;

                    ApacheHttpResponse apacheHttpResponse = (ApacheHttpResponse) theResponse;
                    apacheHttpResponse.getResponse().setEntity(new StringEntity(newBody, contentType));
                }

            }
        } finally {
            IOUtils.closeQuietly(respEntity);
        }
    }

}
