package com.ainotes.studyassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GeminiStudyAiEngine(
    private val apiKey: String,
    private val model: String
) : StudyAiEngine {

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun generatePlan(input: StudyAiInput): AiStudyPlanResult = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext fallbackResult(
                summary = "AI autopilot is off. Add GEMINI_API_KEY to enable auto-planning.",
                raw = null
            )
        }

        try {
            val endpoint = buildEndpoint(model, apiKey)
            val requestBody = buildRequestBody(input)
            val response = postJson(endpoint, requestBody)
            if (response.code !in 200..299) {
                return@withContext fallbackResult(
                    summary = "AI request failed with HTTP ${response.code}. File saved without autoplan.",
                    raw = response.body
                )
            }

            val generatedText = extractCandidateText(response.body)
            if (generatedText.isBlank()) {
                return@withContext fallbackResult(
                    summary = "AI returned an empty response. File saved without autoplan.",
                    raw = response.body
                )
            }

            val payload = extractJsonPayload(generatedText)
            val plan = parsePlan(payload)
            val summary = payload.optString("summary").ifBlank {
                "AI organized your workspace from ${input.fileName}."
            }
            AiStudyPlanResult(
                summary = summary,
                plan = plan,
                rawResponse = generatedText,
                isFallback = false
            )
        } catch (error: Exception) {
            fallbackResult(
                summary = "AI autopilot unavailable: ${error.message ?: "Unknown error"}",
                raw = null
            )
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun buildEndpoint(model: String, key: String): String {
        val encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8.name())
        return "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$key"
    }

    private fun buildRequestBody(input: StudyAiInput): String {
        val prompt = buildPrompt(input)
        val root = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("text", prompt)
                                }
                            )
                        )
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.15)
                    put("responseMimeType", "application/json")
                }
            )
        }
        return root.toString()
    }

    private fun buildPrompt(input: StudyAiInput): String {
        val subjectsContext = input.existingSubjects.joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- none" }
        val tasksContext = input.openTasks.joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- none" }
        val deadlinesContext = input.upcomingDeadlines.joinToString(separator = "\n") { "- $it" }
            .ifBlank { "- none" }

        return """
You are an autonomous study copilot that organizes a learner's semester from uploaded material.

Return ONLY valid JSON. No markdown. No prose outside JSON.

Use this exact schema:
{
  "summary": "short report to learner",
  "subjects": [{"name":"", "description":""}],
  "tasks": [{"title":"", "description":"", "subjectName":"", "dueAtEpochMillis": 0, "priority": 1, "progressPercent": 0}],
  "reminders": [{"title":"", "message":"", "subjectName":"", "taskTitle":"", "triggerAtEpochMillis": 0}],
  "notes": [{"title":"", "content":"", "subjectName":""}],
  "quizzes": [{"title":"", "description":"", "subjectName":"", "scheduledAtEpochMillis": 0}],
  "progressUpdates": [{"taskTitle":"", "progressPercent": 0, "note":""}]
}

Rules:
- Prefer realistic semester planning.
- If dates are unknown, infer useful near-term milestones.
- Keep priorities between 1 and 3.
- Keep progressPercent between 0 and 100.
- Keep summary concise and action-oriented.

Uploaded file:
- name: ${input.fileName}
- mimeType: ${input.mimeType}

Learner intent:
${input.intentText}

Current subjects:
$subjectsContext

Open tasks:
$tasksContext

Upcoming deadlines:
$deadlinesContext
""".trimIndent()
    }

    private fun postJson(endpoint: String, jsonBody: String): HttpResponse {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.doOutput = true

        connection.outputStream.use { output ->
            output.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(code = code, body = body)
    }

    private fun extractCandidateText(responseBody: String): String {
        val response = JSONObject(responseBody)
        val candidates = response.optJSONArray("candidates") ?: return ""

        for (candidateIndex in 0 until candidates.length()) {
            val content = candidates.optJSONObject(candidateIndex)?.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (partIndex in 0 until parts.length()) {
                val text = parts.optJSONObject(partIndex)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    return text
                }
            }
        }

        return ""
    }

    private fun extractJsonPayload(generatedText: String): JSONObject {
        val stripped = generatedText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (stripped.startsWith("{") && stripped.endsWith("}")) {
            return JSONObject(stripped)
        }

        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return JSONObject(stripped.substring(start, end + 1))
        }

        throw IllegalArgumentException("No JSON object found in AI response")
    }

    private fun parsePlan(payload: JSONObject): AiStudyPlan {
        return AiStudyPlan(
            subjects = parseSubjects(payload.optJSONArray("subjects")),
            tasks = parseTasks(payload.optJSONArray("tasks")),
            reminders = parseReminders(payload.optJSONArray("reminders")),
            notes = parseNotes(payload.optJSONArray("notes")),
            quizzes = parseQuizzes(payload.optJSONArray("quizzes")),
            progressUpdates = parseProgressUpdates(payload.optJSONArray("progressUpdates"))
        )
    }

    private fun parseSubjects(array: JSONArray?): List<AiSubjectPlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiSubjectPlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            result += AiSubjectPlan(
                name = name,
                description = item.optString("description").trim()
            )
        }
        return result
    }

    private fun parseTasks(array: JSONArray?): List<AiTaskPlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiTaskPlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            result += AiTaskPlan(
                title = title,
                description = item.optString("description").trim(),
                subjectName = item.optString("subjectName").trim().ifBlank { null },
                dueAtEpochMillis = parseEpoch(item.opt("dueAtEpochMillis")),
                priority = item.optInt("priority", 2).coerceIn(1, 3),
                progressPercent = item.optInt("progressPercent", -1).takeIf { it >= 0 }?.coerceIn(0, 100)
            )
        }
        return result
    }

    private fun parseReminders(array: JSONArray?): List<AiReminderPlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiReminderPlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            result += AiReminderPlan(
                title = title,
                message = item.optString("message").trim(),
                subjectName = item.optString("subjectName").trim().ifBlank { null },
                taskTitle = item.optString("taskTitle").trim().ifBlank { null },
                triggerAtEpochMillis = parseEpoch(item.opt("triggerAtEpochMillis"))
            )
        }
        return result
    }

    private fun parseNotes(array: JSONArray?): List<AiNotePlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiNotePlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val content = item.optString("content").trim()
            if (title.isBlank() || content.isBlank()) continue
            result += AiNotePlan(
                title = title,
                content = content,
                subjectName = item.optString("subjectName").trim().ifBlank { null }
            )
        }
        return result
    }

    private fun parseQuizzes(array: JSONArray?): List<AiQuizPlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiQuizPlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            result += AiQuizPlan(
                title = title,
                description = item.optString("description").trim(),
                subjectName = item.optString("subjectName").trim().ifBlank { null },
                scheduledAtEpochMillis = parseEpoch(item.opt("scheduledAtEpochMillis"))
            )
        }
        return result
    }

    private fun parseProgressUpdates(array: JSONArray?): List<AiProgressPlan> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiProgressPlan>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val taskTitle = item.optString("taskTitle").trim()
            if (taskTitle.isBlank()) continue
            result += AiProgressPlan(
                taskTitle = taskTitle,
                progressPercent = item.optInt("progressPercent", 0).coerceIn(0, 100),
                note = item.optString("note").trim()
            )
        }
        return result
    }

    private fun parseEpoch(raw: Any?): Long? {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isBlank()) return null
                trimmed.toLongOrNull()
                    ?: runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
                    ?: runCatching {
                        LocalDate.parse(trimmed)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
            }
            else -> null
        }
    }

    private fun fallbackResult(summary: String, raw: String?): AiStudyPlanResult {
        return AiStudyPlanResult(
            summary = summary,
            plan = AiStudyPlan(),
            rawResponse = raw,
            isFallback = true
        )
    }
}
