package si.buswidget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Talks to the LPP open data API (data.lpp.si). */
final class Lpp {

    private static final String BASE = "https://data.lpp.si/api";

    static final class Arrival {
        String line;   // e.g. "3B"
        int eta;       // minutes
        int type;      // 0 = live, 1 = timetable, 2 = at the stop
        String dest;   // e.g. "ŠKOFLJICA"
        String tripId; // used to look up the stops this bus passes
    }

    /** One stop on a bus's route. */
    static final class RouteStop {
        String code;
        String name;
    }

    static final class Station {
        String code;   // e.g. "600022"
        String name;   // e.g. "Ajdovščina"
        List<String> groups = new ArrayList<>(); // line numbers stopping here, e.g. "3", "6"
    }

    private static volatile List<Station> stationCache;

    static String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "LPP-Bus-Widget/1.7.5 (Android)");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    /** Upcoming buses at one platform, soonest first. */
    static List<Arrival> arrivals(String code, int timeoutMs) throws Exception {
        String body = get(BASE + "/station/arrival?station-code=" + code, timeoutMs);
        JSONArray arr = new JSONObject(body).getJSONObject("data").getJSONArray("arrivals");
        List<Arrival> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.getJSONObject(i);
            Arrival x = new Arrival();
            x.line = a.optString("route_name", "");
            x.eta = a.optInt("eta_min", 0);
            x.type = a.optInt("type", 0);
            JSONObject st = a.optJSONObject("stations");
            String dest = st != null ? st.optString("arrival", "") : "";
            x.dest = dest.replace(" obrač.", "").trim();
            x.tripId = a.optString("trip_id", "");
            list.add(x);
        }
        list.sort((p, q) -> Integer.compare(p.eta, q.eta));
        return list;
    }

    /** One bus (line + destination) that stops at a platform, whether or not it runs right now. */
    static final class Route {
        String line;   // e.g. "3G"
        String dest;   // e.g. "GROSUPLJE"
        String tripId; // a sample trip, used to look up its stops
    }

    /** Every line and destination that serves this platform (day and night), without trips to the depot. */
    static List<Route> routesOnStation(String code, int timeoutMs) throws Exception {
        String body = get(BASE + "/station/routes-on-station?station-code=" + code, timeoutMs);
        JSONArray arr = new JSONObject(body).getJSONArray("data");
        List<Route> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o.optBoolean("is_garage", false)) continue;
            Route r = new Route();
            r.line = o.optString("route_number", "").trim();
            r.dest = o.optString("route_name", "").replace(" obrač.", "").trim();
            r.tripId = o.optString("trip_id", "");
            if (!r.line.isEmpty()) list.add(r);
        }
        return list;
    }

    /** All stops of one trip, in driving order. */
    static List<RouteStop> stopsOnRoute(String tripId, int timeoutMs) throws Exception {
        String body = get(BASE + "/route/stations-on-route?trip-id=" + tripId, timeoutMs);
        JSONArray arr = new JSONObject(body).getJSONArray("data");
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) items.add(arr.getJSONObject(i));
        items.sort((p, q) -> Integer.compare(p.optInt("order_no", 0), q.optInt("order_no", 0)));
        List<RouteStop> list = new ArrayList<>();
        for (JSONObject o : items) {
            RouteStop s = new RouteStop();
            s.code = o.optString("station_code", "");
            s.name = o.optString("name", "").trim();
            list.add(s);
        }
        return list;
    }

    /** All LPP stops (both platforms of a stop share a name). Cached after the first call. */
    static List<Station> stations() throws Exception {
        List<Station> cached = stationCache;
        if (cached != null) return cached;
        String body = get(BASE + "/station/station-details?show-subroutes=0", 15000);
        JSONArray arr = new JSONObject(body).getJSONArray("data");
        List<Station> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.getJSONObject(i);
            Station st = new Station();
            st.code = s.optString("ref_id", "");
            st.name = s.optString("name", "").trim();
            JSONArray g = s.optJSONArray("route_groups_on_station");
            if (g != null) for (int k = 0; k < g.length(); k++) st.groups.add(g.optString(k, ""));
            if (!st.code.isEmpty() && !st.name.isEmpty()) list.add(st);
        }
        stationCache = list;
        return list;
    }
}
