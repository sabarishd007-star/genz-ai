package com.genzai.config;

import com.genzai.security.UserPrincipal;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && (parameter.getParameterType().equals(Long.class) || parameter.getParameterType().equals(long.class));
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                String headerUserId = webRequest.getHeader("X-User-Id");
                if (headerUserId != null && !headerUserId.isBlank()) {
                    try {
                        return Long.parseLong(headerUserId);
                    } catch (NumberFormatException ignored) {
                    }
                }

                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() != null) {
                    Object principal = auth.getPrincipal();
                    if (principal instanceof Long l) {
                        return l;
                    }
                    if (principal instanceof UserPrincipal up && up.getId() != null) {
                        return Math.abs(up.getId().getMostSignificantBits() % 1000000000L);
                    }
                }
                return 1L;
            }
        });
    }
}
