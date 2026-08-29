# AI-Augmented Collaborative Whiteboard

## Overview

The AI-Augmented Collaborative Whiteboard is a real-time, multi-user drawing application built with Java Swing and Socket Programming. It allows multiple users to simultaneously draw, write text, and collaborate on a shared digital canvas over a TCP/IP network. The system follows a client-server architecture where a central server manages all connected clients and broadcasts drawing actions in real time so every participant sees updates instantly. The project integrates AI-inspired features such as automatic shape normalization — which recognizes rough hand-drawn sketches and converts them into clean geometric shapes like circles, rectangles, triangles, and straight lines — and an automated content moderation module that detects and censors inappropriate or vulgar text before it appears on the shared canvas.

## Objectives

The primary objective of this project is to develop a collaborative whiteboard platform that enables real-time multi-user interaction over a network using Java's Socket API and multithreaded server architecture. The system aims to enhance traditional whiteboard applications by incorporating AI-driven features such as shape recognition and text content moderation, making the collaboration experience smarter and safer. It also serves as a practical implementation of core computer networking concepts including TCP socket communication, multithreading, and client-server broadcasting patterns.

## How to Run

Open three terminal windows from the project root and run the following commands:

### 1. Compile
```bash
 javac -cp "lib/*" -d out src/network/*.java 
```

### 2. Start the Server
```bash
 java -cp "out;lib/*" network.WhiteboardServer
```

### 3. Start a Client (in a separate terminal)
```bash
 java -cp "out;lib/*" network.WhiteboardClient 
```

> You can run command 3 in multiple terminals to connect additional clients.

## Features

- **Real-Time Collaborative Drawing** — Multiple users can draw simultaneously on a shared canvas; every stroke is broadcast to all connected clients instantly via TCP sockets.
- **AI-Powered Shape Normalization** — Hand-drawn sketches are automatically recognized and converted into clean vector shapes (lines, rectangles, circles, triangles) using geometric heuristic algorithms.
- **Automated Content Moderation** — An NLP-style text-filtering module scans user-typed text for vulgar, offensive, or inappropriate words and replaces them with asterisk masks before rendering.
- **Dynamic Slang Blocking** — Users can add custom slang words to the moderation dictionary at runtime, and the new rule is instantly synced to all connected clients.
- **Collaborative Text Placement** — Users can place styled text on the canvas at any position with configurable font sizes (Small, Medium, Large, Huge), and text is synchronized across all clients.
- **Drag-and-Drop Text Repositioning** — Placed text elements can be clicked and dragged to new positions, with movement updates broadcast to all collaborators in real time.
- **Undo Functionality** — Users can undo their last drawing stroke or text action, and the undo operation is synchronized across the network so all clients stay consistent.
- **Multi-Color Drawing** — A toolbar provides preset color options (Black, Red, Blue, Green, Orange) for quick color switching during drawing and text placement.
- **Adjustable Stroke Width** — Users can set line thickness from 1 to 20 pixels using a spinner control, allowing both fine detail work and bold strokes.
- **Eraser Tool** — Draws in the background color (white) to erase portions of the canvas without clearing everything.
- **Clear All Canvas** — A single button clears the entire canvas for all connected users simultaneously.
- **Toggle Grid Background** — A dotted grid overlay can be toggled on or off to assist with alignment and drawing precision.
- **Status Bar** — A real-time status bar displays connection state, user join/leave notifications, and active tool information.


## Project Architecture

```

                 SERVER
                   |
          WhiteboardServer
                   |
        ---------------------
        |         |         |
        ↓         ↓         ↓
 ClientHandler ClientHandler ClientHandler
        |         |         |
        ↓         ↓         ↓
     Client 1  Client 2  Client 3
        |         |         |
        ↓         ↓         ↓
 Whiteboard  Whiteboard  Whiteboard
   Panel       Panel       Panel
```


## Advantages

- **No External Dependencies** — Built entirely with Java's standard library (Swing, AWT, `java.net`), requiring no third-party frameworks, databases, or build tools to compile and run.
- **Lightweight & Portable** — Runs on any system with a Java Runtime Environment (JRE), making it cross-platform compatible across Windows, macOS, and Linux.
- **True Real-Time Synchronization** — Uses TCP socket streaming so drawing strokes appear on remote clients as the user drags the mouse, not just after the stroke is completed.
- **Thread-Safe Architecture** — Employs `CopyOnWriteArrayList`, synchronized collections, and synchronized methods to prevent concurrency issues when multiple clients interact simultaneously.
- **Scalable Multi-Client Support** — The server spawns a dedicated `ClientHandler` thread per client, allowing an unlimited number of users to join the same whiteboard session.
- **Graceful Connection Management** — Handles client disconnection, server shutdown signals, and network errors cleanly without crashing the application.
- **Modern Dark-Themed UI** — Features a sleek, professional toolbar with hover effects, rounded color swatches, and a slate-toned dark theme for comfortable use.

## Limitations

- **No Persistent Storage** — Drawing data exists only in memory; all work is lost when the server or clients are closed since there is no save/load or database integration.
- **Localhost Only by Default** — The server and clients are configured for `localhost`; connecting over a LAN or the internet requires manually changing the `SERVER_HOST` constant and handling firewall/NAT configurations.
- **No User Authentication** — There is no login system or user identity management; anyone who can reach the server port can connect and draw anonymously.
- **Basic Content Moderation** — The text filter relies on a static keyword dictionary with regex matching, which can be bypassed by creative misspellings or obfuscation techniques.
- **No Layer or Object Selection** — Drawing strokes cannot be individually selected, moved, or edited after being placed; only a global undo or full canvas clear is available.
- **Limited Undo Scope** — Undo removes the last action globally (not per-user), which can lead to unintended removal of another user's work in a multi-client session.
- **No Export Functionality** — There is no option to export the whiteboard content as an image (PNG, JPEG) or document (PDF) for sharing outside the application.

## Future Scope

- **Cloud Deployment & Remote Access** — Deploy the server on a cloud platform to enable collaboration over the internet without manual IP configuration.
- **User Authentication & Roles** — Implement a login system with role-based permissions (admin, editor, viewer) to control who can draw, moderate, or manage sessions.
- **Session Persistence & Save/Load** — Add database or file-based storage to save whiteboard sessions and allow users to resume previous work.
- **Image & File Import** — Allow users to drag-and-drop images, PDFs, or other media onto the canvas for annotation and discussion.
- **Export to Image/PDF** — Enable exporting the canvas as PNG, JPEG, or PDF for offline sharing and documentation.
- **AI-Enhanced Moderation** — Upgrade the content filter with machine learning–based NLP models for better detection of obfuscated slang, context-aware filtering, and multilingual support.
- **Voice & Video Chat Integration** — Add real-time audio/video communication alongside the whiteboard for a more complete collaboration experience.
- **Mobile & Web Client** — Develop browser-based (WebSocket) or mobile (Android/iOS) clients to expand accessibility beyond desktop Java applications.
- **Version History & Playback** — Record drawing actions chronologically to allow rewinding and replaying the whiteboard session like a timeline.
- **Individual Object Manipulation** — Enable selecting, resizing, rotating, and deleting individual strokes or shapes after they have been placed.

## Tech Stack

| Component         | Technology                         |
|-------------------|------------------------------------|
| Language          | Java (JDK 8+)                      |
| GUI Framework     | Java Swing + AWT + Graphics2D      |
| Networking        | Java Socket API (`java.net`)       |
| Concurrency       | Java Threads + `CopyOnWriteArrayList` |
| Shape Recognition | Geometric Heuristic Algorithms     |
| Text Moderation   | Regex-Based NLP Filtering          |

## Project Structure

```
AI-Augmented-Collaborative-Whiteboard/
├── src/
│   └── network/
│       ├── WhiteboardServer.java      # Central TCP server — accepts clients and broadcasts messages
│       ├── WhiteboardClient.java      # Client GUI application — connects to server and renders canvas
│       ├── WhiteboardPanel.java       # Swing drawing canvas — handles strokes, shapes, text, and painting
│       ├── ClientHandler.java         # Per-client server thread — manages individual client communication
│       ├── Connection.java            # Socket wrapper — encapsulates streams for sending/receiving
│       ├── ShapeNormalizer.java       # AI shape recognition — classifies freehand input into vector shapes
│       └── ContentModerator.java      # Text moderation — filters offensive words using regex dictionary
├── out/                               # Compiled .class files
└── README.md
```
