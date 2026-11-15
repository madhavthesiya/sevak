package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/**
 * The caller is set on the database connection when a transaction starts, so database work outside a
 * transaction runs as nobody. In production that hides every private row; in the tests, which log in as
 * the owner, it would skip the policies. Today every service method that reaches the main pool is
 * transactional; this keeps it that way.
 */
class TransactionBoundaryTest {

    /** Use the owner pool or run as fixed maintenance statements, outside any request's transaction. */
    private static final Set<String> EXEMPT = Set.of("AdminService.refreshViews", "DemoResetService.reset");

    @Test
    void everyPublicServiceMethodThatUsesARepositoryRunsInATransaction() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));

        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents("com.sevak.api")) {
            Class<?> service =
                    ClassUtils.forName(definition.getBeanClassName(), getClass().getClassLoader());
            boolean usesARepository = Arrays.stream(service.getDeclaredFields())
                    .anyMatch(field -> field.getType().getSimpleName().endsWith("Repository"));
            if (!usesARepository) {
                continue; // TokenService, for example, never touches the database
            }
            boolean classIsTransactional = service.isAnnotationPresent(Transactional.class);
            for (Method method : service.getDeclaredMethods()) {
                String name = service.getSimpleName() + "." + method.getName();
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || EXEMPT.contains(name)) {
                    continue;
                }
                checked++;
                if (!classIsTransactional && !method.isAnnotationPresent(Transactional.class)) {
                    missing.add(name);
                }
            }
        }
        assertThat(checked).as("service methods checked").isGreaterThan(30);
        assertThat(missing).as("public service methods outside a transaction").isEmpty();
    }
}
