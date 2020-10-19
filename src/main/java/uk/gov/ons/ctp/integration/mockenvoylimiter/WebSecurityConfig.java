package uk.gov.ons.ctp.integration.mockenvoylimiter;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

  // SECURITY-HACK  @Value("${spring.security.user.name}")
  //  String username;
  //
  //  @Value("${spring.security.user.password}")
  //  String password;

  protected void configure(HttpSecurity http) throws Exception {
    // Post requests to the service only work with csrf disabled!
    http.csrf().disable();
  }
}
