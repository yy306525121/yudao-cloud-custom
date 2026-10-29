package cn.iocoder.yudao.framework.tracer.core.filter;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class TraceFilterTest {

    private final TraceFilter traceFilter = new TraceFilter();

    @Test
    void doFilterInternal_shouldUseSkyWalkingTraceId() throws Exception {
        String traceId = "trace-123";
        try (MockedStatic<TracerUtils> tracerUtils = mockStatic(TracerUtils.class)) {
            tracerUtils.when(TracerUtils::getTraceId).thenReturn(traceId);

            MockHttpServletResponse response = doFilterAndAssertMdc(traceId);

            assertEquals(traceId, response.getHeader(TraceFilter.HEADER_NAME_TRACE_ID));
            assertNull(MDC.get(TraceFilter.MDC_KEY_TRACE_ID));
        }
    }

    @Test
    void doFilterInternal_shouldCreateUuidWhenTraceIdEmpty() throws Exception {
        try (MockedStatic<TracerUtils> tracerUtils = mockStatic(TracerUtils.class)) {
            tracerUtils.when(TracerUtils::getTraceId).thenReturn("");

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            traceFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
                String traceId = MDC.get(TraceFilter.MDC_KEY_TRACE_ID);
                assertTrue(isUuid(traceId));
                assertEquals(traceId, ((MockHttpServletResponse) servletResponse).getHeader(TraceFilter.HEADER_NAME_TRACE_ID));
            });

            assertTrue(isUuid(response.getHeader(TraceFilter.HEADER_NAME_TRACE_ID)));
            assertNull(MDC.get(TraceFilter.MDC_KEY_TRACE_ID));
        }
    }

    @Test
    void doFilterInternal_shouldCreateUuidWhenSkyWalkingTraceIdNotAvailable() throws Exception {
        try (MockedStatic<TracerUtils> tracerUtils = mockStatic(TracerUtils.class)) {
            tracerUtils.when(TracerUtils::getTraceId).thenReturn("N/A");

            MockHttpServletResponse response = doFilterAndAssertUuidMdc();

            assertTrue(isUuid(response.getHeader(TraceFilter.HEADER_NAME_TRACE_ID)));
            assertNull(MDC.get(TraceFilter.MDC_KEY_TRACE_ID));
        }
    }

    @Test
    void restoresCallerTraceEvenWhenFilterChainThrows() {
        MDC.put(TraceFilter.MDC_KEY_TRACE_ID, "caller");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                    () -> traceFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                            (request, response) -> { throw new jakarta.servlet.ServletException("failed"); }));
            assertEquals("caller", MDC.get(TraceFilter.MDC_KEY_TRACE_ID));
        } finally {
            MDC.clear();
        }
    }

    private MockHttpServletResponse doFilterAndAssertMdc(String expectedTraceId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        traceFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertEquals(expectedTraceId, MDC.get(TraceFilter.MDC_KEY_TRACE_ID)));
        return response;
    }

    private MockHttpServletResponse doFilterAndAssertUuidMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        traceFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertTrue(isUuid(MDC.get(TraceFilter.MDC_KEY_TRACE_ID))));
        return response;
    }

    private static boolean isUuid(String value) {
        return assertDoesNotThrow(() -> UUID.fromString(value)) != null;
    }

}
