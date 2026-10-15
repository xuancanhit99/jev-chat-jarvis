package com.jev.probe.jev

import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Prefs
import com.jev.probe.core.kb.ChatContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The generative route: any OpenAI-compatible `/chat/completions` endpoint.
 * Drafts the 3 candidate replies, and (D stage) summarizes text. Reads
 * replyBaseUrl / replyKey / replyModel from [Prefs].
 */
class ReplyClient(private val prefs: Prefs) {

    /**
     * [count] varied candidate replies (1..3), in the language of the other
     * person's latest message (English chats get English replies). [avoid] holds
     * replies already shown for this chat ("换一组"), which the model must not repeat.
     *
     * @param ctx D-stage knowledge context. When present its background and
     *        history are prepended to the prompt with an instruction to stay
     *        consistent with them and invent nothing beyond them.
     */
    fun draft(
        snapshot: ChatSnapshot,
        relationship: String,
        ctx: ChatContext? = null,
        count: Int = 3,
        avoid: List<String> = emptyList()
    ): List<String> {
        val n = count.coerceIn(1, 3)
        val convo = snapshot.messages.takeLast(10).joinToString("\n") {
            (if (it.side == "me") "Me: " else "Other: ") + it.text
        }
        val sys = "You are a chat reply assistant. Return only a JSON array containing exactly $n candidate reply strings. " +
            (if (n > 1) "Use distinct strategies, such as acknowledgment, a concrete action or commitment, and a brief low-key reply. "
             else "Give the safest, most natural reply. ") +
            "Use the same language as the latest message from the other person, including Vietnamese, English or Chinese. " +
            "Keep each reply natural and concise: at most 40 Chinese characters or 30 words in a language that uses spaces. Do not explain anything outside the JSON array."
        val user = knowledgeBlock(relationship, ctx) +
            "Relationship: $relationship\n\nRecent conversation:\n$convo\n\n" +
            (if (avoid.isEmpty()) "" else
                "These replies have already been shown. Do not repeat or merely rephrase them:\n" + avoid.joinToString("\n") { "- $it" } + "\n\n") +
            "Return $n candidate replies."
        return parseN(chat(sys, user, temperature = if (avoid.isEmpty()) 0.8 else 0.95), n)
    }

    /** The background + history preamble; empty string when there is no context. */
    private fun knowledgeBlock(relationship: String, ctx: ChatContext?): String {
        ctx ?: return ""
        val background = ctx.background(relationship)
        val history = ctx.history
        if (background.isBlank() && history.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("The following is background and knowledge about the user and the other person. Keep replies consistent with it, ")
            .append("and do not invent facts that are not present.\n")
        if (background.isNotBlank()) sb.append(background).append('\n')
        if (history.isNotEmpty()) {
            sb.append("\nEarlier chat history (newer messages are lower):\n")
            history.takeLast(prefs.contextHistoryCount.coerceIn(0, 100)).forEach {
                sb.append(if (it.side == "me") "Me: " else "Other: ").append(it.text).append('\n')
            }
        }
        sb.append('\n')
        return sb.toString()
    }

    /**
     * One plain chat round trip for the settings connectivity test. Deliberately
     * NOT [summarize]: the test should exercise the ordinary path, not whatever
     * the summary prompt happens to be.
     */
    fun ping(): String =
        chat("You are a connectivity test assistant. Follow the request exactly and do not explain.", "Reply with exactly: received", temperature = 0.0).trim()

    /** Condense a block of text (used by the D-stage contact auto-summary). */
    fun summarize(text: String): String {
        if (text.isBlank()) return ""
        val sys = "Summarize this chat in third person in its original language, using at most 120 words. " +
            "Keep only facts, preferences, commitments and tasks. Do not comment or invent. Output only the summary."
        return chat(sys, text, temperature = 0.2).trim()
    }

    /** One chat-completions round trip; returns the assistant message content. */
    private fun chat(system: String, user: String, temperature: Double): String {
        val url = prefs.replyEndpoint()
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", prefs.replyModel)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("stream", false)
        val resp = HttpJson.post(url, prefs.effectiveReplyKey(), body, Route.REPLY, HttpJson.headersFor(url))
        return resp.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
    }

    /** Up to [n] non-blank replies; fewer when the model gave fewer (never padded with filler). */
    private fun parseN(content: String, n: Int): List<String> {
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        if (start >= 0 && end > start) {
            try {
                val arr = JSONArray(content.substring(start, end + 1))
                val out = ArrayList<String>()
                for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
                if (out.isNotEmpty()) return out.take(n)
            } catch (_: Exception) { }
        }
        // Fallback: split lines.
        return content.split("\n").map { it.trim().trimStart('-', '*', '1', '2', '3', '.', ' ', '"').trimEnd('"', ',') }
            .filter { it.isNotBlank() && it != "[" && it != "]" }
            .take(n)
    }
}
