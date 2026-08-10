package com.odoomaster.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Spring Modulith module model derived from {@link Application}:
 * every capability module keeps its {@code …internal} types private, only reaches
 * the named interfaces it declares in {@code @ApplicationModule(allowedDependencies = …)}, and the
 * whole dependency graph stays acyclic. Pure static classpath analysis — no Spring
 * context or datasource is booted, so it runs in a plain {@code mvn test}.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(Application.class);

    @Test
    void verifiesModuleBoundaries() {
        MODULES.verify();
    }

    /**
     * Pins each module's published API surface. {@code verify()} alone cannot catch a dropped
     * {@code @NamedInterface} annotation: the type would silently fall back into the module's unnamed
     * interface, and any consumer declaring only {@code "module::facet"} would start failing with a
     * confusing boundary violation elsewhere. Asserting the facets here fails at the cause instead.
     *
     * <p>Modules absent from this list ({@code analytics}, {@code audit}, {@code feedback},
     * {@code notification}) publish no cross-module API at all.
     */
    @Test
    void exposesTheDeclaredNamedInterfaces() {
        assertNamedInterfaces("shared", "errors", "security", "audit", "contracts");
        assertNamedInterfaces("iam", "directory");
        assertNamedInterfaces("catalog", "events", "inventory");
        assertNamedInterfaces("ticketing", "issuance", "reporting");
        assertNamedInterfaces("sales", "reporting");
    }

    private static void assertNamedInterfaces(String moduleName, String... expected) {
        ApplicationModule module = MODULES.getModuleByName(moduleName)
                .orElseThrow(() -> new AssertionError("No module named '" + moduleName + "'!"));

        Set<String> actual = module.getNamedInterfaces().stream()
                .filter(NamedInterface::isNamed)
                .map(NamedInterface::getName)
                .collect(Collectors.toSet());

        assertThat(actual)
                .as("named interfaces of module '%s'", moduleName)
                .containsExactlyInAnyOrder(expected);
    }
}
