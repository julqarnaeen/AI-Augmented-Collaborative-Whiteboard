package network;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class MathExpressionSolver {

    public static class Point {
        public int x;
        public int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class MathSolveRequest {
        public List<List<Point>> strokes;

        public MathSolveRequest(List<List<Point>> strokes) {
            this.strokes = strokes;
        }
    }

    public static class MathSolveResponse {
        public String expression;
        public String result;
        public int text_x;
        public int text_y;
    }

    private static final String SOLVER_URL = "http://localhost:8000/solve_math";

    public static MathSolveResponse solve(List<WhiteboardPanel.Stroke> strokesList) {
        try {
            List<List<Point>> serializedStrokes = new ArrayList<>();
            for (WhiteboardPanel.Stroke stroke : strokesList) {
                List<Point> pts = new ArrayList<>();
                if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.FREEHAND) {
                    for (WhiteboardPanel.DrawPoint dp : stroke.getPoints()) {
                        pts.add(new Point(dp.x, dp.y));
                    }
                } else if (stroke.getType() == WhiteboardPanel.Stroke.ShapeType.LINE) {
                    pts.add(new Point(stroke.getX1(), stroke.getY1()));
                    pts.add(new Point(stroke.getX2(), stroke.getY2()));
                }
                if (!pts.isEmpty()) {
                    serializedStrokes.add(pts);
                }
            }

            if (serializedStrokes.isEmpty()) {
                return null;
            }

            MathSolveRequest req = new MathSolveRequest(serializedStrokes);
            Gson gson = new Gson();
            String jsonPayload = gson.toJson(req);

            URL url = new URL(SOLVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return gson.fromJson(response.toString(), MathSolveResponse.class);
                }
            } else {
                System.err.println("[MathSolver] HTTP error: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("[MathSolver] Failed to call AI math solver service: " + e.getMessage());
        }
        return null;
    }
}
