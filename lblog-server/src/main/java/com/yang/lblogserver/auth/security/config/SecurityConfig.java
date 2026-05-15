package com.yang.lblogserver.auth.security.config;

import com.yang.lblogserver.auth.security.filter.TokenAuthenticationFilter;
import com.yang.lblogserver.auth.security.handler.CustomAccessDeniedHandler;
import com.yang.lblogserver.auth.security.handler.CustomAuthEntryPoint;
import com.yang.lblogserver.auth.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final CustomAuthEntryPoint customAuthEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          TokenAuthenticationFilter tokenAuthenticationFilter,
                          CustomAuthEntryPoint customAuthEntryPoint,
                          CustomAccessDeniedHandler customAccessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.customAuthEntryPoint = customAuthEntryPoint;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(customAuthEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // 公开接口：登录 & 注册 & 刷新令牌
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                // 公开接口：前台查询（HomeController — 注意 controller 映射是 /api/v1 而非 /api/v1/home）
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**", "/api/v1/categories/**", "/api/v1/tags/**", "/api/v1/series/**").permitAll()
                // 公开接口：浏览上报 & 点赞（无需登录）
                .requestMatchers(HttpMethod.POST, "/api/v1/posts/*/view", "/api/v1/posts/*/like").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*/like").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/*/like/status").permitAll()
                // 公开接口：站点配置
                .requestMatchers("/api/v1/config").permitAll()
                // 公开接口：AI 绘图（调试期间临时放开鉴权）
                .requestMatchers("/api/v1/draw/**").permitAll()
                // 公开接口：静态资源
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                // 创作中心 & 上传：由 Controller 上的 @PreAuthorize 控制角色
                .requestMatchers("/api/v1/author/**", "/api/v1/upload/**").authenticated()
                // 其余接口需登录
                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService)
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Profile("!prod")
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        String defaultEncode = "bcrypt";
        Map<String, PasswordEncoder> encoders = Map.of(
            defaultEncode, new BCryptPasswordEncoder(),
            "noop", NoOpPasswordEncoder.getInstance()
        );
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(defaultEncode, encoders);
        delegating.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance());
        return delegating;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_AUTHOR > ROLE_USER");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
