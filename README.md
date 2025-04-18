# 🚀 FastTube

Point. Snap. Summarize.  
FastTube lets you scan YouTube videos with your phone camera and get instant summaries using AI.

## 📱Features

- Detect YouTube videos using YOLO on Android
- Extract video thumbnail text, title, and channel name via OCR
- Send metadata to a Flask backend
- Backend finds video link and fetches transcription
- Use an LLM to generate a clean summary
- View the result directly in the app

## 📂 Project Structure

```
fasttube/
├── android/       # Android app with YOLO & OCR logic
├── backend/       # Flask API for link lookup, transcription, and LLM summary
└── README.md
```

## 🛠️ Setup Instructions

### Android App

- Requires: Android SDK, ML Kit, TensorFlow Lite model (YOLOv8)

### Flask Backend

Requires:
- [Groq API Key](https://console.groq.com/keys) (or adapt the code to use OpenAI, Ollama, etc.)
- [YouTube Data API v3 Key](https://console.cloud.google.com/apis/library/youtube.googleapis.com)

If you don't want to get them, just set `debug` to `False` in the request.

Adjust your IP to the one displayed when running backend.

## 💡 Usage Example

Payload example
```
{
  "thumb": "Did it go wrong?",
  "title": "I jumped off a building.",
  "channel": "Clickbaiter",
  "debug": "True"
}
```

- thumb: isn't actually used, I'd like to use it to better directs the summary, as for example to easily identifying click bait videos.
- title: an actual YouTube video title name
- channel: an actual YouTube channel name
- debug: if set anything differently from "False", will return a placeholder, otherwise, it will actually search for the video transcription and summarize it for you.

Response example:

```
{
  "summary": "Nothing went wrong. He jumped off the building, but opened his parachute and landed safely."
}
```

## 🔒 Security Note

You can't make money from this because this would go against YT TOS. But you can run it locally and have your TV YouTube home screens videos summarized. You can also have fun playing with the code.

## 🧠 Tech Stack

- **Android (Kotlin/Java)** – UI, camera, YOLOv8 TFLite inference, OCR
- **Flask (Python)** – API backend
- **YOLOv8** – Object detection
- **Google ML Kit** – Text recognition
- **YouTube APIs** – Transcription
- **LLM (OpenAI or similar)** – Summarization

## 📸 Screenshots

*Coming soon... (or not)*

## 🧑‍💻 Author

Built to tweak a bunch of different stuff, by [matheuscs](https://github.com/matheuscs).

## 📄 License

MIT License