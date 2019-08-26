
package org.dhis2.fhir.adapter.fhir.express;

import java.util.Base64;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author Charles Chigoriwa
 */
public class ExpressUtility {
    
    public static String getBasicAuthorization(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
    
    public static String getBearerAuthorization(String token) {
        return "Bearer " + token;
    }

    public static String toBase64String(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
    
    public static boolean isEmpty(String string){
        return string==null || string.trim().isEmpty();
    }
    
    public static boolean isEmpty(Collection<?> collection){
        return collection==null || collection.isEmpty();
    }
    
    public static boolean isEmpty(Map<?,?> map){
        return map==null || map.isEmpty();
    }
}
