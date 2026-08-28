package network;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import network.WhiteboardPanel.DrawPoint;
import com.google.gson.Gson;

public class ShapeRecognizer {

    private static final String RECOGNIZE_URL = "http://localhost:8000/recognize_shape";

    public static class NormalizedShape {
        public enum Type { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE }
        public final Type type;
        public final int x1, y1, x2, y2;

        public NormalizedShape(Type type, int x1, int y1, int x2, int y2) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static class ShapeRequest {
        List<DrawPoint> points;
        ShapeRequest(List<DrawPoint> points) { this.points = points; }
    }

    private static class ShapeResponse {
        String shape_type;
    }

    public static NormalizedShape normalize(List<DrawPoint> points) {
        if (points == null || points.size() < 5) {
            return new NormalizedShape(NormalizedShape.Type.FREEHAND, 0, 0, 0, 0);
        }

        int N = points.size();
        DrawPoint start = points.get(0);
        DrawPoint end = points.get(N - 1);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double sumX = 0;
        double sumY = 0;

        for (DrawPoint p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
            sumX += p.x;
            sumY += p.y;
        }

        int W = maxX - minX;
        int H = maxY - minY;
        double cx = sumX / N;
        double cy = sumY / N;

        if (W < 12 || H < 12) {
            return new NormalizedShape(NormalizedShape.Type.FREEHAND, 0, 0, 0, 0);
        }

        // Call python AI shape normalizer service
        String shapeTypeStr = "FREEHAND";
        try {
            Gson gson = new Gson();
            ShapeRequest req = new ShapeRequest(points);
            String jsonPayload = gson.toJson(req);

            URL url = new URL(RECOGNIZE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] in = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(in, 0, in.length);
            }

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    ShapeResponse resp = gson.fromJson(response.toString(), ShapeResponse.class);
                    if (resp != null && resp.shape_type != null) {
                        shapeTypeStr = resp.shape_type;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ShapeRecognizer] Python AI shape service offline. Falling back to local line check.");
            // Fallback: simple line check
            double pathLength = 0;
            for (int i = 0; i < N - 1; i++) {
                pathLength += distance(points.get(i), points.get(i + 1));
            }
            double directDistance = distance(start, end);
            if (pathLength > 0 && (directDistance / pathLength) > 0.85) {
                shapeTypeStr = "LINE";
            }
        }

        NormalizedShape.Type type = NormalizedShape.Type.FREEHAND;
        try {
            type = NormalizedShape.Type.valueOf(shapeTypeStr);
        } catch (IllegalArgumentException e) {
            type = NormalizedShape.Type.FREEHAND;
        }

        if (type == NormalizedShape.Type.LINE) {
            return new NormalizedShape(type, start.x, start.y, end.x, end.y);
        } else if (type == NormalizedShape.Type.CIRCLE) {
            double sumRadius = 0;
            for (DrawPoint p : points) {
                sumRadius += distance(p.x, p.y, cx, cy);
            }
            double meanRadius = sumRadius / N;
            int radius = (int) meanRadius;
            int x = (int) (cx - radius);
            int y = (int) (cy - radius);
            return new NormalizedShape(type, x, y, radius * 2, radius * 2);
        } else if (type == NormalizedShape.Type.RECTANGLE || type == NormalizedShape.Type.TRIANGLE) {
            return new NormalizedShape(type, minX, minY, W, H);
        }

        return new NormalizedShape(NormalizedShape.Type.FREEHAND, 0, 0, 0, 0);
    }

    private static double distance(DrawPoint p1, DrawPoint p2) {
        return distance(p1.x, p1.y, p2.x, p2.y);
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
