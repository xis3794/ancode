package com.ancode.app.tools

import com.ancode.app.model.TodoItem
import com.ancode.app.model.TodoOp
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Built-in Do List (Claude Code TodoWrite style).
 * The agent maintains a plan; rendered as tool-call cards in the chat stream.
 */
class TodoTool(
    private val onUpdate: (List<TodoItem>) -> Unit
) : Tool {

    private val items = mutableListOf<TodoItem>()

    override val name = "todo"
    override val description =
        "维护当前任务的待办清单（Do List）。开始任务前先 ADD 规划步骤，每完成一步用 MARK_DONE 更新。" +
            "ADD 添加（可指定 index），UPDATE 修改文案，MARK_DONE 标记完成（index 或 id），CLEAR 清空。"

    override val parametersSpec: Map<String, JsonObject> = mapOf(
        "op" to Schema.string("操作: ADD / UPDATE / MARK_DONE / CLEAR", listOf("ADD", "UPDATE", "MARK_DONE", "CLEAR")),
        "content" to Schema.string("任务内容（ADD/UPDATE 时必填）"),
        "index" to Schema.integer("任务下标（0 开始；ADD 时插入位置，其他操作定位任务）"),
        "id" to Schema.string("任务 id（可替代 index 定位）")
    )

    override val requiredParams = listOf("op")

    override suspend fun execute(args: Map<String, Any?>): String {
        val op = runCatching { TodoOp.valueOf(args["op"]?.toString()?.uppercase() ?: "") }
            .getOrNull() ?: return "错误：op 必须是 ADD/UPDATE/MARK_DONE/CLEAR"
        val content = args["content"]?.toString()
        val index = (args["index"] as? Number)?.toInt()
        val id = args["id"]?.toString()

        return when (op) {
            TodoOp.ADD -> {
                val text = content ?: return "错误：ADD 需要 content"
                val item = TodoItem(UUID.randomUUID().toString().take(8), text)
                if (index != null && index in 0..items.size) items.add(index, item) else items.add(item)
                notifyUpdate()
                "已添加任务 #${items.indexOf(item)}: $text"
            }
            TodoOp.UPDATE -> {
                val target = locate(index, id) ?: return "错误：任务不存在（index=$index id=$id）"
                val text = content ?: return "错误：UPDATE 需要 content"
                val idx = items.indexOf(target)
                items[idx] = target.copy(content = text)
                notifyUpdate()
                "已更新任务 #$idx: $text"
            }
            TodoOp.MARK_DONE -> {
                val target = locate(index, id) ?: return "错误：任务不存在（index=$index id=$id）"
                val idx = items.indexOf(target)
                items[idx] = target.copy(done = !target.done)
                notifyUpdate()
                "任务 #$idx 标记为 ${if (items[idx].done) "完成" else "未完成"}"
            }
            TodoOp.CLEAR -> {
                items.clear()
                notifyUpdate()
                "已清空待办清单"
            }
        }
    }

    private fun locate(index: Int?, id: String?): TodoItem? {
        id?.let { return items.firstOrNull { it.id == id } }
        index?.let { return items.getOrNull(it) }
        return null
    }

    private fun notifyUpdate() {
        onUpdate(items.toList())
    }

    fun current(): List<TodoItem> = items.toList()

    fun restore(list: List<TodoItem>) {
        items.clear()
        items.addAll(list)
        notifyUpdate()
    }
}