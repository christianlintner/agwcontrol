package com.agwcontrol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiInfoFormatterTest {

    private final ApiInfoFormatter formatter = new ApiInfoFormatter();

    @Test
    void emptyListReturnsNoneFound() {
        String result = formatter.format("testserver", List.of());
        assertEquals("Keine APIs gefunden.", result);
    }

    @Test
    void singleApiContainsAllFields() {
        List<ApiInfo> apis = List.of(new ApiInfo("1", "MyAPI", "v1", "REST", true));
        String result = formatter.format("srv", apis);
        assertTrue(result.contains("MyAPI"));
        assertTrue(result.contains("v1"));
        assertTrue(result.contains("REST"));
        assertTrue(result.contains("AKTIV"));
    }

    @Test
    void inactiveApiShowsInaktiv() {
        List<ApiInfo> apis = List.of(new ApiInfo("1", "OldAPI", "1.0", "SOAP", false));
        String result = formatter.format("srv", apis);
        assertTrue(result.contains("INAKTIV"));
    }

    @Test
    void multipleApisAllAppearInOutput() {
        List<ApiInfo> apis = List.of(
                new ApiInfo("1", "AlphaAPI", "1.0", "REST", true),
                new ApiInfo("2", "BetaService", "2.3", "SOAP", false),
                new ApiInfo("3", "GammaWS", "v3", "REST", true));
        String result = formatter.format("srv", apis);
        assertTrue(result.contains("AlphaAPI"));
        assertTrue(result.contains("BetaService"));
        assertTrue(result.contains("GammaWS"));
        assertTrue(result.contains("3 APIs gefunden"));
    }

    @Test
    void singleApiShowsSingularLabel() {
        List<ApiInfo> apis = List.of(new ApiInfo("1", "OnlyOne", "1.0", "REST", true));
        String result = formatter.format("srv", apis);
        assertTrue(result.contains("1 API gefunden"));
    }

    @Test
    void serverLabelAppearsInHeader() {
        List<ApiInfo> apis = List.of(new ApiInfo("1", "SomeAPI", "1.0", "REST", true));
        String result = formatter.format("my.server.com", apis);
        assertTrue(result.contains("my.server.com"));
    }
}
