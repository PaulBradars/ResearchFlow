package researchflow.collection;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import researchflow.domain.*;
import researchflow.persistence.ConnectionFactory;
import researchflow.service.*;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Respondent-only HTTP surface. Researcher operations are never exposed. */
public final class CollectionServer implements AutoCloseable {
    private final FormService forms;
    private final ResponseSubmissionService submissions;
    private final ConnectionFactory connections;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final String baseUrl;
    private static final int MAX_BODY = 262_144;
    private static final class Session {
        final UUID formId;
        final Instant started = Instant.now();
        boolean submitted;
        Session(UUID formId) { this.formId = formId; }
    }

    public CollectionServer(FormService forms, ResponseSubmissionService submissions, ConnectionFactory connections,
                            InetSocketAddress address, String publicUrl) throws IOException {
        this.forms = forms;
        this.submissions = submissions;
        this.connections = connections;
        if (publicUrl != null && !publicUrl.isBlank()) {
            var uri = URI.create(publicUrl.strip());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                throw new IllegalArgumentException("Collection public URL must be an http(s) origin, such as https://forms.example.org.");
        }
        server = HttpServer.create(address, 64);
        baseUrl = publicUrl == null || publicUrl.isBlank()
                ? "http://" + localAddress() + ":" + server.getAddress().getPort()
                : publicUrl.strip().replaceAll("/+$", "");
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    private static String localAddress() throws SocketException {
        for (var network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback()) continue;
            for (var address : Collections.list(network.getInetAddresses()))
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
        }
        return "127.0.0.1";
    }

    public int port() { return server.getAddress().getPort(); }
    public String link(UUID formId) {
        var form = forms.require(formId);
        forms.requireWritableStudy(form.studyId());
        if (form.status() != FormStatus.ACTIVE) throw new IllegalStateException("Activate the form before sharing it.");
        return baseUrl + "/forms/" + formId;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                var path = exchange.getRequestURI().getPath();
                if (!path.matches("/forms/[0-9a-fA-F-]{36}")) { send(exchange, 404, "Form not found"); return; }
                var id = UUID.fromString(path.substring(7));
                if (!Set.of("GET", "POST").contains(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET, POST");
                    send(exchange, 405, "Method not allowed"); return;
                }
                var form = connections.inOperation(() -> {
                    var value = forms.require(id);
                    forms.requireWritableStudy(value.studyId());
                    if (value.status() != FormStatus.ACTIVE) throw new IllegalStateException("This form is not accepting responses.");
                    return value;
                });
                if (exchange.getRequestMethod().equals("GET")) {
                    sessions.entrySet().removeIf(entry -> entry.getValue().started.isBefore(Instant.now().minusSeconds(86400)));
                    if (sessions.size() >= 10_000) { send(exchange, 503, "Please try again later."); return; }
                    var token = UUID.randomUUID().toString();
                    sessions.put(token, new Session(id));
                    send(exchange, 200, render(form, token, Map.of(), Map.of()));
                    return;
                }
                var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                    send(exchange, 415, "Unsupported submission format"); return;
                }
                var bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (bytes.length > MAX_BODY) { send(exchange, 413, "Response is too large."); return; }
                var values = decode(new String(bytes, StandardCharsets.UTF_8));
                var token = values.getOrDefault("token", List.of("")).getFirst();
                var session = sessions.get(token);
                if (session == null || !session.formId.equals(id) || session.started.isBefore(Instant.now().minusSeconds(86400))) {
                    send(exchange, 400, "This form session has expired. Open the original form link again."); return;
                }
                synchronized (session) {
                    if (!session.submitted) {
                        var answers = new LinkedHashMap<UUID, String>();
                        for (var question : form.questions()) {
                            var entries = values.getOrDefault(question.id().toString(), List.of(""));
                            answers.put(question.id(), question.type() == QuestionType.MULTIPLE_CHOICE
                                    ? String.join(ResponseSubmissionService.MULTI_VALUE_SEPARATOR, entries) : entries.getFirst());
                        }
                        try {
                            connections.inOperation(() -> submissions.submit(id, session.started, answers));
                            session.submitted = true;
                        } catch (ValidationException invalid) {
                            send(exchange, 422, render(form, token, values, invalid.errors())); return;
                        }
                    }
                    send(exchange, 200, "<h1>Thank you!</h1><p>Your response has been saved. You can close this page.</p>");
                }
            } catch (IllegalArgumentException invalid) {
                send(exchange, 404, "The form or request could not be found or is invalid.");
            } catch (IllegalStateException unavailable) {
                send(exchange, 409, "This form is not accepting responses right now.");
            } catch (Exception failure) {
                java.util.logging.Logger.getLogger(CollectionServer.class.getName()).warning("COLLECTION_REQUEST_FAILED [" + failure.getClass().getSimpleName() + "]");
                send(exchange, 503, "Could not save your response. Please try again.");
            }
        }
    }

    private static Map<String, List<String>> decode(String body) {
        var result = new LinkedHashMap<String, List<String>>();
        for (var pair : body.split("&")) {
            var parts = pair.split("=", 2);
            var key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            var value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static String render(Form form, String token, Map<String, List<String>> values, Map<String, String> errors) {
        var html = new StringBuilder("<h1>").append(escape(form.title())).append("</h1><p>")
                .append(escape(form.description())).append("</p><p>* Required</p>");
        if (!errors.isEmpty()) html.append("<p role='alert' class='error'>Please correct the answers below.</p>");
        html.append("<form method='post'><input type='hidden' name='token' value='").append(token).append("'>");
        for (var section : form.sections()) {
            html.append("<h2>").append(escape(section.title())).append("</h2>");
            for (var q : section.questions()) {
                var name = q.id().toString();
                var selected = values.getOrDefault(name, List.of(""));
                var required = q.required() ? " required" : "";
                html.append("<fieldset><legend>").append(escape(q.label())).append(q.required() ? " *" : "")
                        .append("</legend><p>").append(escape(q.helpText())).append("</p>");
                switch (q.type()) {
                    case MULTIPLE_CHOICE -> {
                        for (var option : q.options()) html.append("<label class='choice'><input type='checkbox' name='")
                                .append(name).append("' value='").append(escape(option.value())).append("'")
                                .append(selected.contains(option.value()) ? " checked" : "").append("> ")
                                .append(escape(option.label())).append("</label>");
                    }
                    case SINGLE_CHOICE, LIKERT, RATING, YES_NO -> {
                        html.append("<select aria-label='").append(escape(q.label())).append("' name='").append(name).append("'").append(required).append("><option value=''>Choose an option</option>");
                        var options = q.type() == QuestionType.YES_NO
                                ? List.of(QuestionOption.create("Yes"), QuestionOption.create("No")) : q.options();
                        for (var option : options) html.append("<option value='").append(escape(option.value())).append("'")
                                .append(selected.contains(option.value()) ? " selected" : "").append(">")
                                .append(escape(option.label())).append("</option>");
                        html.append("</select>");
                    }
                    default -> {
                        var type = q.type() == QuestionType.DATE ? "date" : q.type() == QuestionType.NUMBER ? "number" : "text";
                        html.append("<input aria-label='").append(escape(q.label())).append("' name='").append(name).append("' type='").append(type)
                                .append("' value='").append(escape(selected.getFirst())).append("'").append(required);
                        if (q.type() == QuestionType.NUMBER) {
                            html.append(" step='any'");
                            if (q.minimum() != null) html.append(" min='").append(q.minimum()).append("'");
                            if (q.maximum() != null) html.append(" max='").append(q.maximum()).append("'");
                        }
                        html.append(">");
                    }
                }
                if (errors.containsKey(name)) html.append("<p class='error'>").append(escape(errors.get(name))).append("</p>");
                html.append("</fieldset>");
            }
        }
        return html.append("<button type='submit'>Submit response</button></form>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        var html = "<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>ResearchFlow form</title><style>body{font:17px system-ui;background:#f3f5f9;color:#182535;margin:0;padding:24px}"
                + "main{max-width:720px;margin:auto;background:white;padding:28px;border-radius:16px}fieldset{border:1px solid #ccd5df;border-radius:8px;margin:22px 0;padding:18px}"
                + "legend{font-weight:600}p{white-space:pre-wrap}input:not([type=checkbox]),select{box-sizing:border-box;width:100%;padding:12px;font:inherit}"
                + ".choice{display:block;margin:12px 0}.error{color:#a51b29}button{background:#235bcc;color:white;border:0;padding:14px 24px;border-radius:7px;font:inherit}</style><main>"
                + body + "</main></html>";
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); sessions.clear(); }
}
