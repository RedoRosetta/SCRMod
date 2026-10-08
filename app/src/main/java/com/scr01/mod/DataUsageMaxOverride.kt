package com.scr01.mod

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val enabledKey = "enabled"
private const val overrideValueKey = "override_value"
private const val originalValueKey = "original_value"
private const val recoveryPendingKey = "recovery_pending"

const val samsungDataUsageMaxKey = "mhs_data_usage_month_max"
enum class DataUsagePeriod(val label: String, val settingsKey: String, val preferenceName: String) {
    Month("1个月", samsungDataUsageMaxKey, "data_usage_max_override"),
    ThreeDays("3天", "mhs_data_usage_days_max", "data_usage_max_override_three_days"),
}

const val dataUsageMaxMinimum = 1
const val dataUsageMaxMaximum = 2000

data class DataUsageMaxOverrideConfig(
    val enabled: Boolean = false,
    val overrideValue: String? = null,
    val originalValue: String? = null,
    val recoveryPending: Boolean = false,
)

object DataUsageMaxOverridePreferences {
    fun read(context: Context, period: DataUsagePeriod = DataUsagePeriod.Month): DataUsageMaxOverrideConfig {
        val preferences = context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE)
        return DataUsageMaxOverrideConfig(
            enabled = preferences.getBoolean(enabledKey, false),
            overrideValue = preferences.getString(overrideValueKey, null),
            originalValue = preferences.getString(originalValueKey, null),
            recoveryPending = preferences.getBoolean(recoveryPendingKey, false) ||
                (!preferences.getBoolean(enabledKey, false) && preferences.contains(originalValueKey)),
        )
    }

    fun saveEnabled(
        context: Context,
        overrideValue: String,
        originalValue: String,
        period: DataUsagePeriod = DataUsagePeriod.Month,
    ): Boolean = context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE).edit()
        .putBoolean(enabledKey, true)
        .putString(overrideValueKey, overrideValue)
        .putString(originalValueKey, originalValue)
        .putBoolean(recoveryPendingKey, false)
        .commit()

    fun saveOverrideValue(context: Context, overrideValue: String, period: DataUsagePeriod = DataUsagePeriod.Month): Boolean =
        context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE).edit()
            .putString(overrideValueKey, overrideValue)
            .commit()

    fun saveOriginalValue(context: Context, originalValue: String, period: DataUsagePeriod = DataUsagePeriod.Month): Boolean =
        context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE).edit()
            .putString(originalValueKey, originalValue)
            .commit()

    fun prepareOriginalValue(context: Context, originalValue: String, period: DataUsagePeriod = DataUsagePeriod.Month): Boolean =
        saveOriginalValue(context, originalValue, period)

    fun clearOriginalAndDisable(context: Context, period: DataUsagePeriod = DataUsagePeriod.Month): Boolean =
        context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE).edit()
            .putBoolean(enabledKey, false)
            .remove(originalValueKey)
            .putBoolean(recoveryPendingKey, false)
            .commit()

    fun clearPreparedOriginal(context: Context, period: DataUsagePeriod = DataUsagePeriod.Month): Boolean =
        context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE).edit()
            .remove(originalValueKey)
            .putBoolean(enabledKey, false)
            .commit()
}

internal interface DataUsageMaxStore {
    fun read(): DataUsageMaxOverrideConfig
    fun prepare(target: String, original: String): Boolean
    fun completeApply(target: String, original: String): Boolean
    fun prepareRestore(): Boolean
    fun completeRestore(): Boolean
}

private class PreferenceDataUsageMaxStore(private val context: Context, private val period: DataUsagePeriod) : DataUsageMaxStore {
    private val prefs get() = context.getSharedPreferences(period.preferenceName, Context.MODE_PRIVATE)
    override fun read() = DataUsageMaxOverridePreferences.read(context, period)
    override fun prepare(target: String, original: String) = prefs.edit()
        .putString(originalValueKey, original).putString(overrideValueKey, target)
        .putBoolean(recoveryPendingKey, true).commit()
    override fun completeApply(target: String, original: String) =
        DataUsageMaxOverridePreferences.saveEnabled(context, target, original, period)
    override fun prepareRestore() = prefs.edit().putBoolean(recoveryPendingKey, true).commit()
    override fun completeRestore() = DataUsageMaxOverridePreferences.clearOriginalAndDisable(context, period)
}

data class DataUsageMaxOperationResult(
    val success: Boolean,
    val message: String,
    val config: DataUsageMaxOverrideConfig,
    val actualValue: String? = null,
)

data class DataUsageMaxReadResult(
    val success: Boolean,
    val value: String? = null,
    val message: String,
)

object DataUsageMaxValidator {
    fun parseTarget(raw: String): Result<String> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.failure(IllegalArgumentException("请输入 1～2000 GB"))
        if (value.length > 4 || value.any { it !in '0'..'9' }) {
            return Result.failure(IllegalArgumentException("请输入 1～2000 GB 的整数"))
        }
        val integer = value.toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("请输入 1～2000 GB 的整数"))
        if (integer !in dataUsageMaxMinimum..dataUsageMaxMaximum) {
            return Result.failure(IllegalArgumentException("请输入 1～2000 GB"))
        }
        return Result.success(integer.toString())
    }

    fun parseSamsungValue(raw: String): Float? {
        val value = raw.trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
        return value.toFloatOrNull()?.takeIf { it.isFinite() && it in dataUsageMaxMinimum.toFloat()..dataUsageMaxMaximum.toFloat() }
    }

    fun valuesEqual(first: String, second: String): Boolean {
        val firstNumber = first.toFloatOrNull() ?: return false
        val secondNumber = second.toFloatOrNull() ?: return false
        return firstNumber.isFinite() && secondNumber.isFinite() && firstNumber == secondNumber
    }
}

class DataUsageMaxOverrideController internal constructor(
    private val store: DataUsageMaxStore,
    private val executeCommand: suspend (String, Long) -> RootCommandResult,
    private val period: DataUsagePeriod = DataUsagePeriod.Month,
) {
    constructor(context: Context, executor: RootCommandExecutor, period: DataUsagePeriod = DataUsagePeriod.Month) : this(
        PreferenceDataUsageMaxStore(context.applicationContext, period),
        { command, timeout -> executor.execute(command, timeout) },
        period,
    )

    companion object {
        // All App and boot controllers share ownership of the same system setting.
        private val operations = Mutex()
    }

    suspend fun readCurrent(): DataUsageMaxReadResult = operations.withLock {
        if (!checkRoot()) return@withLock DataUsageMaxReadResult(false, message = "未获取 Root 权限")
        readCurrentWithoutRoot()
    }

    suspend fun enableOrUpdate(rawTarget: String): DataUsageMaxOperationResult = transaction {
        val config = store.read()
        val target = DataUsageMaxValidator.parseTarget(rawTarget).getOrElse {
            return@transaction failure(it.message ?: "请输入 1～2000 GB")
        }
        if (config.recoveryPending) return@transaction failure("上次操作待恢复，请先恢复原值")
        if (!checkRoot()) return@transaction failure("未获取 Root 权限")
        val current = readCurrentWithoutRoot()
        if (!current.success || current.value == null) return@transaction failure(current.message)
        if (DataUsageMaxValidator.parseSamsungValue(current.value) == null) return@transaction failure("当前数据上限无效")
        val original = if (config.enabled) config.originalValue else current.value
        if (original == null || DataUsageMaxValidator.parseSamsungValue(original) == null) {
            return@transaction failure("原始数据上限缺失，无法安全更新")
        }
        // Durable intent precedes the device write. Any uncertain outcome retains the baseline.
        if (!store.prepare(target, original)) return@transaction failure("保存恢复记录失败，未写入设备")
        val written = executeCommand("settings put system ${period.settingsKey} $target", 6)
        if (!written.succeeded) return@transaction failure("写入未确认，原值已保留，可恢复原值")
        val verified = readCurrentWithoutRoot()
        if (!verified.success || verified.value == null || !DataUsageMaxValidator.valuesEqual(target, verified.value)) {
            return@transaction failure("写入结果未确认，原值已保留，可恢复原值", verified.value)
        }
        if (!store.completeApply(target, original)) return@transaction failure("设备已写入，状态保存失败，可恢复原值", verified.value)
        success("已设置上限：$target GB", verified.value)
    }

    suspend fun disableAndRestore(): DataUsageMaxOperationResult = transaction { restore() }

    suspend fun restoreFactoryDefault(): DataUsageMaxOperationResult = transaction {
        if (!checkRoot()) return@transaction failure("未获取 Root 权限")
        // Explicit default reset adopts 15 GB as the durable recovery baseline.
        if (!store.prepare("15", "15")) return@transaction failure("保存恢复记录失败，未写入设备")
        val written = executeCommand("settings put system ${period.settingsKey} 15", 6)
        if (!written.succeeded) return@transaction failure("恢复默认未确认，恢复记录已保留")
        val verified = readCurrentWithoutRoot()
        if (!verified.success || verified.value == null || !DataUsageMaxValidator.valuesEqual("15", verified.value)) {
            return@transaction failure("恢复默认结果未确认，恢复记录已保留", verified.value)
        }
        if (!store.completeRestore()) return@transaction failure("已写回默认值，状态保存未确认", verified.value)
        success("已恢复默认：15 GB", verified.value)
    }

    suspend fun reconcile(): DataUsageMaxOperationResult = transaction {
        val config = store.read()
        // An interrupted apply/restore is rolled back before any automatic reapplication.
        if (config.recoveryPending) return@transaction restore()
        if (!config.enabled) return@transaction success("自定义上限未启用", null)
        val target = config.overrideValue?.let { DataUsageMaxValidator.parseTarget(it).getOrNull() }
            ?: return@transaction failure("保存的上限无效，未自动应用")
        val original = config.originalValue
        if (original == null || DataUsageMaxValidator.parseSamsungValue(original) == null) return@transaction failure("原始上限缺失，未自动应用")
        if (!checkRoot()) return@transaction failure("等待 Root 权限恢复")
        val current = readCurrentWithoutRoot()
        if (!current.success || current.value == null) return@transaction failure("无法读取当前上限")
        if (DataUsageMaxValidator.valuesEqual(target, current.value)) return@transaction success("当前上限：$target GB", current.value)
        if (!store.prepare(target, original)) return@transaction failure("保存恢复记录失败，未写入设备")
        val written = executeCommand("settings put system ${period.settingsKey} $target", 6)
        if (!written.succeeded) return@transaction failure("自动应用未确认，可恢复原值")
        val verified = readCurrentWithoutRoot()
        if (!verified.success || verified.value == null || !DataUsageMaxValidator.valuesEqual(target, verified.value)) {
            return@transaction failure("自动应用结果未确认，可恢复原值", verified.value)
        }
        if (!store.completeApply(target, original)) return@transaction failure("状态保存失败，可恢复原值", verified.value)
        success("已恢复自定义上限：$target GB", verified.value)
    }

    private suspend fun restore(): DataUsageMaxOperationResult {
        val config = store.read()
        if (!config.enabled && !config.recoveryPending && config.originalValue == null) return success("自定义上限未启用", null)
        val original = config.originalValue
        if (original == null || DataUsageMaxValidator.parseSamsungValue(original) == null) return failure("原始上限缺失或无效，无法恢复")
        if (!checkRoot()) return failure("恢复失败：未获取 Root 权限")
        if (!store.prepareRestore()) return failure("保存恢复记录失败，未写入设备")
        val written = executeCommand("settings put system ${period.settingsKey} $original", 6)
        if (!written.succeeded) return failure("恢复未确认，恢复记录已保留")
        val verified = readCurrentWithoutRoot()
        if (!verified.success || verified.value == null || !DataUsageMaxValidator.valuesEqual(original, verified.value)) {
            return failure("恢复结果未确认，恢复记录已保留", verified.value)
        }
        if (!store.completeRestore()) return failure("设备已恢复，状态保存未确认", verified.value)
        return success("已恢复原值：$original GB", verified.value)
    }

    private suspend fun <T> transaction(action: suspend () -> T): T = operations.withLock {
        // Once durable intent exists, finish verification despite UI coroutine cancellation.
        withContext(NonCancellable) { action() }
    }

    private suspend fun checkRoot(): Boolean {
        val result = executeCommand("id", 8)
        return result.succeeded && Regex("(?m)(^|\\s)uid=0(?:\\D|$)").containsMatchIn(result.stdout)
    }

    private suspend fun readCurrentWithoutRoot(): DataUsageMaxReadResult {
        val result = executeCommand("settings get system ${period.settingsKey}", 6)
        val value = result.stdout.trim()
        return if (result.succeeded && value.isNotEmpty() && !value.equals("null", true)) {
            DataUsageMaxReadResult(true, value, "读取成功")
        } else DataUsageMaxReadResult(false, message = "读取数据上限失败")
    }

    private fun failure(message: String, actual: String? = null) = DataUsageMaxOperationResult(false, message, store.read(), actual)
    private fun success(message: String, actual: String?) = DataUsageMaxOperationResult(true, message, store.read(), actual)
}
