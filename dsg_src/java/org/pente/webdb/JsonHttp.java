package org.pente.webdb;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Small JSON plumbing helper for the {@code /api/db/*} endpoints.
 *
 * Success bodies are the DTO serialized as-is. Error bodies use a fixed
 * envelope: <code>{"error":{"code":..., "message":...}}</code> (message is
 * omitted when null). All responses are {@code application/json;charset=UTF-8}.
 */
public final class JsonHttp {

    private static Category cat = Category.getInstance(JsonHttp.class.getName());

    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    /** Maximum request body accepted by {@link #readBody}. */
    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB

    // serializeNulls so DTO null fields (e.g. ping's "auth":null) render
    // explicitly. Error messages are still omitted via conditional puts below.
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private JsonHttp() {
    }

    /**
     * Serialize {@code dto} as JSON with HTTP 200.
     */
    public static void ok(HttpServletResponse response, Object dto)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        writeJson(response, GSON.toJson(dto));
    }

    /**
     * Emit the standard error envelope
     * <code>{"error":{"code":code, "message":message}}</code> with the given
     * HTTP status. A null {@code message} is omitted from the envelope.
     */
    public static void error(HttpServletResponse response, int status,
                             String code, String message) throws IOException {

        response.setStatus(status);

        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", code);
        if (message != null) {
            err.put("message", message);
        }

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", err);

        writeJson(response, GSON.toJson(body));
    }

    /**
     * Read and Gson-deserialize the request body into {@code type}, enforcing a
     * 1 MB cap. On a body that is too large, empty, or malformed JSON, this
     * sends the appropriate 4xx error envelope and returns {@code null}, so
     * callers can simply do {@code if (dto == null) return;}.
     */
    public static <T> T readBody(HttpServletRequest request,
                                 HttpServletResponse response, Class<T> type)
            throws IOException {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        InputStream in = request.getInputStream();
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > MAX_BODY_BYTES) {
                error(response, 413, "payload_too_large",
                        "Request body exceeds 1 MB limit");
                return null;
            }
            buf.write(chunk, 0, n);
        }

        String json = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        try {
            T dto = GSON.fromJson(json, type);
            if (dto == null) {
                error(response, HttpServletResponse.SC_BAD_REQUEST, "bad_json",
                        "Request body is empty or not valid JSON");
                return null;
            }
            return dto;
        } catch (JsonSyntaxException e) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "bad_json",
                    "Malformed JSON: " + e.getMessage());
            return null;
        }
    }

    private static void writeJson(HttpServletResponse response, String json)
            throws IOException {
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.write(json);
        out.flush();
    }
}
