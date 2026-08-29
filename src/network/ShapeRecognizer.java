// Turns a freehand stroke into a clean line, rectangle, circle, ellipse, or triangle using plain geometry.
package network;

import java.util.ArrayList;
import java.util.List;
import network.WhiteboardPanel.DrawPoint;

public class ShapeRecognizer {

    private static final int SAMPLES = 64;
    private static final double CLOSE_RATIO = 0.25;
    private static final double CLOSE_SWEEP = 5.2;
    private static final double ROUND_ASPECT = 1.25;
    private static final double LINE_RMS_TOLERANCE = 0.045;
    private static final double LINE_MAX_TOLERANCE = 0.16;
    private static final double ROUND_TOLERANCE = 0.06;
    private static final double RECT_ROUND_TOLERANCE = 0.15;
    private static final double MIN_FILL = 0.30;
    private static final double RECT_FILL = 0.75;
    private static final double CORNER_EPS = 0.055;
    private static final double SHORT_EDGE = 0.10;
    private static final int MIN_SPAN = 25;

    // The recognized shape type and the box or endpoints to draw it with.
    public static class NormalizedShape {
        // The shape kinds this recognizer can produce.
        public enum Type { FREEHAND, LINE, RECTANGLE, CIRCLE, TRIANGLE }
        public final Type type;
        public final int x1, y1, x2, y2;

        // Holds one recognition result.
        public NormalizedShape(Type type, int x1, int y1, int x2, int y2) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static final NormalizedShape FREEHAND =
            new NormalizedShape(NormalizedShape.Type.FREEHAND, 0, 0, 0, 0);

    // Classifies a stroke and returns the clean shape to draw in its place.
    public static NormalizedShape normalize(List<DrawPoint> points) {
        if (points == null || points.size() < 5) return FREEHAND;

        List<double[]> raw = new ArrayList<>();
        for (DrawPoint p : points) {
            double[] last = raw.isEmpty() ? null : raw.get(raw.size() - 1);
            if (last == null || last[0] != p.x || last[1] != p.y) raw.add(new double[]{p.x, p.y});
        }
        if (raw.size() < 5) return FREEHAND;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : raw) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        double w = maxX - minX, h = maxY - minY;
        double diagonal = Math.hypot(w, h);
        if (Math.max(w, h) < MIN_SPAN) return FREEHAND;

        double[] first = raw.get(0);
        double[] last = raw.get(raw.size() - 1);
        double perimeter = pathLength(raw);
        if (perimeter <= 0) return FREEHAND;

        double rawCx = 0, rawCy = 0;
        for (double[] p : raw) { rawCx += p[0]; rawCy += p[1]; }
        rawCx /= raw.size();
        rawCy /= raw.size();

        boolean closed = dist(first, last) < CLOSE_RATIO * diagonal
                || sweep(raw, rawCx, rawCy) >= CLOSE_SWEEP;

        if (!closed) {
            double chord = dist(first, last);
            if (chord > 0
                    && rmsDeviation(raw, first, last) / chord < LINE_RMS_TOLERANCE
                    && maxDeviation(raw, first, last) / chord < LINE_MAX_TOLERANCE) {
                return new NormalizedShape(NormalizedShape.Type.LINE,
                        (int) Math.round(first[0]), (int) Math.round(first[1]),
                        (int) Math.round(last[0]), (int) Math.round(last[1]));
            }
            return FREEHAND;
        }

        List<double[]> loop = smooth(resample(raw, SAMPLES));
        double cx = 0, cy = 0;
        for (double[] p : loop) { cx += p[0]; cy += p[1]; }
        cx /= loop.size();
        cy /= loop.size();

        double roundness = normalizedRoundness(loop, minX, minY, w, h);
        int corners = countCorners(loop, cx, cy, CORNER_EPS * diagonal);
        double fill = w * h > 0 ? polygonArea(loop) / (w * h) : 0;

        if (roundness < ROUND_TOLERANCE) {
            return oval(minX, minY, w, h);
        }
        if (fill >= MIN_FILL) {
            if (corners == 3) {
                return box(NormalizedShape.Type.TRIANGLE, minX, minY, w, h);
            }
            if (corners == 4 || (fill >= RECT_FILL && roundness < RECT_ROUND_TOLERANCE)) {
                return box(NormalizedShape.Type.RECTANGLE, minX, minY, w, h);
            }
        }
        return FREEHAND;
    }

    // Measures how circular a loop is once scaled into its bounding box.
    private static double normalizedRoundness(List<double[]> loop, double minX, double minY, double w, double h) {
        if (w <= 0 || h <= 0) return 1;
        double cx = 0.5;
        double cy = 0.5;
        double mean = 0;
        for (double[] p : loop) mean += Math.hypot((p[0] - minX) / w - cx, (p[1] - minY) / h - cy);
        mean /= loop.size();
        if (mean <= 0) return 1;
        double variance = 0;
        for (double[] p : loop) {
            double d = Math.hypot((p[0] - minX) / w - cx, (p[1] - minY) / h - cy) - mean;
            variance += d * d;
        }
        return Math.sqrt(variance / loop.size()) / mean;
    }

    // Returns the shoelace area of the loop.
    private static double polygonArea(List<double[]> loop) {
        double area = 0;
        for (int i = 0; i < loop.size(); i++) {
            double[] a = loop.get(i);
            double[] b = loop.get((i + 1) % loop.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(area) / 2;
    }

    // Builds a circle for near-square bounds, otherwise an ellipse.
    private static NormalizedShape oval(double minX, double minY, double w, double h) {
        double aspect = Math.max(w, h) / Math.max(1, Math.min(w, h));
        if (aspect <= ROUND_ASPECT) {
            double d = (w + h) / 2;
            return box(NormalizedShape.Type.CIRCLE,
                    minX + (w - d) / 2, minY + (h - d) / 2, d, d);
        }
        return box(NormalizedShape.Type.CIRCLE, minX, minY, w, h);
    }

    // Returns the total angle the stroke sweeps around a center point.
    private static double sweep(List<double[]> pts, double cx, double cy) {
        double total = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double a = Math.atan2(pts.get(i)[1] - cy, pts.get(i)[0] - cx);
            double b = Math.atan2(pts.get(i + 1)[1] - cy, pts.get(i + 1)[0] - cx);
            double d = b - a;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            total += d;
        }
        return Math.abs(total);
    }

    // Builds a shape result from bounding box coordinates.
    private static NormalizedShape box(NormalizedShape.Type t, double minX, double minY, double w, double h) {
        return new NormalizedShape(t, (int) Math.round(minX), (int) Math.round(minY),
                (int) Math.round(w), (int) Math.round(h));
    }

    // Counts the corners of a loop by simplifying it from its farthest point.
    private static int countCorners(List<double[]> loop, double cx, double cy, double eps) {
        int startIdx = 0;
        double best = -1;
        for (int i = 0; i < loop.size(); i++) {
            double d = Math.hypot(loop.get(i)[0] - cx, loop.get(i)[1] - cy);
            if (d > best) { best = d; startIdx = i; }
        }
        List<double[]> rotated = new ArrayList<>();
        for (int i = 0; i < loop.size(); i++) rotated.add(loop.get((startIdx + i) % loop.size()));
        rotated.add(rotated.get(0));

        List<double[]> simplified = new ArrayList<>();
        rdp(rotated, 0, rotated.size() - 1, eps, simplified);
        return dropShortEdges(simplified);
    }

    // Counts only corners separated by a meaningful edge, merging duplicates.
    private static int dropShortEdges(List<double[]> corners) {
        int n = corners.size();
        if (n < 3) return n;
        double perimeter = 0;
        for (int i = 0; i < n; i++) perimeter += dist(corners.get(i), corners.get((i + 1) % n));
        if (perimeter <= 0) return n;
        int kept = 0;
        for (int i = 0; i < n; i++) {
            if (dist(corners.get(i), corners.get((i + 1) % n)) >= SHORT_EDGE * perimeter) kept++;
        }
        return kept;
    }

    // Ramer-Douglas-Peucker simplification, keeping points that deviate past the tolerance.
    private static void rdp(List<double[]> pts, int from, int to, double eps, List<double[]> out) {
        double maxDist = -1;
        int idx = from;
        for (int i = from + 1; i < to; i++) {
            double d = pointToSegment(pts.get(i), pts.get(from), pts.get(to));
            if (d > maxDist) { maxDist = d; idx = i; }
        }
        if (maxDist > eps) {
            rdp(pts, from, idx, eps, out);
            rdp(pts, idx, to, eps, out);
        } else {
            out.add(pts.get(from));
        }
    }

    // Respaces a stroke into a fixed number of evenly spaced points.
    private static List<double[]> resample(List<double[]> pts, int count) {
        double step = pathLength(pts) / (count - 1);
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        double accumulated = 0;
        double[] prev = pts.get(0);
        for (int i = 1; i < pts.size(); ) {
            double[] cur = pts.get(i);
            double d = dist(prev, cur);
            if (accumulated + d >= step && d > 0) {
                double t = (step - accumulated) / d;
                double[] np = new double[]{prev[0] + t * (cur[0] - prev[0]), prev[1] + t * (cur[1] - prev[1])};
                out.add(np);
                prev = np;
                accumulated = 0;
                if (out.size() == count) break;
            } else {
                accumulated += d;
                prev = cur;
                i++;
            }
        }
        while (out.size() < count) out.add(pts.get(pts.size() - 1));
        return out;
    }

    // Averages each point with its neighbours around the loop to damp hand tremor.
    private static List<double[]> smooth(List<double[]> loop) {
        int n = loop.size();
        List<double[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double sx = 0, sy = 0;
            for (int k = -2; k <= 2; k++) {
                double[] p = loop.get(((i + k) % n + n) % n);
                sx += p[0];
                sy += p[1];
            }
            out.add(new double[]{sx / 5, sy / 5});
        }
        return out;
    }

    // Returns the root-mean-square distance from the points to a segment.
    private static double rmsDeviation(List<double[]> pts, double[] a, double[] b) {
        double sum = 0;
        for (double[] p : pts) {
            double d = pointToSegment(p, a, b);
            sum += d * d;
        }
        return Math.sqrt(sum / pts.size());
    }

    // Returns the largest distance from any point to a segment.
    private static double maxDeviation(List<double[]> pts, double[] a, double[] b) {
        double max = 0;
        for (double[] p : pts) max = Math.max(max, pointToSegment(p, a, b));
        return max;
    }

    // Returns the shortest distance from a point to a line segment.
    private static double pointToSegment(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return dist(p, a);
        double t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2));
        return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    }

    // Returns the total length walked along the point list.
    private static double pathLength(List<double[]> pts) {
        double sum = 0;
        for (int i = 0; i < pts.size() - 1; i++) sum += dist(pts.get(i), pts.get(i + 1));
        return sum;
    }

    // Returns the straight-line distance between two points.
    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
