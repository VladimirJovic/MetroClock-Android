package com.echoproduction.metroclock.services

import com.echoproduction.metroclock.models.WorkspaceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

enum class TaskProvider { CLICKUP, ASANA, JIRA }

data class ExternalTask(
    val id: String,
    val name: String,
    val listName: String? = null,
    val folderName: String? = null,
    val spaceName: String? = null,
    val status: String = "",
    val provider: TaskProvider
) {
    val displayName: String get() {
        val parts = mutableListOf<String>()
        if (!spaceName.isNullOrEmpty() && spaceName != "hidden") parts.add(spaceName)
        if (!folderName.isNullOrEmpty() && folderName != "hidden") parts.add(folderName)
        if (!listName.isNullOrEmpty()) parts.add(listName)
        parts.add(name)
        return parts.joinToString(" › ")
    }
}

class TaskService {
    private val client = OkHttpClient()

    private val _tasks = MutableStateFlow<List<ExternalTask>>(emptyList())
    val tasks: StateFlow<List<ExternalTask>> = _tasks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    fun fetchTasks(config: WorkspaceConfig, metroUserId: String) {
        _tasks.value = emptyList()

        // ClickUp
        val token = config.clickupApiToken
        val clickupUserId = config.clickupUserMappings[metroUserId]
        if (token != null && clickupUserId != null) {
            _isAvailable.value = true
            fetchClickUpTasks(token, clickupUserId)
            return
        }

        // Future providers:
        // val asanaToken = config.asanaToken
        // val asanaUserId = config.asanaUserMappings[metroUserId]
        // if (asanaToken != null && asanaUserId != null) {
        //     _isAvailable.value = true
        //     fetchAsanaTasks(asanaToken, asanaUserId)
        //     return
        // }

        _isAvailable.value = false
    }

    // MARK: - ClickUp

    private fun fetchClickUpTasks(token: String, clickupUserId: String) {
        _isLoading.value = true
        val teamsRequest = Request.Builder()
            .url("https://api.clickup.com/api/v2/team")
            .addHeader("Authorization", token)
            .build()

        client.newCall(teamsRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _isLoading.value = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val teams = json.optJSONArray("teams") ?: return
                if (teams.length() == 0) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        _isLoading.value = false
                    }
                    return
                }
                val teamId = teams.getJSONObject(0).getString("id")
                fetchClickUpTasksForTeam(token, teamId, clickupUserId)
            }
        })
    }

    private fun fetchClickUpTasksForTeam(token: String, teamId: String, clickupUserId: String) {
        val url = "https://api.clickup.com/api/v2/team/$teamId/task" +
                "?assignees[]=$clickupUserId" +
                "&include_closed=false" +
                "&subtasks=true" +
                "&page=0" +
                "&include_archived_lists=false"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", token)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _isLoading.value = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val taskArray = json.optJSONArray("tasks") ?: return
                val result = mutableListOf<ExternalTask>()
                for (i in 0 until taskArray.length()) {
                    val task = taskArray.getJSONObject(i)
                    val id = task.optString("id")
                    val name = task.optString("name")
                    if (id.isEmpty() || name.isEmpty()) continue
                    val status = task.optJSONObject("status")?.optString("status") ?: ""
                    val listName = task.optJSONObject("list")?.optString("name")
                    val folderName = task.optJSONObject("folder")?.optString("name")
                    val spaceName = task.optJSONObject("space")?.optString("name")
                    result.add(ExternalTask(
                        id = id,
                        name = name,
                        listName = listName,
                        folderName = folderName,
                        spaceName = spaceName,
                        status = status,
                        provider = TaskProvider.CLICKUP
                    ))
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _tasks.value = result
                    _isLoading.value = false
                }
            }
        })
    }
}