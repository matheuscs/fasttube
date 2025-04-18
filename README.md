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

You will need to use a `tflite` file to identify the YT videos. I shared mine in Releases page, but if your layout is different you'll probably need to generate your own. Add them to the `assets` folder in Android project.

### Flask Backend

Requires:
- [Groq API Key](https://console.groq.com/keys) (or adapt the code to use OpenAI, Ollama, etc.)
- [YouTube Data API v3 Key](https://console.cloud.google.com/apis/library/youtube.googleapis.com)

If you don't want to get them, just set `debug` to `True` in the request.

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

## 🛣️ Roadmap

- Improve UI (a lot!)
- Improve prompt (it is not that useful now). Ask a very short summary and a longer one, perhaps. Ask to answer straight to the point if there is a question in the thumb or title.
- Setttings UI. So you don't need to recompile if your IP changes. Language selection, etc
- Database? So you don't need to call API again if already done before.
- RAG? Every time a video is summarized, add some context to that channel, so future summaries will have more knowledge about that specific channel.
- Feedback UI? Would make sense if were deployed in production.
- Let user make following up questions after summary is provided.

## 📸 Screenshots
<p float="left">
  <a href="https://github.com/user-attachments/assets/4ee63baf-1dc8-44d3-a80d-ed867751822e">
    <img src="https://github.com/user-attachments/assets/4ee63baf-1dc8-44d3-a80d-ed867751822e" width="255" style="vertical-align: top;"/>
  </a>
  <a href="https://github.com/user-attachments/assets/94e6e0e0-ea2f-4896-8df9-235486baf6ee">
    <img src="https://github.com/user-attachments/assets/94e6e0e0-ea2f-4896-8df9-235486baf6ee" width="121" style="vertical-align: top; margin-left: 10px;"/>
  </a>
</p>



## 🧑‍💻 Author

Built to tweak a bunch of different stuff, by [matheuscs](https://github.com/matheuscs).

## 📄 License

MIT License