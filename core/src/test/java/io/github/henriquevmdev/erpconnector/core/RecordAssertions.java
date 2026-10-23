package io.github.henriquevmdev.erpconnector.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class RecordAssertions {

    /**
     * Rebuilds {@code valid} once per component, with only that component set to null, and expects the canonical
     * constructor to reject it. A component added later is covered without writing a new test.
     */
    public static void assertEachComponentRejectsNull(Record valid) throws Exception {
        RecordComponent[] components = valid.getClass().getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Constructor<?> constructor = valid.getClass().getDeclaredConstructor(types);
        for (int i = 0; i < components.length; i++) {
            Object[] args = new Object[components.length];
            for (int j = 0; j < components.length; j++) {
                args[j] = i == j ? null : components[j].getAccessor().invoke(valid);
            }
            assertThatThrownBy(() -> constructor.newInstance(args))
                    .as("%s.%s = null", valid.getClass().getSimpleName(), components[i].getName())
                    .hasRootCauseInstanceOf(NullPointerException.class);
        }
    }

    private RecordAssertions() {
    }
}
