package org.dhis2.fhir.adapter;


import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

import javax.annotation.Nonnull;

/**
 * 
 * @author Charles Chigoriwa
 */
@Configuration
@Order( 3 )
public class RemoteFhirExpressWebSecurityConfig extends WebSecurityConfigurerAdapter
{
    @Override
    protected void configure( @Nonnull HttpSecurity http ) throws Exception
    {
        http.sessionManagement().sessionCreationPolicy( SessionCreationPolicy.STATELESS );
        http.csrf().disable();
        http.antMatcher( "/remote-fhir-express/**" )
            .authorizeRequests()
            .antMatchers( HttpMethod.PUT, "/remote-fhir-express/**" ).permitAll()
            .antMatchers( HttpMethod.POST, "/remote-fhir-express/**" ).permitAll()
            .antMatchers( HttpMethod.DELETE, "/remote-fhir-express/**" ).permitAll()
            .antMatchers( HttpMethod.GET, "/remote-fhir-express/**" ).permitAll();
    }
}
