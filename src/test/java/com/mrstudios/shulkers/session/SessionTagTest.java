package com.mrstudios.shulkers.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionTagTest {

    @Test
    void roundTripsUuid() {
        UUID id = SessionTag.generate();

        assertEquals(Optional.of(id), SessionTag.parse(SessionTag.encode(id)));
    }

    @Test
    void acceptsUpperCaseForm() {
        UUID id = SessionTag.generate();

        assertEquals(Optional.of(id), SessionTag.parse(SessionTag.encode(id).toUpperCase()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "not-a-uuid",
        "1-1-1-1-1",
        "123e4567-e89b-12d3-a456-42661417400",
        "123e4567-e89b-12d3-a456-4266141740000",
        "123e4567xe89bx12d3xa456x426614174000",
        "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
    })
    void malformedValuesAreStale(String value) {
        assertTrue(SessionTag.parse(value).isEmpty());
    }

    @Test
    void generatedIdsNeverMatch() {
        assertNotEquals(SessionTag.generate(), SessionTag.generate());
    }
}
