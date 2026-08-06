package com.ancode.app.llm

/** Connection settings for an OpenAI-compatible chat completion endpoint. */
data class LlmConfig(
    val baseUrl: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val temperature: Double? = 0.3,
    val maxTokens: Int? = 8192
) {
    val chatEndpoint: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        val PRESETS = listOf(
            LlmConfig("https://api.deepseek.com", "", "deepseek-chat"),
            LlmConfig("https://api.openai.com/v1", "", "gpt-4o-mini"),
            LlmConfig("https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen-plus"),
            LlmConfig("https://open.bigmodel.cn/api/paas/v4", "", "glm-4-flash"),
            LlmConfig("http://10.0.2.2:11434/v1", "", "llama3.1") // local Ollama (emulator)
        )
    }
}