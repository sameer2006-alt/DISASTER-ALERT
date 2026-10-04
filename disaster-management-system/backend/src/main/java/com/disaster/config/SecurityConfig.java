package com.disaster.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth

        // ═════════════════════════════════════════════════════════════════════
        //  SECURITY ACCESS MATRIX (Order: most specific to least specific)
        // ═════════════════════════════════════════════════════════════════════

        // ── 0. Infrastructure & Preflight ─────────────────────────────────────
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers("/ws/**", "/ws/info/**").permitAll()

        // ── 1. Public GET ─────────────────────────────────────────────────────
        // Health
        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
        // Organisation public cards
        .requestMatchers(HttpMethod.GET, "/api/public/organisations").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/org/public/**").permitAll()
        // Public stats, alerts, events, shelters
        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/events/active").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/events/{id}").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/events").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/shelters/public/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/shelters").permitAll()
        // SACHET feed
        .requestMatchers(HttpMethod.GET, "/api/sachet/**").permitAll()
        // Active simulation feed
        .requestMatchers(HttpMethod.GET, "/api/simulate/active").permitAll()

        // ── 2. Public POST ────────────────────────────────────────────────────
        // Citizen auth endpoints
        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
        // Organisation auth endpoints
        .requestMatchers(HttpMethod.POST, "/api/org/auth/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/org/register").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/org/login").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/org/verify-otp").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/org/resend-otp").permitAll()
        // SOS Emergency Rescue Request
        .requestMatchers(HttpMethod.POST, "/api/rescue/request").permitAll()

        // ── 3. ADMIN only ─────────────────────────────────────────────────────
        // Disaster simulations & lifecycle resolution
        .requestMatchers(HttpMethod.POST, "/api/simulate/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.PATCH, "/api/simulate/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/api/events/simulate").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/api/events/ingest").hasRole("ADMIN")
        // Verification pipeline simulation & reset
        .requestMatchers(HttpMethod.POST, "/api/verification/simulate/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/api/verification/reset").hasRole("ADMIN")
        // External integrations (NASA FIRMS, OpenWeather, Twilio, Email test/status)
        .requestMatchers("/api/integrations/**").hasRole("ADMIN")

        // ── 4. ORGANISATION or ADMIN ──────────────────────────────────────────
        // Rescue triage and status updates
        .requestMatchers(HttpMethod.GET, "/api/rescue").hasAnyRole("ORGANISATION", "ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/rescue/pending").hasAnyRole("ORGANISATION", "ADMIN")
        .requestMatchers(HttpMethod.PATCH, "/api/rescue/*/status").hasAnyRole("ORGANISATION", "ADMIN")
        .requestMatchers(HttpMethod.PATCH, "/api/rescue/{id}/status").hasAnyRole("ORGANISATION", "ADMIN")
        .requestMatchers(HttpMethod.GET, "/api/rescue/**").hasAnyRole("ORGANISATION", "ADMIN")
        // Shelter management (creation & capacity/occupancy updates)
        .requestMatchers(HttpMethod.POST, "/api/shelters").hasAnyRole("ORGANISATION", "ADMIN")
        .requestMatchers(HttpMethod.PATCH, "/api/shelters/**").hasAnyRole("ORGANISATION", "ADMIN")
        // Volunteer roster & status updates
        .requestMatchers("/api/volunteers/**").hasAnyRole("ORGANISATION", "ADMIN")
        // Organisation profile management
        .requestMatchers("/api/org/profile").hasAnyRole("ORGANISATION", "ADMIN")

        // ── 5. Authenticated (CITIZEN, ORGANISATION, ADMIN) ───────────────────
        .requestMatchers("/api/climate/**").authenticated()
        .requestMatchers("/api/ai/**").authenticated()
        .requestMatchers("/api/verification/**").authenticated()

        // ── 6. Default Deny / Authenticated ───────────────────────────────────
        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}