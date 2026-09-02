# Sathi AI v0.2

Personal Android Reply Assistant prototype upgraded with a real Groq API connection.

## Included
- Real Groq Chat Completions API connection
- Model: `openai/gpt-oss-20b`
- Hinglish / Latin Nepali / English / mixed chat understanding
- 5 reply suggestions
- Natural, Funny, Playful, Flirty, Romantic, Caring, Confident and Short style selection
- Relationship selector
- Optional previous-chat context
- Conversation Booster questions
- Copy button
- Why? explanation
- Auto-send remains OFF
- Groq API key entered and saved locally in Settings

## Important security note
Groq's official security guidance says API keys should not be embedded in frontend/client apps and should instead be kept on a trusted backend. This direct API mode is therefore a personal prototype/testing setup only. Do not publish or distribute an APK containing your key. For a public release, add a backend proxy and keep the Groq key server-side.

## Setup
1. Open the project in Android Studio.
2. Let Gradle sync.
3. Run the app on your Android phone/emulator.
4. Open Settings.
5. Paste your Groq API key and tap Save key.
6. Return to Reply Assistant and test with a message.

The app never auto-sends messages.
