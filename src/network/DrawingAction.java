package network;

import java.awt.Color;
import java.util.List;

/**
 * A unified action model that wraps every discrete user action on the
 * whiteboard canvas.  Instances are immutable after construction.
 *
 * <p>Each {@code DrawingAction} carries a {@link Type}, the originating
 * client ID, a millisecond timestamp, and type-specific payload fields
 * (stroke, text element, move coordinates, or free-form extra data).
 *
 * <p>The class provides {@link #toMessage()} and
 * {@link #fromMessage(String, String)} for round-tripping through the
 * existing wire protocol.
 */
public class DrawingAction {

    // ------------------------------------------------------------------ enum

    /** Every kind of discrete user action the whiteboard supports. */
    public enum Type {
        STROKE,
        TEXT,
        MOVE_TEXT,
        CLEAR_CANVAS,
        UNDO,
        BLOCK_SLANG
    }

    // ---------------------------------------------------------------- fields

    private final Type type;
    private final String clientId;
    private final long timestamp;

    /** Non-null only when {@code type == STROKE}. */
    private final WhiteboardPanel.Stroke stroke;

    /** Non-null only when {@code type == TEXT}. */
    private final WhiteboardPanel.TextElement textElement;

    /** Coordinates used when {@code type == MOVE_TEXT}. */
    private final int oldX;
    private final int oldY;
    private final int newX;
    private final int newY;

    /** Free-form payload, e.g. the blocked word for {@code BLOCK_SLANG}. */
    private final String extraData;

    // ---------------------------------------------------------- constructors

    /**
     * Creates an action that carries no extra payload.
     * Suitable for {@link Type#CLEAR_CANVAS} and {@link Type#UNDO}.
     */
    public DrawingAction(Type type, String clientId) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    /**
     * Creates a STROKE action wrapping the given stroke data.
     */
    public DrawingAction(Type type, String clientId, WhiteboardPanel.Stroke stroke) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = stroke;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    /**
     * Creates a TEXT action wrapping the given text element.
     */
    public DrawingAction(Type type, String clientId, WhiteboardPanel.TextElement textElement) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = textElement;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = null;
    }

    /**
     * Creates a MOVE_TEXT action with old and new coordinates.
     */
    public DrawingAction(Type type, String clientId, int oldX, int oldY, int newX, int newY) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
        this.extraData = null;
    }

    /**
     * Creates an action carrying a free-form string payload.
     * Suitable for {@link Type#BLOCK_SLANG}.
     */
    public DrawingAction(Type type, String clientId, String extraData) {
        this.type = type;
        this.clientId = clientId;
        this.timestamp = System.currentTimeMillis();
        this.stroke = null;
        this.textElement = null;
        this.oldX = 0;
        this.oldY = 0;
        this.newX = 0;
        this.newY = 0;
        this.extraData = extraData;
    }

    // --------------------------------------------------------------- getters

    public Type getType() {
        return type;
    }

    public String getClientId() {
        return clientId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public WhiteboardPanel.Stroke getStroke() {
        return stroke;
    }

    public WhiteboardPanel.TextElement getTextElement() {
        return textElement;
    }

    public int getOldX() {
        return oldX;
    }

    public int getOldY() {
        return oldY;
    }

    public int getNewX() {
        return newX;
    }

    public int getNewY() {
        return newY;
    }

    public String getExtraData() {
        return extraData;
    }

    // --------------------------------------------------------- serialization

    /**
     * Serializes this action into the existing wire-protocol format.
     *
     * <p>For {@code STROKE} actions with {@code ShapeType.FREEHAND}, this
     * returns the first {@code DRAW_LINE} segment only (freehand strokes
     * are sent as multiple individual messages during drag).  For normalized
     * shapes it returns the single corresponding message.
     *
     * @return a protocol string ready to send over the connection,
     *         or {@code null} if the action cannot be serialized
     */
    public String toMessage() {
        switch (type) {
            case STROKE:
                return strokeToMessage();

            case TEXT:
                if (textElement == null) return null;
                return "TEXT:" + textElement.getX() + "," + textElement.getY()
                    + "," + textElement.getColor().getRGB()
                    + "," + textElement.getFontSize()
                    + "," + textElement.getText();

            case MOVE_TEXT:
                return "MOVE_TEXT:" + oldX + "," + oldY + "," + newX + "," + newY;

            case CLEAR_CANVAS:
                return "CLEAR_CANVAS:";

            case UNDO:
                return "UNDO:";

            case BLOCK_SLANG:
                return "BLOCK_SLANG:" + (extraData != null ? extraData : "");

            default:
                System.err.println("[DrawingAction] Unknown type in toMessage: " + type);
                return null;
        }
    }

    /**
     * Converts a stroke action into the appropriate wire-protocol message
     * based on its {@link WhiteboardPanel.Stroke.ShapeType}.
     */
    private String strokeToMessage() {
        if (stroke == null) return null;

        WhiteboardPanel.Stroke.ShapeType shapeType = stroke.getType();
        int rgb = stroke.getColor().getRGB();
        int sw = stroke.getStrokeWidth();

        switch (shapeType) {
            case LINE:
                return "DRAW_LINE:" + stroke.getX1() + "," + stroke.getY1()
                    + "," + stroke.getX2() + "," + stroke.getY2()
                    + "," + rgb + "," + sw;

            case RECTANGLE:
                return "DRAW_RECT:" + stroke.getX1() + "," + stroke.getY1()
                    + "," + stroke.getX2() + "," + stroke.getY2()
                    + "," + rgb + "," + sw;

            case CIRCLE:
                return "DRAW_CIRCLE:" + stroke.getX1() + "," + stroke.getY1()
                    + "," + stroke.getX2() + "," + stroke.getY2()
                    + "," + rgb + "," + sw;

            case TRIANGLE:
                return "DRAW_TRI:" + stroke.getX1() + "," + stroke.getY1()
                    + "," + stroke.getX2() + "," + stroke.getY2()
                    + "," + rgb + "," + sw;

            case FREEHAND:
            default:
                // Freehand strokes with at least 2 points: return the first segment
                List<WhiteboardPanel.DrawPoint> pts = stroke.getPoints();
                if (pts != null && pts.size() >= 2) {
                    WhiteboardPanel.DrawPoint p1 = pts.get(0);
                    WhiteboardPanel.DrawPoint p2 = pts.get(1);
                    return "DRAW_LINE:" + p1.x + "," + p1.y
                        + "," + p2.x + "," + p2.y
                        + "," + rgb + "," + sw;
                }
                return null;
        }
    }

    // -------------------------------------------------------- deserialization

    /**
     * Parses a wire-protocol message string into a {@code DrawingAction}.
     *
     * <p>The {@code message} parameter should be the action portion of the
     * protocol string (i.e., <em>without</em> the {@code FROM:sender|}
     * prefix — that is stripped by the caller).
     *
     * @param message  the protocol message to parse
     * @param clientId the ID of the client that sent the message
     * @return a new {@code DrawingAction}, or {@code null} if the message
     *         is unrecognized or malformed
     */
    public static DrawingAction fromMessage(String message, String clientId) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        String messageType;
        String messageData = "";
        if (message.contains(":")) {
            int colonIndex = message.indexOf(":");
            messageType = message.substring(0, colonIndex).toUpperCase();
            messageData = message.substring(colonIndex + 1);
        } else {
            messageType = message.toUpperCase();
        }

        try {
            switch (messageType) {
                case "DRAW_LINE":
                    return parseDrawLine(messageData, clientId);

                case "DRAW_RECT":
                    return parseDrawShape(messageData, clientId,
                        WhiteboardPanel.Stroke.ShapeType.RECTANGLE);

                case "DRAW_CIRCLE":
                    return parseDrawShape(messageData, clientId,
                        WhiteboardPanel.Stroke.ShapeType.CIRCLE);

                case "DRAW_TRI":
                    return parseDrawShape(messageData, clientId,
                        WhiteboardPanel.Stroke.ShapeType.TRIANGLE);

                case "TEXT":
                    return parseText(messageData, clientId);

                case "MOVE_TEXT":
                    return parseMoveText(messageData, clientId);

                case "CLEAR_CANVAS":
                    return new DrawingAction(Type.CLEAR_CANVAS, clientId);

                case "UNDO":
                    return new DrawingAction(Type.UNDO, clientId);

                case "BLOCK_SLANG":
                    return new DrawingAction(Type.BLOCK_SLANG, clientId, messageData);

                case "DRAW_START":
                case "DRAW_END":
                    // These are framing messages — no action needed
                    return null;

                default:
                    System.out.println("[DrawingAction] Unknown message type: " + messageType);
                    return null;
            }
        } catch (NumberFormatException e) {
            System.err.println("[DrawingAction] Error parsing message '" + message
                + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses a DRAW_LINE message into a STROKE action with a two-point
     * freehand stroke.
     */
    private static DrawingAction parseDrawLine(String data, String clientId) {
        String[] parts = data.split(",");
        if (parts.length < 4) return null;

        int x1 = Integer.parseInt(parts[0].trim());
        int y1 = Integer.parseInt(parts[1].trim());
        int x2 = Integer.parseInt(parts[2].trim());
        int y2 = Integer.parseInt(parts[3].trim());

        Color color = Color.BLACK;
        int strokeWidth = 3;
        if (parts.length >= 6) {
            color = new Color(Integer.parseInt(parts[4].trim()));
            strokeWidth = Integer.parseInt(parts[5].trim());
        }

        WhiteboardPanel.Stroke stroke = new WhiteboardPanel.Stroke(color, strokeWidth);
        stroke.addPoint(x1, y1);
        stroke.addPoint(x2, y2);

        return new DrawingAction(Type.STROKE, clientId, stroke);
    }

    /**
     * Parses a DRAW_RECT, DRAW_CIRCLE, or DRAW_TRI message into a STROKE
     * action with the appropriate shape type.
     */
    private static DrawingAction parseDrawShape(String data, String clientId,
                                                 WhiteboardPanel.Stroke.ShapeType shapeType) {
        String[] parts = data.split(",");
        if (parts.length < 6) return null;

        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());
        int w = Integer.parseInt(parts[2].trim());
        int h = Integer.parseInt(parts[3].trim());
        Color color = new Color(Integer.parseInt(parts[4].trim()));
        int strokeWidth = Integer.parseInt(parts[5].trim());

        WhiteboardPanel.Stroke stroke = new WhiteboardPanel.Stroke(
            color, strokeWidth, shapeType, x, y, w, h);

        return new DrawingAction(Type.STROKE, clientId, stroke);
    }

    /**
     * Parses a TEXT message into a TEXT action with a TextElement.
     */
    private static DrawingAction parseText(String data, String clientId) {
        String[] parts = data.split(",", 5);
        if (parts.length < 4) return null;

        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());
        Color color = new Color(Integer.parseInt(parts[2].trim()));

        int fontSize = 20;
        String content;
        if (parts.length == 5) {
            fontSize = Integer.parseInt(parts[3].trim());
            content = parts[4];
        } else {
            content = parts[3];
        }

        WhiteboardPanel.TextElement te = new WhiteboardPanel.TextElement(
            content, x, y, color, fontSize);

        return new DrawingAction(Type.TEXT, clientId, te);
    }

    /**
     * Parses a MOVE_TEXT message into a MOVE_TEXT action.
     */
    private static DrawingAction parseMoveText(String data, String clientId) {
        String[] parts = data.split(",");
        if (parts.length < 4) return null;

        int oX = Integer.parseInt(parts[0].trim());
        int oY = Integer.parseInt(parts[1].trim());
        int nX = Integer.parseInt(parts[2].trim());
        int nY = Integer.parseInt(parts[3].trim());

        return new DrawingAction(Type.MOVE_TEXT, clientId, oX, oY, nX, nY);
    }

    // -------------------------------------------------------------- toString

    @Override
    public String toString() {
        return "DrawingAction[" + type + " by " + clientId + " at " + timestamp + "]";
    }
}
