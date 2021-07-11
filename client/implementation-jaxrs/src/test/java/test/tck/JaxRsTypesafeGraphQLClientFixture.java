package test.tck;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URI;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.smallrye.graphql.client.typesafe.api.TypesafeGraphQLClientBuilder;
import tck.graphql.typesafe.TypesafeGraphQLClientFixture;

public class JaxRsTypesafeGraphQLClientFixture implements TypesafeGraphQLClientFixture {
    private final Client mockClient = Mockito.mock(Client.class);
    private final WebTarget mockWebTarget = Mockito.mock(WebTarget.class);
    private final Invocation.Builder mockInvocationBuilder = Mockito.mock(Invocation.Builder.class);
    private Response response;
    private Entity<JsonObject> entitySent;

    public JaxRsTypesafeGraphQLClientFixture() {
        given(mockClient.target(any(URI.class))).willReturn(mockWebTarget);
        given(mockWebTarget.request(any(MediaType.class))).willReturn(mockInvocationBuilder);
        given(mockInvocationBuilder.headers(any())).willReturn(mockInvocationBuilder);
        given(mockInvocationBuilder.post(any())).will(i -> response);
    }

    @Override
    public <T> T build(Class<T> apiClass) {
        return builder().build(apiClass);
    }

    @Override
    public TypesafeGraphQLClientBuilder builder() {
        return builderWithoutEndpointConfig().endpoint("urn:dummy-endpoint");
    }

    @Override
    public TypesafeGraphQLClientBuilder builderWithoutEndpointConfig() {
        TypesafeGraphQLClientBuilder builder = TypesafeGraphQLClientBuilder.newBuilder();
        try {
            Method method = builder.getClass().getMethod("client", Client.class);
            method.invoke(builder, mockClient);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("can't set client on builder", e);
        }
        return builder;
    }

    @Override
    public void returnsData(String data) {
        returns("{\"data\":{" + data.replace('\'', '\"') + "}}");
    }

    @Override
    public void returns(String response) {
        this.response = Response.ok(response).build();
    }

    @Override
    public void returnsServerError() {
        this.response = Response.serverError().type(TEXT_PLAIN_TYPE).entity("failed").build();
    }

    @Override
    public String variables() {
        return rawVariables().replace('\"', '\'');
    }

    @Override
    public String rawVariables() {
        JsonObject variables = entitySent().getEntity().getJsonObject("variables");
        return String.valueOf(variables);
    }

    @Override
    public String operationName() {
        return entitySent().getEntity().getString("operationName", "null");
    }

    @Override
    public String query() {
        return entitySent().getEntity().getString("query").replace('\"', '\'');
    }

    @Override
    public boolean sent() {
        return entitySent().getEntity() != null;
    }

    private Entity<JsonObject> entitySent() {
        if (entitySent == null) {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Entity<String>> captor = ArgumentCaptor.forClass(Entity.class);
            then(mockInvocationBuilder).should(atMost(1)).post(captor.capture());
            if (captor.getAllValues().isEmpty()) {
                entitySent = Entity.json(null);
            } else {
                Entity<String> stringEntity = captor.getValue();
                JsonObject jsonObject = Json.createReader(new StringReader(stringEntity.getEntity())).readObject();
                entitySent = Entity.entity(jsonObject, stringEntity.getMediaType());
            }
        }
        return entitySent;
    }

    @Override
    public Object sentHeader(String name) {
        return sentHeaders().getFirst(name);
    }

    @Override
    public URI endpointUsed() {
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        then(mockClient).should().target(captor.capture());
        return captor.getValue();
    }

    private MultivaluedMap<String, Object> sentHeaders() {
        MultivaluedMap<String, Object> map = captureExplicitHeaders();
        map.putSingle("Accept", captureAcceptHeader());
        map.putSingle("Content-Type", entitySent().getMediaType());
        return map;
    }

    private MultivaluedMap<String, Object> captureExplicitHeaders() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultivaluedMap<String, Object>> captor = ArgumentCaptor.forClass(MultivaluedMap.class);
        then(mockInvocationBuilder).should().headers(captor.capture());
        MultivaluedMap<String, Object> map = captor.getValue();
        return (map == null) ? new MultivaluedHashMap<>() : map;
    }

    private MediaType captureAcceptHeader() {
        ArgumentCaptor<MediaType> captor = ArgumentCaptor.forClass(MediaType.class);
        then(mockWebTarget).should().request(captor.capture());
        return captor.getValue();
    }

    @Override
    public void verifyClosed() {
        verify(mockClient).close();
    }
}
