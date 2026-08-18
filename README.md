# AI Augmented Collaborative Whiteboard

## How to Run

Open three terminal windows from the project root and run the following commands:

### 1. Compile
```bash
javac -d out src/network/*.java
```

### 2. Start the Server
```bash
java -cp out network.WhiteboardServer
```

### 3. Start a Client (in a separate terminal)
```bash
java -cp out network.WhiteboardClient
```

> You can run command 3 in multiple terminals to connect additional clients.
