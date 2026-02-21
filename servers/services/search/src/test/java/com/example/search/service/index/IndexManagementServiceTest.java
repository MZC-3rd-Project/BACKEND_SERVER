package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.search.dto.index.response.IndexRecreateResponse;
import com.example.search.exception.SearchErrorCode;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexManagementServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private SearchIndexTemplateResolver templateResolver;

    private IndexManagementService indexManagementService;

    @BeforeEach
    void setUp() {
        indexManagementService = new IndexManagementService(restClient, templateResolver);
        when(templateResolver.resolveItemsIndexTemplate()).thenReturn("{\"settings\":{},\"mappings\":{}}");
    }

    @Test
    void recreateItemsIndex_returnsSuccessWhenConcurrentRequestAlreadySwitchedAlias() throws Exception {
        List<Request> requests = new ArrayList<>();
        AtomicInteger writeAliasCalls = new AtomicInteger();

        when(restClient.performRequest(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            requests.add(request);
            if ("GET".equals(request.getMethod()) && "/_alias/items-write".equals(request.getEndpoint())) {
                if (writeAliasCalls.getAndIncrement() == 0) {
                    return aliasResponse("items-v1", "items-write", true);
                }
                return aliasResponse("items-v2", "items-write", true);
            }
            if ("PUT".equals(request.getMethod()) && "/items-v2".equals(request.getEndpoint())) {
                throw indexAlreadyExistsException();
            }
            throw new IllegalStateException("Unexpected request: " + request.getMethod() + " " + request.getEndpoint());
        });

        IndexRecreateResponse response = indexManagementService.recreateItemsIndex("items");

        assertThat(response.getIndexName()).isEqualTo("items-v2");
        assertThat(response.isRecreated()).isTrue();
        assertThat(requests).noneMatch(request ->
                "DELETE".equals(request.getMethod()) && "/items-v2".equals(request.getEndpoint()));
    }

    @Test
    void recreateItemsIndex_deletesCreatedTargetWhenAliasSwitchFails() throws Exception {
        List<Request> requests = new ArrayList<>();

        when(restClient.performRequest(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            requests.add(request);
            if ("GET".equals(request.getMethod()) && "/_alias/items-write".equals(request.getEndpoint())) {
                return aliasResponse("items-v1", "items-write", true);
            }
            if ("PUT".equals(request.getMethod()) && "/items-v2".equals(request.getEndpoint())) {
                return emptyResponse();
            }
            if ("POST".equals(request.getMethod()) && "/_aliases".equals(request.getEndpoint())) {
                throw new IOException("alias switch failed");
            }
            if ("GET".equals(request.getMethod()) && "/_alias/items-read".equals(request.getEndpoint())) {
                return aliasResponse("items-v1", "items-read", false);
            }
            if ("DELETE".equals(request.getMethod()) && "/items-v2".equals(request.getEndpoint())) {
                return emptyResponse();
            }
            throw new IllegalStateException("Unexpected request: " + request.getMethod() + " " + request.getEndpoint());
        });

        assertThatThrownBy(() -> indexManagementService.recreateItemsIndex("items"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(SearchErrorCode.INDEX_MANAGEMENT_FAILED);
                });

        assertThat(requests).anyMatch(request ->
                "DELETE".equals(request.getMethod()) && "/items-v2".equals(request.getEndpoint()));
    }

    private Response emptyResponse() {
        return mock(Response.class);
    }

    private Response aliasResponse(String indexName, String alias, boolean writeAlias) {
        String aliasMetadata = writeAlias ? "{\"is_write_index\":true}" : "{}";
        String json = """
                {
                  "%s": {
                    "aliases": {
                      "%s": %s
                    }
                  }
                }
                """.formatted(indexName, alias, aliasMetadata);

        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity(json, ContentType.APPLICATION_JSON));
        return response;
    }

    private ResponseException indexAlreadyExistsException() {
        Response response = mock(Response.class);
        StatusLine statusLine = mock(StatusLine.class);
        lenient().when(statusLine.getStatusCode()).thenReturn(400);
        lenient().when(response.getStatusLine()).thenReturn(statusLine);
        lenient().when(response.getEntity()).thenReturn(new StringEntity(
                "{\"error\":{\"type\":\"resource_already_exists_exception\"}}",
                ContentType.APPLICATION_JSON
        ));
        try {
            return new ResponseException(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
