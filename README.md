# AINOTES - AI Study Workspace

AINOTES is an Android study assistant built with Kotlin, Jetpack Compose, MVVM, and Room.

The app now uses a compact single-page workspace design and includes an AI autopilot pipeline that can process uploaded study context and auto-populate subjects, tasks, reminders, notes, quiz checkpoints, and progress updates.

## Product Direction

The goal is to make AI behave like a co-student:

- reads uploaded semester plans and study units
- maps them into structured academic objects
- sets reminders and checkpoints
- proposes quiz moments
- updates and reports progress back to the learner

## What Is Implemented

- Single-page collage workspace UI with floating quick actions
- Upload Context gateway as the primary intake path
- Manual create flows: subjects, tasks, notes, reminders
- AI autopilot flow on upload:
	- stores file metadata
	- captures user intent
	- calls Hugging Face Router (OpenAI-compatible chat completions)
	- falls back to Gemini when HF token is not configured
	- parses strict JSON plan
	- applies actions into Room (subjects/tasks/reminders/notes/quizzes/progress)
	- records an AI report note for traceability
- AlarmManager scheduling for reminders and AI-created quiz alerts
- Progress tracking with logs

## Tech Stack

- Android
- Kotlin
- Jetpack Compose
- MVVM
- Room
- Hugging Face Router API (primary)
- Google Generative Language API (fallback)

## AI Configuration

Do not hardcode API keys in source code.

The app reads these values at build time:

- `HF_TOKEN`
- `HF_MODEL` (optional, defaults to `google/gemma-4-31B-it:novita`)
- `GEMINI_API_KEY`
- `GEMINI_MODEL` (optional fallback, defaults to `gemini-flash-latest`)

Set them via local Gradle properties or environment variables, for example:

```bash
export HF_TOKEN="your_huggingface_token"
export HF_MODEL="google/gemma-4-31B-it:novita"
export GEMINI_API_KEY="optional_fallback_key"
export GEMINI_MODEL="gemini-flash-latest"
```

Then build normally with Gradle.

## Project Structure

- `app/src/main/java/com/ainotes/studyassistant/ai`: AI engine contracts and Hugging Face/Gemini implementations
- `app/src/main/java/com/ainotes/studyassistant/data/local`: Room database, entities, DAOs
- `app/src/main/java/com/ainotes/studyassistant/data/repository`: repository implementation
- `app/src/main/java/com/ainotes/studyassistant/domain`: repository contracts and dashboard model
- `app/src/main/java/com/ainotes/studyassistant/feature/workspace`: compact workspace UI and orchestration ViewModel
- `app/src/main/java/com/ainotes/studyassistant/core`: app container and ViewModel factory
- `app/src/main/java/com/ainotes/studyassistant/notifications`: reminder scheduler and alarm receiver

## Security Note

If an API key was ever exposed in chat, logs, or source history, rotate it immediately and replace it with a new restricted key.