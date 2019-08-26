package org.dhis2.fhir.adapter.fhir.express;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

/**
 *
 * @author Charles Chigoriwa
 */
public class ExpressHttpUtility {

    public static String httpPost(String url, String body, String authorization, Map<String, String> headers) throws UnsupportedEncodingException, IOException {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(url);
        if (!ExpressUtility.isEmpty(authorization)) {
            httppost.addHeader("Authorization", authorization);
        }

        if (!ExpressUtility.isEmpty(headers)) {
            headers.keySet().forEach((key) -> {
                httppost.addHeader(key, headers.get(key));
            });
        }

        if (!ExpressUtility.isEmpty(body)) {
            httppost.setEntity(new StringEntity(body, "UTF-8"));
        }

        HttpResponse response = httpClient.execute(httppost);
        return parseStringContent(response);
    }

    public static String httpPost(String url, List<NameValuePair> params, String authorization, Map<String, String> headers) throws UnsupportedEncodingException, IOException {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(url);
        if (!ExpressUtility.isEmpty(authorization)) {
            httppost.addHeader("Authorization", authorization);
        }

        if (!ExpressUtility.isEmpty(headers)) {
            headers.keySet().forEach((key) -> {
                httppost.addHeader(key, headers.get(key));
            });
        }
        if (!ExpressUtility.isEmpty(params)) {
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
        }
        HttpResponse response = httpClient.execute(httppost);
        return parseStringContent(response);
    }

    public static String httpGet(String url, String authorization, Map<String, String> headers) throws UnsupportedEncodingException, IOException {
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        if (!ExpressUtility.isEmpty(authorization)) {
            httpGet.addHeader("Authorization", authorization);
        }
        
        if (!ExpressUtility.isEmpty(headers)) {
            headers.keySet().forEach((key) -> {
                httpGet.addHeader(key, headers.get(key));
            });
        }
        HttpResponse response = httpClient.execute(httpGet);
        return parseStringContent(response);
    }

    public static String httpPost(String url, List<NameValuePair> params, String authorization) throws UnsupportedEncodingException, IOException {
        return httpPost(url, params, authorization, null);
    }

    public static String httpPost(String url, String body, String authorization) throws UnsupportedEncodingException, IOException {
        return httpPost(url, body, authorization, null);
    }

    public static String httpGet(String url, String authorization) throws UnsupportedEncodingException, IOException {
        return httpGet(url, authorization, null);
    }

    private static String parseStringContent(HttpResponse response) throws IOException {
        String content = "";
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try (InputStream instream = entity.getContent()) {
                content = IOUtils.toString(instream, "UTF-8");
            }
        }
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 401 || statusCode == 403) {
            throw new UnauthorizedApiException(String.valueOf(statusCode), content);
        } else if (statusCode >= 300) {
            throw new ApiException(String.valueOf(statusCode), content);
        }
        return content;
    }

}
