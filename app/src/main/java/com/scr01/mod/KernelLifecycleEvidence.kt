package com.scr01.mod

import android.content.Context

internal data class KernelLoadIdentity(val bootId: String, val sysfsInode: Long) {
    fun encode(): String = "$bootId|$sysfsInode"

    companion object {
        fun parse(value: String): KernelLoadIdentity? {
            val parts = value.trim().split('|')
            if (parts.size != 2 || !Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(parts[0])) return null
            val inode = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return KernelLoadIdentity(parts[0], inode)
        }
    }
}

/** Stores proof obtained by strict readback, never just the module's presence. */
internal interface KernelEvidenceStore {
    fun read(): KernelLoadIdentity?
    fun save(identity: KernelLoadIdentity)
    fun clear()
}

internal class MemoryKernelEvidenceStore : KernelEvidenceStore {
    private var identity: KernelLoadIdentity? = null
    override fun read() = identity
    override fun save(identity: KernelLoadIdentity) { this.identity = identity }
    override fun clear() { identity = null }
}

internal class PersistentKernelEvidenceStore(context: Context) : KernelEvidenceStore {
    private val preferences = context.getSharedPreferences("kernel-strict-evidence-v1", Context.MODE_PRIVATE)
    override fun read() = preferences.getString("verified_identity", null)?.let(KernelLoadIdentity::parse)
    override fun save(identity: KernelLoadIdentity) {
        check(preferences.edit().putString("verified_identity", identity.encode()).commit()) { "Cannot persist kernel verification" }
    }
    override fun clear() { preferences.edit().remove("verified_identity").commit() }
}
