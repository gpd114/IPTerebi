import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A fake Xtream Codes panel, for running IPTerebi without a real line.
 *
 * It misbehaves on purpose, in the ways CLAUDE.md says real panels do: ids
 * arrive as numbers and as strings, two channels have no stream_id (so they
 * would collide as list keys), a category has a blank id and another is
 * repeated, guide titles are base64 with start/end strings in no timezone, one
 * channel refuses with 403 like a line at its connection limit, and the series
 * come back in each of the shapes get_series_info takes. See README.md beside
 * this file for the full list and what each one exercises.
 *
 * Plain JDK, no dependencies, one file:
 *
 *     java FakePanel.java [mediaDir] [port]
 *
 * mediaDir defaults to ./media (make it with make-media.sh) and port to 8080.
 * Sign in with username and password "demo".
 */
public class FakePanel {

    static Path media;

    public static void main(String[] args) throws IOException {
        media = Path.of(args.length > 0 ? args[0] : "media");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", FakePanel::handle);
        server.start();
        log("fake panel on port " + port + ", media from " + media.toAbsolutePath());
        if (!Files.isDirectory(media)) {
            log("no media directory yet — the API works, but nothing will play until you run make-media.sh");
        }
        log("from an emulator the address is 10.0.2.2:" + port + "; sign in as demo / demo");
    }

    static void log(String line) {
        System.out.println(Instant.now().toString().substring(11, 19) + " " + line);
        System.out.flush();
    }

    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String ua = ex.getRequestHeaders().getFirst("User-Agent");
        try {
            if (path.equals("/player_api.php")) {
                String action = q.getOrDefault("action", "");
                log("API " + (action.isEmpty() ? "(sign-in)" : action) + " "
                    + q.getOrDefault("category_id", q.getOrDefault("stream_id", q.getOrDefault("series_id", "")))
                    + "  ua=" + ua);
                if (!"demo".equals(q.get("username")) || !"demo".equals(q.get("password"))) {
                    // As real panels do it: a rejected login is a 200 with auth 0, never a 401.
                    json(ex, "{\"user_info\":{\"auth\":0,\"message\":\"Invalid credentials (fake panel)\"}}");
                    return;
                }
                json(ex, api(action, q, base(ex)));
            } else if (path.startsWith("/live/")) {
                String file = fileName(path);
                log("LIVE " + file + "  ua=" + ua);
                if (file.startsWith("103.")) {
                    // A line at its connection limit.
                    status(ex, 403);
                } else if (file.startsWith("104.") || file.startsWith("105.")) {
                    streamDropping(ex, file.substring(0, 3));
                } else if (file.endsWith(".ts")) {
                    streamLive(ex);
                } else {
                    // No HLS here: the Stream format setting's HLS option has
                    // nothing to be served by this panel, which is itself a
                    // realistic answer to test the 404 wording against.
                    status(ex, 404);
                }
            } else if (path.startsWith("/movie/")) {
                String file = fileName(path);
                log("FILM " + file + "  range=" + ex.getRequestHeaders().getFirst("Range"));
                if (file.equals("501.mp4")) serveFile(ex, "film.mp4", "video/mp4");
                else if (file.equals("502.mkv")) serveFile(ex, "film.mkv", "video/x-matroska");
                else status(ex, 404);
            } else if (path.startsWith("/series/")) {
                String file = fileName(path);
                log("EPISODE " + file + "  range=" + ex.getRequestHeaders().getFirst("Range"));
                if (file.endsWith(".mp4")) serveFile(ex, "film.mp4", "video/mp4");
                else if (file.endsWith(".mkv")) serveFile(ex, "film.mkv", "video/x-matroska");
                else status(ex, 404);
            } else if (path.startsWith("/poster/")) {
                String id = path.substring("/poster/".length()).replace(".jpg", "");
                serveFile(ex, "poster" + id + ".jpg", "image/jpeg");
            } else {
                log("??? " + path);
                status(ex, 404);
            }
        } catch (IOException e) {
            // A player hanging up mid-stream is normal, not a fault.
            log("   (client closed " + path + ": " + e.getMessage() + ")");
        } finally {
            ex.close();
        }
    }

    /**
     * The address the client used to reach us, for URLs we hand back — poster
     * links, mostly. Taken from the request rather than fixed, so the same
     * panel serves an emulator (10.0.2.2) and a phone on the same Wi-Fi.
     */
    static String base(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            host = "10.0.2.2:" + ex.getLocalAddress().getPort();
        }
        return "http://" + host;
    }

    static String api(String action, Map<String, String> q, String base) {
        long now = Instant.now().getEpochSecond();
        switch (action) {
            case "":
                // The password echoed back in clear, as real panels do: this is
                // what String.withoutCredentialValues() exists to keep out of logs.
                return "{\"user_info\":{\"username\":\"demo\",\"password\":\"demo\",\"auth\":1," +
                    "\"status\":\"Active\",\"exp_date\":\"1893456000\",\"max_connections\":\"1\"," +
                    "\"active_cons\":0,\"allowed_output_formats\":[\"m3u8\",\"ts\"]}," +
                    "\"server_info\":{\"url\":\"" + base.replaceFirst("^http://", "").replaceFirst(":.*", "") + "\"}}";
            case "get_live_categories":
                // A blank id and a repeat, which must not reach the screen as keys.
                return "[{\"category_id\":\"1\",\"category_name\":\"News\"}," +
                    "{\"category_id\":2,\"category_name\":\"Sport\"}," +
                    "{\"category_id\":\"\",\"category_name\":\"Blank id\"}," +
                    "{\"category_id\":\"1\",\"category_name\":\"News\"}]";
            case "get_live_streams": {
                // Ids as numbers and strings, one refusing channel, and two with
                // no stream_id at all — they would share list key 0 and crash
                // the list if they got through.
                String news = "{\"num\":1,\"name\":\"Test News HD\",\"stream_id\":101,\"category_id\":\"1\",\"stream_icon\":\"\",\"epg_channel_id\":\"news.test\"}," +
                    "{\"num\":\"2\",\"name\":\"Test Pattern TV\",\"stream_id\":\"102\",\"category_id\":1}," +
                    "{\"num\":3,\"name\":\"Refused (connection limit)\",\"stream_id\":103,\"category_id\":\"1\"}," +
                    "{\"num\":6,\"name\":\"Drops every 20 s\",\"stream_id\":104,\"category_id\":\"1\"}," +
                    "{\"num\":7,\"name\":\"Drops, then off air\",\"stream_id\":105,\"category_id\":\"1\"}," +
                    "{\"num\":4,\"name\":\"No stream id A\",\"category_id\":\"1\"}," +
                    "{\"num\":5,\"name\":\"No stream id B\",\"category_id\":\"1\"}";
                String sport = "{\"num\":\"1\",\"name\":\"Sport One\",\"stream_id\":\"201\",\"category_id\":\"2\",\"stream_icon\":null}";
                String category = q.get("category_id");
                // No category means every channel on the line, as on a real
                // panel — which is what searching every channel asks for.
                if (category == null || category.isEmpty()) return "[" + news + "," + sport + "]";
                if (category.equals("2")) return "[" + sport + "]";
                if (category.equals("1")) return "[" + news + "]";
                return "[]";
            }
            case "get_short_epg":
                // A guide for 101 only; every other channel has none, which is the
                // normal case and must draw nothing rather than an error.
                if (!"101".equals(q.get("stream_id"))) return "{\"epg_listings\":[]}";
                long start = now - 20 * 60, mid = now + 40 * 60, end = mid + 30 * 60;
                return "{\"epg_listings\":[" +
                    listing("1", "Evening News", "The day's headlines.", start, mid) + "," +
                    listing("2", "Weather Tonight", "Rain, probably.", mid, end) + "]}";
            case "get_vod_categories":
                return "[{\"category_id\":\"10\",\"category_name\":\"Test films\",\"parent_id\":0}]";
            case "get_vod_streams":
                // One mp4 and one mkv: the extension must come from the film, not
                // from the live stream format.
                return "[{\"num\":1,\"name\":\"Test Pattern (MP4)\",\"stream_type\":\"movie\",\"stream_id\":501," +
                    "\"stream_icon\":\"" + base + "/poster/501.jpg\",\"category_id\":\"10\"," +
                    "\"container_extension\":\"mp4\",\"rating\":\"7.5\",\"custom_sid\":null}," +
                    "{\"num\":\"2\",\"name\":\"Colour Bars (MKV)\",\"stream_id\":\"502\"," +
                    "\"stream_icon\":\"" + base + "/poster/502.jpg\",\"category_id\":10," +
                    "\"container_extension\":\"mkv\",\"rating\":0}]";
            case "get_series_categories":
                return "[{\"category_id\":\"20\",\"category_name\":\"Drama\"}]";
            case "get_series":
                return "[{\"num\":1,\"name\":\"Test Horses\",\"series_id\":88,\"cover\":\"" + base + "/poster/501.jpg\"," +
                    "\"plot\":\"Spies, badly.\",\"rating\":\"8.2\",\"releaseDate\":\"2022-04-01\",\"category_id\":\"20\"}," +
                    "{\"name\":\"Bars and Tones\",\"series_id\":\"89\",\"cover\":\"" + base + "/poster/502.jpg\"," +
                    "\"rating\":0,\"category_id\":20}]";
            case "get_series_info":
                if ("89".equals(q.get("series_id"))) {
                    // Episodes as an array of arrays — seasons by position — and
                    // info as PHP's spelling of an empty object: [].
                    return "{\"info\":[],\"seasons\":[],\"episodes\":[[" +
                        ep("8901", "1", "Pilot", "mkv", 0, "[]") + "],[" +
                        ep("8902", "1", "Second Season Opener", "mp4", 0, "[]") + "]]}";
                }
                // Keyed by season and out of order, specials in season 0, an
                // empty seasons[], an episode whose info is [], a repeated
                // episode id and a blank one, and titles carrying the
                // "Show - S01E01 - " prefix the app strips.
                return "{\"seasons\":[],\"info\":{\"name\":\"Test Horses\",\"plot\":\"Spies, badly. A test " +
                    "series for a fake panel, with a plot long enough to need truncating on a phone screen, " +
                    "which is the point of it being this long.\",\"cover\":\"" + base + "/poster/501.jpg\"}," +
                    "\"episodes\":{" +
                    "\"2\":[" + ep("201", "1", "Test Horses - S02E01 - Return", "mp4", 2, "{\"duration_secs\":\"1800\"}") + "," +
                    ep("202", 2, "Test Horses - S02E02 - Hello Goodbye", "mkv", 0, "[]") + "]," +
                    "\"1\":[" + ep("102", 2, "Test Horses - S01E02 - Second", "mp4", 1, "{\"duration_secs\":2700}") + "," +
                    ep("101", 1, "Test Horses - S01E01 - First", "mp4", 1, "{\"duration_secs\":2650,\"plot\":\"It begins.\"}") + "," +
                    ep("101", 1, "duplicate", "mp4", 1, "{}") + "," +
                    ep("", 3, "no id", "mp4", 1, "{}") + "]," +
                    "\"0\":[" + ep("900", 1, "Christmas Special", "mp4", 0, "{}") + "]}}";
            default:
                // How some forks answer an action they do not implement.
                return "false";
        }
    }

    /** An episode record. [num] as a String is sent quoted, as some panels do. */
    static String ep(String id, Object num, String title, String ext, int season, String info) {
        String n = num instanceof String ? "\"" + num + "\"" : String.valueOf(num);
        return "{\"id\":\"" + id + "\",\"episode_num\":" + n + ",\"title\":\"" + title + "\"," +
            "\"container_extension\":\"" + ext + "\",\"season\":" + season + ",\"info\":" + info + "}";
    }

    /**
     * A guide entry: base64 title and description, a quoted start timestamp
     * and a bare stop one, and start/end strings that are deliberately wrong —
     * the app must read the timestamps and ignore the strings.
     */
    static String listing(String id, String title, String desc, long start, long stop) {
        Base64.Encoder b = Base64.getEncoder();
        return "{\"id\":\"" + id + "\",\"epg_id\":\"news.test\",\"title\":\"" +
            b.encodeToString(title.getBytes(StandardCharsets.UTF_8)) + "\",\"description\":\"" +
            b.encodeToString(desc.getBytes(StandardCharsets.UTF_8)) + "\",\"start\":\"2026-01-01 00:00:00\"," +
            "\"end\":\"2026-01-01 01:00:00\",\"start_timestamp\":\"" + start + "\",\"stop_timestamp\":" + stop + "}";
    }

    /**
     * A "live" channel: the TS file sent with no length, as a panel's live
     * output is. It is ten minutes long and then the connection closes, which
     * the player logs as "ended (source closed the connection)" — the same thing
     * a real line's connection limit looks like, so worth knowing it is this.
     */
    /** When each dropping channel last hung up, and when 105 goes back on air. */
    static final Map<String, Long> droppedAt = new java.util.concurrent.ConcurrentHashMap<>();
    static volatile long offAirUntil = 0;

    /**
     * A channel that plays for about 20 seconds and then closes the connection
     * cleanly, as a panel restarting a stream does. Sent at the pace it plays,
     * so the hang-up comes when the picture runs out rather than minutes
     * earlier into a buffer.
     *
     * 104 then refuses a new connection for 2.5 s with a 456, as a panel still
     * counting the old one does: a reconnect that is too quick is refused, and
     * one that waits a moment gets in. 105 goes off air for 45 s — longer than
     * the app keeps trying — and then comes back, for Try again.
     */
    static void streamDropping(HttpExchange ex, String id) throws IOException {
        long now = System.currentTimeMillis();
        Long last = droppedAt.get(id);
        if (id.equals("104") && last != null && now - last < 2_500) {
            log("   (104 refused: old connection still counted)");
            status(ex, 456);
            return;
        }
        if (id.equals("105") && now < offAirUntil) {
            log("   (105 off air for " + (offAirUntil - now) / 1000 + " s more)");
            status(ex, 503);
            return;
        }
        Path file = media.resolve("live.ts");
        if (!Files.exists(file)) {
            missing(ex, file);
            return;
        }
        long size = Files.size(file);
        long perSecond = size / 600; // the clip is 600 s long
        long toSend = perSecond * 20;
        ex.getResponseHeaders().set("Content-Type", "video/mp2t");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody();
             java.io.InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16 * 1024];
            long sent = 0;
            long start = System.currentTimeMillis();
            while (sent < toSend) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, toSend - sent));
                if (n < 0) break;
                out.write(buf, 0, n);
                out.flush();
                sent += n;
                // Two seconds up front for the player to start on, then real time.
                long due = start + (sent - 2 * perSecond) * 1000 / perSecond;
                long wait = due - System.currentTimeMillis();
                if (wait > 0) {
                    try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
                }
            }
        }
        droppedAt.put(id, System.currentTimeMillis());
        if (id.equals("105")) offAirUntil = System.currentTimeMillis() + 45_000;
        log("   (" + id + " hung up after 20 s, as planned)");
    }

    static void streamLive(HttpExchange ex) throws IOException {
        Path file = media.resolve("live.ts");
        if (!Files.exists(file)) {
            missing(ex, file);
            return;
        }
        ex.getResponseHeaders().set("Content-Type", "video/mp2t");
        ex.sendResponseHeaders(200, 0); // chunked: live has no length
        try (OutputStream out = ex.getResponseBody()) {
            Files.copy(file, out);
        }
    }

    /** Serves a media file with Range support, which seeking in a film depends on. */
    static void serveFile(HttpExchange ex, String name, String type) throws IOException {
        Path file = media.resolve(name);
        if (!Files.exists(file)) {
            missing(ex, file);
            return;
        }
        long size = Files.size(file);
        long from = 0, to = size - 1;
        String range = ex.getRequestHeaders().getFirst("Range");
        boolean partial = range != null && range.startsWith("bytes=");
        if (partial) {
            String[] parts = range.substring("bytes=".length()).split("-", -1);
            if (parts[0].isEmpty()) {
                // "bytes=-500": the last 500 bytes.
                from = Math.max(0, size - Long.parseLong(parts[1]));
            } else {
                from = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) to = Math.min(Long.parseLong(parts[1]), size - 1);
            }
            if (from >= size) {
                ex.getResponseHeaders().set("Content-Range", "bytes */" + size);
                status(ex, 416);
                return;
            }
        }
        long length = to - from + 1;
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        if (partial) ex.getResponseHeaders().set("Content-Range", "bytes " + from + "-" + to + "/" + size);
        ex.sendResponseHeaders(partial ? 206 : 200, length);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r"); OutputStream out = ex.getResponseBody()) {
            raf.seek(from);
            byte[] buf = new byte[64 * 1024];
            long left = length;
            while (left > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        }
    }

    /** A clear answer, and a clear log line, when the media has not been made yet. */
    static void missing(HttpExchange ex, Path file) throws IOException {
        log("   " + file.getFileName() + " is missing from " + media.toAbsolutePath() + " — run make-media.sh");
        status(ex, 404);
    }

    static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static void json(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void status(HttpExchange ex, int code) throws IOException {
        ex.sendResponseHeaders(code, -1);
    }

    static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }
}
