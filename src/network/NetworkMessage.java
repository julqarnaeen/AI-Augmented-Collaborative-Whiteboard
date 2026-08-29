// Plain JSON data object describing one whiteboard event exchanged between client and server.
package network;

public class NetworkMessage {
    private String type;
    private String senderId;
    private Integer x1;
    private Integer y1;
    private Integer x2;
    private Integer y2;
    private Integer colorRgb;
    private Integer strokeWidth;
    private String text;
    private Integer fontSize;

    // Required no-argument constructor for Gson.
    public NetworkMessage() {}

    // Creates a message of the given type.
    public NetworkMessage(String type) {
        this.type = type;
    }

    // Returns the message type tag.
    public String getType() {
        return type;
    }

    // Sets the message type tag.
    public void setType(String type) {
        this.type = type;
    }

    // Returns the id of the client that sent this message.
    public String getSenderId() {
        return senderId;
    }

    // Sets the id of the sending client.
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    // Returns the first x coordinate.
    public Integer getX1() {
        return x1;
    }

    // Sets the first x coordinate.
    public void setX1(Integer x1) {
        this.x1 = x1;
    }

    // Returns the first y coordinate.
    public Integer getY1() {
        return y1;
    }

    // Sets the first y coordinate.
    public void setY1(Integer y1) {
        this.y1 = y1;
    }

    // Returns the second x coordinate, or the shape width.
    public Integer getX2() {
        return x2;
    }

    // Sets the second x coordinate, or the shape width.
    public void setX2(Integer x2) {
        this.x2 = x2;
    }

    // Returns the second y coordinate, or the shape height.
    public Integer getY2() {
        return y2;
    }

    // Sets the second y coordinate, or the shape height.
    public void setY2(Integer y2) {
        this.y2 = y2;
    }

    // Returns the packed RGB drawing color.
    public Integer getColorRgb() {
        return colorRgb;
    }

    // Sets the packed RGB drawing color.
    public void setColorRgb(Integer colorRgb) {
        this.colorRgb = colorRgb;
    }

    // Returns the pen width in pixels.
    public Integer getStrokeWidth() {
        return strokeWidth;
    }

    // Sets the pen width in pixels.
    public void setStrokeWidth(Integer strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    // Returns the text payload used by text and chat messages.
    public String getText() {
        return text;
    }

    // Sets the text payload used by text and chat messages.
    public void setText(String text) {
        this.text = text;
    }

    // Returns the font size for text elements.
    public Integer getFontSize() {
        return fontSize;
    }

    // Sets the font size for text elements.
    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    private String strokeId;

    // Returns the unique stroke or action identifier.
    public String getStrokeId() {
        return strokeId;
    }

    // Sets the unique stroke or action identifier.
    public void setStrokeId(String strokeId) {
        this.strokeId = strokeId;
    }

    private String jsonData;

    // Returns the nested JSON payload used for saved boards.
    public String getJsonData() {
        return jsonData;
    }

    // Sets the nested JSON payload used for saved boards.
    public void setJsonData(String jsonData) {
        this.jsonData = jsonData;
    }
}

