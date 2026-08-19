package network;

import java.util.List;
import network.WhiteboardPanel.DrawPoint;

public class ShapeNormalizer {

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

    public static NormalizedShape normalize(List<DrawPoint> points) {
        if (points == null || points.size() < 5) {
            return new NormalizedShape(NormalizedShape.Type.FREEHAND, 0, 0, 0, 0);
        }

        int N = points.size();
        DrawPoint start = points.get(0);
        DrawPoint end = points.get(N - 1);

        double pathLength = 0;
        for (int i = 0; i < N - 1; i++) {
            pathLength += distance(points.get(i), points.get(i + 1));
        }
        double directDistance = distance(start, end);

        if (pathLength > 0 && (directDistance / pathLength) > 0.85) {
            return new NormalizedShape(NormalizedShape.Type.LINE, start.x, start.y, end.x, end.y);
        }

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

        double startEndDist = distance(start, end);
        boolean isClosed = startEndDist < 60 || (pathLength > 0 && startEndDist / pathLength < 0.3);

        if (isClosed) {

            double sumRadius = 0;
            for (DrawPoint p : points) {
                sumRadius += distance(p.x, p.y, cx, cy);
            }
            double meanRadius = sumRadius / N;

            double varianceSum = 0;
            for (DrawPoint p : points) {
                double diff = distance(p.x, p.y, cx, cy) - meanRadius;
                varianceSum += diff * diff;
            }
            double stdDev = Math.sqrt(varianceSum / N);
            double cv = (meanRadius > 0) ? (stdDev / meanRadius) : 1.0;

            if (cv < 0.18) {
                int radius = (int) meanRadius;
                int x = (int) (cx - radius);
                int y = (int) (cy - radius);
                return new NormalizedShape(NormalizedShape.Type.CIRCLE, x, y, radius * 2, radius * 2);
            }

            double minDistanceToTL = Double.MAX_VALUE;
            double minDistanceToTR = Double.MAX_VALUE;
            for (DrawPoint p : points) {
                double dTL = distance(p.x, p.y, minX, minY);
                double dTR = distance(p.x, p.y, maxX, minY);
                if (dTL < minDistanceToTL) minDistanceToTL = dTL;
                if (dTR < minDistanceToTR) minDistanceToTR = dTR;
            }

            double diagonal = Math.sqrt(W * W + H * H);
            if (minDistanceToTL > 0.22 * diagonal && minDistanceToTR > 0.22 * diagonal) {
                return new NormalizedShape(NormalizedShape.Type.TRIANGLE, minX, minY, W, H);
            }

            return new NormalizedShape(NormalizedShape.Type.RECTANGLE, minX, minY, W, H);
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
