import sys
import re
import os
import time
import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Dict, Any
import numpy as np
from scipy.ndimage import distance_transform_edt
from PIL import Image, ImageDraw
from sklearn.neural_network import MLPClassifier
import joblib

app = FastAPI(title="Whiteboard AI Engine")

# ==========================================
# 1. Content Moderation
# ==========================================

blocked_words = {
    "badword", "badword1", "badword2", "hate", "kill", 
    "stupid", "vulgar", "slang", "spam", "scam", "idiot", "crap"
}

class ModerationRequest(BaseModel):
    text: str

class ModerationResponse(BaseModel):
    moderated_text: str

class SlangRequest(BaseModel):
    word: str

def make_bypass_regex(word: str) -> str:
    leets = {
        'a': '[a@4]',
        'b': '[b8]',
        'c': '[c(]',
        'e': '[e3]',
        'g': '[g9]',
        'i': '[i1!|]',
        'l': '[l1!|]',
        'o': '[o0]',
        's': '[s5$]',
        't': '[t7+]',
        'u': '[uv]'
    }
    parts = []
    for char in word.lower():
        parts.append(leets.get(char, re.escape(char)))
    
    separator_pattern = r"[^a-zA-Z0-9]*"
    word_pattern = separator_pattern.join(parts)
    
    if len(word) >= 4:
        return r"(?i)\b" + word_pattern + r"\w*"
    else:
        return r"(?i)\b" + word_pattern + r"\b"

@app.post("/moderate_text", response_model=ModerationResponse)
def moderate_text(req: ModerationRequest):
    text = req.text
    if not text:
        return ModerationResponse(moderated_text=text)
    
    moderated = text
    for word in blocked_words:
        pattern = re.compile(make_bypass_regex(word))
        matches = list(pattern.finditer(moderated))
        # Replace matches with asterisks
        for match in reversed(matches):
            start, end = match.span()
            mask = "*" * (end - start)
            moderated = moderated[:start] + mask + moderated[end:]
            
    return ModerationResponse(moderated_text=moderated)

@app.post("/add_slang")
def add_slang(req: SlangRequest):
    word = req.word.strip().lower()
    if word:
        blocked_words.add(word)
    return {"status": "success", "blocked_words": list(blocked_words)}


# ==========================================
# 2. Handwritten Math Solver (OCR)
# ==========================================

class Point(BaseModel):
    x: int
    y: int

class StrokeModel(BaseModel):
    points: List[Point]

class MathSolveRequest(BaseModel):
    strokes: List[List[Point]]

class MathSolveResponse(BaseModel):
    expression: str
    result: str
    text_x: int
    text_y: int

# Define standard templates for characters in a 28x28 space (supporting multiple variants)
templates = {
    "0": [[(14, 4), (22, 8), (24, 16), (22, 22), (14, 24), (6, 22), (4, 16), (6, 8), (14, 4)]],
    "0_diamond": [[(4, 14), (14, 4), (24, 14), (14, 24), (4, 14)]],
    "0_narrow": [[(14, 4), (20, 8), (20, 20), (14, 24), (8, 20), (8, 8), (14, 4)]],
    "0_egg": [[(14, 4), (20, 8), (22, 16), (20, 22), (14, 24), (8, 22), (6, 16), (8, 8), (14, 4)]],
    "1": [[(14, 4), (14, 24)]],
    "1_tick": [[(8, 8), (14, 4), (14, 24)]],
    "2": [[(6, 8), (14, 4), (22, 8), (6, 24), (22, 24)]],
    "3": [[(6, 6), (22, 6), (14, 14), (22, 18), (14, 24), (6, 20)]],
    "3_curved": [[(6, 8), (14, 4), (22, 8), (14, 14), (22, 18), (14, 24), (6, 20)]],
    "4": [[(18, 4), (6, 16), (24, 16)], [(18, 4), (18, 24)]],
    "5": [[(8, 6), (22, 6)], [(8, 6), (8, 14), (22, 14), (22, 24), (6, 24)]],
    "5_curved": [[(10, 6), (22, 6)], [(10, 6), (8, 14), (22, 14), (22, 24), (6, 24)]],
    "6": [[(20, 4), (10, 4), (4, 12), (4, 20), (10, 24), (20, 24), (24, 18), (20, 12), (10, 12)]],
    "7": [[(6, 6), (22, 6), (10, 24)]],
    "7_dashed": [[(6, 6), (22, 6), (10, 24)], [(10, 14), (18, 14)]],
    "8": [[(14, 14), (6, 8), (14, 4), (22, 8), (14, 14), (6, 20), (14, 24), (22, 20), (14, 14)]],
    "9": [[(20, 14), (8, 14), (8, 4), (20, 4), (20, 24)]],
    "+": [[(14, 6), (14, 22)], [(6, 14), (22, 14)]],
    "-": [[(6, 14), (22, 14)]],
    "*": [[(14, 6), (14, 22)], [(6, 14), (22, 14)], [(8, 8), (20, 20)], [(20, 8), (8, 20)]],
    "/": [[(6, 22), (22, 6)]],
    "=": [[(6, 10), (22, 10)], [(6, 18), (22, 18)]]
}

def render_strokes(strokes, size=(28, 28), width=2):
    img = Image.new("L", size, 0)
    draw = ImageDraw.Draw(img)
    for stroke in strokes:
        if len(stroke) == 1:
            draw.ellipse([stroke[0][0]-width, stroke[0][1]-width, stroke[0][0]+width, stroke[0][1]+width], fill=255)
        elif len(stroke) > 1:
            draw.line(stroke, fill=255, width=width)
    return np.array(img) / 255.0

# Precompute template images for data generator
template_imgs = {}
for char, pts in templates.items():
    template_imgs[char] = render_strokes(pts, size=(28, 28), width=2)

def generate_synthetic_data():
    X_train = []
    y_train = []
    classes = list(templates.keys())
    variations_per_class = 400
    
    for char in classes:
        pts = templates[char]
        for _ in range(variations_per_class):
            # Apply random aspect-ratio scaling around the center (14, 14)
            scale_x = np.random.uniform(0.75, 1.25)
            scale_y = np.random.uniform(0.75, 1.25)
            
            # Apply random translations
            shift_x = np.random.uniform(-2, 2)
            shift_y = np.random.uniform(-2, 2)
            
            aug_pts = []
            for stroke in pts:
                stroke_angle = np.random.uniform(-15, 15)
                rad = np.radians(stroke_angle)
                cos_a, sin_a = np.cos(rad), np.sin(rad)
                
                # Independent small stroke translation to handle human alignment errors
                stroke_shift_x = np.random.uniform(-2.2, 2.2)
                stroke_shift_y = np.random.uniform(-2.2, 2.2)
                
                aug_stroke = []
                for x, y in stroke:
                    tx = x - 14.0
                    ty = y - 14.0
                    rx = tx * cos_a - ty * sin_a
                    ry = tx * sin_a + ty * cos_a
                    nx = rx * scale_x + 14.0 + shift_x + stroke_shift_x
                    ny = ry * scale_y + 14.0 + shift_y + stroke_shift_y
                    nx = max(0, min(27, nx))
                    ny = max(0, min(27, ny))
                    aug_stroke.append((nx, ny))
                aug_pts.append(aug_stroke)
                
            img_arr = render_strokes(aug_pts, size=(28, 28), width=2)
            img = Image.fromarray((img_arr * 255.0).astype(np.uint8))
            
            # 1. Random rotation (between -30 and 30 degrees to support slanted writing)
            angle = np.random.uniform(-30, 30)
            img_rot = img.rotate(angle, resample=Image.Resampling.BILINEAR)
            
            arr = np.array(img_rot) / 255.0
            arr = (arr > 0.3).astype(np.float32)
            X_train.append(arr.flatten())
            y_train.append(char)
            
    return np.array(X_train), np.array(y_train)

MODEL_PATH = "mlp_math_solver.joblib"

def load_or_train_model():
    if os.path.exists(MODEL_PATH):
        print(f"[AI Engine] Loading pre-trained MLP model from '{MODEL_PATH}'...")
        try:
            return joblib.load(MODEL_PATH)
        except Exception as e:
            print(f"[AI Engine] Error loading model: {e}. Re-training...")
            
    print("[AI Engine] Training MLP Neural Network from synthetic templates...")
    start_time = time.time()
    X, y = generate_synthetic_data()
    
    # High-capacity MLP neural network trained on aspect-ratio augmented dataset
    clf = MLPClassifier(
        hidden_layer_sizes=(100, 50),
        max_iter=300,
        random_state=42,
        early_stopping=False
    )
    clf.fit(X, y)
    
    duration = time.time() - start_time
    print(f"[AI Engine] Training completed in {duration:.2f} seconds.")
    try:
        joblib.dump(clf, MODEL_PATH)
        print(f"[AI Engine] Neural network saved to '{MODEL_PATH}'.")
    except Exception as e:
        print(f"[AI Engine] Error saving model: {e}")
        
    return clf

clf = load_or_train_model()

def safe_eval(expr: str) -> str:
    expr = expr.replace(" ", "")
    # Restrict input string to standard math syntax
    if not re.match(r"^[0-9+\-*/().]+$", expr):
        return "?"
    try:
        # Safe eval using limited builtins
        val = eval(expr, {"__builtins__": None}, {})
        if isinstance(val, (int, float)):
            if isinstance(val, float) and val.is_integer():
                return str(int(val))
            # Limit float decimals
            if isinstance(val, float):
                return f"{val:.4f}".rstrip('0').rstrip('.')
            return str(val)
        return "?"
    except Exception:
        return "?"

@app.post("/solve_math", response_model=MathSolveResponse)
def solve_math(req: MathSolveRequest):
    raw_strokes = req.strokes
    if not raw_strokes:
        return MathSolveResponse(expression="", result="", text_x=0, text_y=0)
    
    # 1. Group strokes horizontally
    # Sort strokes by their min X coordinate
    stroke_infos = []
    for s_idx, stroke in enumerate(raw_strokes):
        if not stroke:
            continue
        xs = [pt.x for pt in stroke]
        ys = [pt.y for pt in stroke]
        stroke_infos.append({
            "idx": s_idx,
            "min_x": min(xs),
            "max_x": max(xs),
            "min_y": min(ys),
            "max_y": max(ys),
            "points": [(pt.x, pt.y) for pt in stroke]
        })
        
    if not stroke_infos:
        return MathSolveResponse(expression="", result="", text_x=0, text_y=0)
    
    # ---- Step 1b: Vertical line clustering ----
    # Cluster strokes into horizontal text lines by Y-center proximity.
    # This prevents strokes from different rows (e.g., "5+5" on row 1 and
    # "1000+1" on row 2) from being merged when they overlap in X.
    strokes_by_center = []
    for s in stroke_infos:
        cy = (s["min_y"] + s["max_y"]) / 2.0
        strokes_by_center.append((cy, s))
    strokes_by_center.sort(key=lambda x: x[0])
    
    text_lines = []
    for cy, s in strokes_by_center:
        placed = False
        for line in text_lines:
            line_centers = [(item["min_y"] + item["max_y"]) / 2.0 for item in line]
            line_avg_cy = sum(line_centers) / len(line_centers)
            line_heights = [item["max_y"] - item["min_y"] for item in line]
            line_avg_h = sum(line_heights) / len(line_heights) if line_heights else 30
            # Group if Y-center is within 0.8x the average stroke height of the line
            if abs(cy - line_avg_cy) < 0.8 * max(line_avg_h, 30):
                line.append(s)
                placed = True
                break
        if not placed:
            text_lines.append([s])
    
    # Merge very small lines (flat strokes like top-bar of "5") into nearby lines
    if len(text_lines) > 1:
        merged = True
        while merged:
            merged = False
            new_lines = []
            skip = set()
            for i in range(len(text_lines)):
                if i in skip:
                    continue
                line_i = text_lines[i]
                # Check if this is a "flat" line (all strokes have very small height)
                all_flat = all((s["max_y"] - s["min_y"]) <= 8 for s in line_i)
                if all_flat and len(text_lines) > 1:
                    # Find the nearest non-flat line by average Y-center distance
                    ci = sum((s["min_y"] + s["max_y"]) / 2.0 for s in line_i) / len(line_i)
                    best_dist = float("inf")
                    best_j = -1
                    for j in range(len(text_lines)):
                        if j == i or j in skip:
                            continue
                        cj = sum((s["min_y"] + s["max_y"]) / 2.0 for s in text_lines[j]) / len(text_lines[j])
                        dist = abs(ci - cj)
                        if dist < best_dist:
                            best_dist = dist
                            best_j = j
                    if best_j >= 0:
                        text_lines[best_j].extend(line_i)
                        skip.add(i)
                        merged = True
                        continue
                new_lines.append(line_i)
            if merged:
                text_lines = [text_lines[i] for i in range(len(text_lines)) if i not in skip]
    
    # Pick the line with the most strokes (the main equation)
    # If multiple lines have the same count, pick the bottom-most (most recent)
    best_line = max(text_lines, key=lambda line: (len(line), max(s["max_y"] for s in line)))
    active_strokes = best_line
    
    # ---- Step 2: Horizontal character segmentation within the chosen line ----
    active_strokes.sort(key=lambda s: s["min_x"])
    
    char_groups = []
    for s in active_strokes:
        if not char_groups:
            char_groups.append([s])
        else:
            g = char_groups[-1]
            g_min_x = min(item["min_x"] for item in g)
            g_max_x = max(item["max_x"] for item in g)
            g_w = g_max_x - g_min_x
            s_w = s["max_x"] - s["min_x"]
            
            # Calculate overlap width (using + 1 to handle zero-width intervals correctly)
            overlap_w = min(g_max_x, s["max_x"]) - max(g_min_x, s["min_x"])
            if overlap_w >= 0:
                overlap_ratio = (overlap_w + 1) / max(min(g_w, s_w) + 1, 1)
            else:
                overlap_ratio = 0
            
            # Group only if there is a significant horizontal overlap (e.g., > 12%)
            if overlap_ratio > 0.12:
                g.append(s)
            else:
                char_groups.append([s])
    
    recognized_chars = []
    last_char_bbox = (0, 0, 0, 0)
    
    # 2. Recognize each character group
    for g in char_groups:
        # Bounding box of character
        g_min_x = min(s["min_x"] for s in g)
        g_max_x = max(s["max_x"] for s in g)
        g_min_y = min(s["min_y"] for s in g)
        g_max_y = max(s["max_y"] for s in g)
        
        w = g_max_x - g_min_x
        h = g_max_y - g_min_y
        
        last_char_bbox = (g_min_x, g_min_y, g_max_x, g_max_y)
        
        # Shift and scale strokes of the character
        char_strokes = []
        for s in g:
            char_strokes.append(s["points"])
            
        # Normalize coordinates
        norm_strokes = []
        max_dim = max(w, h, 1)
        scale = 18.0 / max_dim  # fit inside 18x18
        
        # Center in 28x28
        offset_x = (28.0 - w * scale) / 2.0
        offset_y = (28.0 - h * scale) / 2.0
        
        for stroke in char_strokes:
            norm_stroke = []
            for pt in stroke:
                nx = int((pt[0] - g_min_x) * scale + offset_x)
                ny = int((pt[1] - g_min_y) * scale + offset_y)
                # Keep within bounds
                nx = max(0, min(27, nx))
                ny = max(0, min(27, ny))
                norm_stroke.append((nx, ny))
            norm_strokes.append(norm_stroke)
            
        # Render input character array
        input_arr = render_strokes(norm_strokes, size=(28, 28), width=2)
        
        # Match against MLP Neural Network
        features = input_arr.flatten().reshape(1, -1)
        best_char = clf.predict(features)[0]
        
        # Resolve character prefix (e.g., "3_curved" -> "3")
        resolved_char = best_char.split("_")[0]
        recognized_chars.append(resolved_char)
        
    expression = "".join(recognized_chars)
    
    # 3. Solve expression
    # Extract the math expression up to "=" if it ends with "="
    eval_expr = expression
    if eval_expr.endswith("="):
        eval_expr = eval_expr[:-1]
        
    result = safe_eval(eval_expr)
    
    # 4. Text placement coordinates
    # Place text just to the right of the last character's box
    text_x = last_char_bbox[2] + 15
    text_y = int((last_char_bbox[1] + last_char_bbox[3]) / 2.0) + 7
    
    print(f"Recognized: {expression} | Solved: {result} at ({text_x}, {text_y})")
    
    return MathSolveResponse(
        expression=expression,
        result=result,
        text_x=text_x,
        text_y=text_y
    )
# ==========================================
# 3. Shape Snapping / Normalization (Shape Neural Network)
# ==========================================

shape_templates = {
    "line": [[(4, 14), (24, 14)]],
    "rectangle": [[(6, 6), (22, 6), (22, 22), (6, 22), (6, 6)]],
    "circle": [[(14, 4), (21, 6), (24, 14), (21, 21), (14, 24), (7, 21), (4, 14), (7, 6), (14, 4)]],
    "triangle": [[(14, 5), (23, 21), (5, 21), (14, 5)]]
}

def generate_synthetic_shapes():
    X_train = []
    y_train = []
    classes = list(shape_templates.keys())
    variations_per_class = 300
    
    for char in classes:
        pts = shape_templates[char]
        for _ in range(variations_per_class):
            # Apply random aspect ratio stretching
            scale_x = np.random.uniform(0.7, 1.3)
            scale_y = np.random.uniform(0.7, 1.3)
            
            # Apply random translations
            shift_x = np.random.uniform(-3, 3)
            shift_y = np.random.uniform(-3, 3)
            
            aug_pts = []
            for stroke in pts:
                aug_stroke = []
                for x, y in stroke:
                    nx = (x - 14.0) * scale_x + 14.0 + shift_x
                    ny = (y - 14.0) * scale_y + 14.0 + shift_y
                    nx = max(0, min(27, nx))
                    ny = max(0, min(27, ny))
                    aug_stroke.append((nx, ny))
                aug_pts.append(aug_stroke)
                
            img_arr = render_strokes(aug_pts, size=(28, 28), width=2)
            img = Image.fromarray((img_arr * 255.0).astype(np.uint8))
            
            # Apply random rotation - fully 360 degrees rotation-invariant for shapes!
            angle = np.random.uniform(-180, 180)
            img_rot = img.rotate(angle, resample=Image.Resampling.BILINEAR)
            
            arr = np.array(img_rot) / 255.0
            arr = (arr > 0.3).astype(np.float32)
            X_train.append(arr.flatten())
            y_train.append(char)
            
    return np.array(X_train), np.array(y_train)

SHAPES_MODEL_PATH = "mlp_shape_recognizer.joblib"

def load_or_train_shapes_model():
    if os.path.exists(SHAPES_MODEL_PATH):
        print(f"[AI Engine] Loading pre-trained Shape MLP model from '{SHAPES_MODEL_PATH}'...")
        try:
            return joblib.load(SHAPES_MODEL_PATH)
        except Exception as e:
            print(f"[AI Engine] Error loading shapes model: {e}. Re-training...")
            
    print("[AI Engine] Training Shape MLP Neural Network...")
    start_time = time.time()
    X, y = generate_synthetic_shapes()
    
    clf = MLPClassifier(
        hidden_layer_sizes=(100, 50),
        max_iter=250,
        random_state=42,
        early_stopping=True,
        n_iter_no_change=15
    )
    clf.fit(X, y)
    
    duration = time.time() - start_time
    print(f"[AI Engine] Shape NN training completed in {duration:.2f} seconds.")
    try:
        joblib.dump(clf, SHAPES_MODEL_PATH)
        print(f"[AI Engine] Shape NN saved to '{SHAPES_MODEL_PATH}'.")
    except Exception as e:
        print(f"[AI Engine] Error saving shape model: {e}")
        
    return clf

shape_clf = load_or_train_shapes_model()

class ShapePoint(BaseModel):
    x: int
    y: int

class ShapeRecognizeRequest(BaseModel):
    points: List[ShapePoint]

class ShapeRecognizeResponse(BaseModel):
    shape_type: str

@app.post("/recognize_shape", response_model=ShapeRecognizeResponse)
def recognize_shape(req: ShapeRecognizeRequest):
    pts = req.points
    if not pts or len(pts) < 5:
        return ShapeRecognizeResponse(shape_type="FREEHAND")
        
    xs = [p.x for p in pts]
    ys = [p.y for p in pts]
    w = max(xs) - min(xs)
    h = max(ys) - min(ys)
    
    if w < 12 or h < 12:
        return ShapeRecognizeResponse(shape_type="FREEHAND")
        
    g_min_x = min(xs)
    g_min_y = min(ys)
    max_dim = max(w, h, 1)
    scale = 18.0 / max_dim
    offset_x = (28.0 - w * scale) / 2.0
    offset_y = (28.0 - h * scale) / 2.0
    
    norm_pts = []
    for p in pts:
        nx = int((p.x - g_min_x) * scale + offset_x)
        ny = int((p.y - g_min_y) * scale + offset_y)
        nx = max(0, min(27, nx))
        ny = max(0, min(27, ny))
        norm_pts.append((nx, ny))
        
    input_arr = render_strokes([norm_pts], size=(28, 28), width=2)
    features = input_arr.flatten().reshape(1, -1)
    
    # Predict probabilities for confidence thresholding
    probs = shape_clf.predict_proba(features)[0]
    best_idx = np.argmax(probs)
    best_prob = probs[best_idx]
    best_class = shape_clf.classes_[best_idx]
    
    # Treat low-confidence predictions as standard freehand drawing (scribbles)
    if best_prob < 0.65:
        return ShapeRecognizeResponse(shape_type="FREEHAND")
        
    return ShapeRecognizeResponse(shape_type=best_class.upper())

if __name__ == "__main__":
    print("[AI Engine] Starting FastAPI server on port 8000...")
    uvicorn.run(app, host="127.0.0.1", port=8000)
