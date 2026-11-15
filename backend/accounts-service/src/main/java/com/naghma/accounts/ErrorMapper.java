package com.naghma.accounts;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Uniform JSON errors: {status, title, detail}. */
public class ErrorMapper {

    public record Problem(int status, String title, String detail) {}

    @ServerExceptionMapper
    public Response web(WebApplicationException e) {
        int status = e.getResponse().getStatus();
        return json(status, e.getMessage());
    }

    @ServerExceptionMapper
    public Response validation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return json(400, detail);
    }

    private static String lastNode(String path) {
        int i = path.lastIndexOf('.');
        return i < 0 ? path : path.substring(i + 1);
    }

    private static Response json(int status, String detail) {
        Response.Status s = Response.Status.fromStatusCode(status);
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Problem(status, s == null ? "Error" : s.getReasonPhrase(), detail))
                .build();
    }
}
