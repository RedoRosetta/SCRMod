package com.scr01.mod

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class KernelProtectionControllerTest {
    private val active = "scr01_idc36: patch active wrapper=${ModuleAssetVerifier.expectedWrapper} callsite=${ModuleAssetVerifier.expectedCallsitePatched}"
    private val inactive = "scr01_idc36: patch inactive wrapper=${"00".repeat(32)} callsite=${ModuleAssetVerifier.expectedCallsiteOriginal}"

    private inner class Device {
        var loaded = true
        var presenceAvailable = true
        var activeLine = active
        var inactiveLine = ""
        var activeCount = 1
        var inactiveCount = 0
        var countsAvailable = true
        var unloadExit: Int? = 0
        var unloadTimedOut = false
        var unloadRemovesModule = true
        var unloadRestoresText = true
        var postUnloadPresenceAvailable = true
        var identityAvailable = true
        var bootId = "10cba013-95db-46a7-b3cb-0a1b3a592ff7"
        var inode = 47200L
        val evidence = MemoryKernelEvidenceStore().apply { save(KernelLoadIdentity(bootId, inode)) }
        val commands = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val controller = KernelProtectionController(::execute, evidence, logs::add)

        suspend fun runCommand(command: String, timeout: Long) = execute(command, timeout)

        private suspend fun execute(command: String, timeout: Long): RootCommandResult {
            commands.add(command)
            return when {
                command.contains("/proc/sys/kernel/random/boot_id") -> RootCommandResult(command,
                    if (identityAvailable && loaded) 0 else 1, "$bootId\n$inode", "", false)
                command == "cat /proc/modules" -> RootCommandResult(command, if (presenceAvailable) 0 else 1,
                    if (loaded) "scr01_idc36 7856 0 - Live 0x0" else "", "", false)
                command == "rmmod scr01_idc36" -> {
                    if (unloadExit == 0 && !unloadTimedOut) {
                        if (unloadRemovesModule) loaded = false
                        if (unloadRestoresText) { inactiveLine = inactive; inactiveCount++ }
                        presenceAvailable = postUnloadPresenceAvailable
                    }
                    RootCommandResult(command, unloadExit, "", if (unloadExit != 0) "unload error" else "", unloadTimedOut)
                }
                command.contains("awk") -> RootCommandResult(command, if (countsAvailable) 0 else 1,
                    if (command.contains("patch inactive")) "$inactiveCount" else "$activeCount", "", false)
                command.contains("patch inactive") -> RootCommandResult(command, 0, inactiveLine, "", false)
                command.contains("patch active") -> RootCommandResult(command, 0, activeLine, "", false)
                else -> error("Unexpected command: $command / $timeout")
            }
        }

        suspend fun failAfterLoad(): KernelProtectionResult {
            loaded = false
            activeLine = ""
            activeCount = 0
            return controller.ensureLoaded {
                loaded = true
                RootCommandResult("insmod", 0, "", "", false)
            }
        }
    }

    @Test fun loadedWithVerificationPassAllowsOperation() = runBlocking {
        val device = Device()
        val result = device.controller.ensureLoaded { error("Must not load twice") }
        assertTrue(result.success)
        assertEquals(KernelVerificationState.VERIFIED_LOADED, result.verification)
    }

    @Test fun loadedWithUnavailableVerificationDeniesAndPreservesConfiguration() = runBlocking {
        val device = Device().apply { activeLine = ""; identityAvailable = false }
        val result = device.controller.ensureLoaded { error("Must not load") }
        assertFalse(result.success)
        assertEquals(KernelVerificationState.VERIFICATION_UNAVAILABLE, result.verification)
        assertTrue(device.loaded)
        assertFalse(device.commands.contains("rmmod scr01_idc36"))
    }

    @Test fun verificationMismatchDeniesAndPreservesConfiguration() = runBlocking {
        val device = Device().apply { activeLine = "scr01_idc36: patch active wrapper=wrong" }
        val result = device.controller.ensureLoaded { error("Must not load") }
        assertFalse(result.success)
        assertEquals(KernelVerificationState.VERIFICATION_FAILED, result.verification)
        assertTrue(device.loaded)
    }

    @Test fun absentModuleIsNotVerifiedLoadedAndLoadExitZeroIsInsufficient() = runBlocking {
        val device = Device().apply { loaded = false }
        assertEquals(KernelVerificationState.VERIFIED_NOT_LOADED, device.controller.verify())
        val result = device.controller.ensureLoaded { RootCommandResult("insmod", 0, "", "", false) }
        assertFalse(result.success)
    }

    @Test fun unavailablePresenceCannotBeTreatedAsAbsent() = runBlocking {
        val device = Device().apply { presenceAvailable = false }
        assertFalse(device.controller.ensureLoaded { error("Must not load") }.success)
        assertFalse(device.controller.stop().success)
        assertFalse(device.commands.contains("rmmod scr01_idc36"))
    }

    @Test fun newLoadNeedsFreshVerificationBeforeAllowingOperation() = runBlocking {
        val device = Device().apply { loaded = false; activeCount = 0 }
        val result = device.controller.ensureLoaded {
            device.loaded = true
            device.activeCount++
            RootCommandResult("insmod", 0, "", "", false)
        }
        assertTrue(result.success)
        assertEquals(KernelVerificationState.VERIFIED_LOADED, result.verification)
    }

    @Test fun operationFailureThenUnloadAndRestoreVerificationReportsRollbackSuccess() = runBlocking {
        val device = Device()
        val result = device.failAfterLoad()
        assertFalse(result.success)
        assertEquals(KernelRollbackState.ROLLBACK_SUCCESS, result.rollback?.state)
        assertEquals(KernelVerificationState.VERIFIED_NOT_LOADED, result.rollback?.finalState)
        assertTrue(result.message.contains("加载后 patch 验证失败"))
        assertTrue(result.message.contains("IDC 保护已停止"))
        assertTrue(device.logs.contains("ROLLBACK_ATTEMPTED=YES"))
    }

    @Test fun unloadFailureKeepsOriginalFailureAndReportsRollbackFailed() = runBlocking {
        val device = Device().apply { unloadExit = 1 }
        val result = device.failAfterLoad()
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
        assertEquals(1, result.rollback?.commandResult?.exitCode)
        assertTrue(result.message.contains("加载后 patch 验证失败"))
        assertTrue(result.message.contains("unload error"))
        assertFalse(result.message.contains("已停止"))
    }

    @Test fun successfulUnloadWithModuleStillPresentIsRollbackFailed() = runBlocking {
        val device = Device().apply { unloadRemovesModule = false }
        val result = device.failAfterLoad()
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
        assertEquals(KernelVerificationState.VERIFICATION_FAILED, result.rollback?.finalState)
        assertFalse(result.message.contains("已停止"))
    }

    @Test fun unknownUnloadMustNotReportStopped() = runBlocking {
        val device = Device().apply { unloadExit = null; unloadTimedOut = true }
        val result = device.failAfterLoad()
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
        assertTrue(result.rollback!!.commandResult.timedOut)
        assertFalse(result.message.contains("已停止"))
    }

    @Test fun absentModuleWithoutRestoreProofIsRollbackFailed() = runBlocking {
        val device = Device().apply { unloadRestoresText = false }
        val result = device.failAfterLoad()
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
        assertEquals(KernelVerificationState.VERIFICATION_UNAVAILABLE, result.rollback?.finalState)
        assertFalse(result.message.contains("已停止"))
    }

    @Test fun normalStopAlsoRequiresFreshRestoreProof() = runBlocking {
        val device = Device().apply { unloadRestoresText = false; inactiveLine = inactive }
        val result = device.controller.stop()
        assertFalse(result.success)
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
    }

    @Test fun unavailablePostRollbackStateMustNotReportStopped() = runBlocking {
        val device = Device().apply { postUnloadPresenceAvailable = false }
        val result = device.failAfterLoad()
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
        assertEquals(KernelVerificationState.VERIFICATION_UNAVAILABLE, result.rollback?.finalState)
        assertFalse(result.message.contains("已停止"))
    }

    @Test fun controllerCancellationCannotAbandonFailedLoadRollback() = runBlocking {
        val device = Device().apply { loaded = false; activeLine = ""; activeCount = 0 }
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            device.controller.ensureLoaded {
                device.loaded = true
                job.cancel()
                RootCommandResult("insmod", 0, "", "", false)
            }
        }
        job.start()
        job.join()
        assertFalse(device.loaded)
        assertTrue(device.logs.any { it.contains("ROLLBACK_SUCCESS") })
    }

    @Test fun verifiedLifecycleSurvivesHistoricalLogEvictionWithoutReload() = runBlocking {
        val device = Device().apply { activeLine = "" }
        assertTrue(device.controller.ensureLoaded { error("No reinsertion") }.success)
        assertFalse(device.commands.contains("rmmod scr01_idc36"))
    }

    @Test fun savedProofIsUsableByANewControllerInSameLifecycle() = runBlocking {
        val device = Device().apply { activeLine = "" }
        // Same evidence store models restoring the private record after app restart.
        val next = KernelProtectionController({ command, timeout ->
            device.runCommand(command, timeout)
        }, device.evidence, device.logs::add)
        assertEquals(KernelVerificationState.VERIFIED_LOADED, next.verify())
    }

    @Test fun externalReloadInvalidatesProofEvenIfOldMatchingLogRemains() = runBlocking {
        val device = Device().apply { inode++ }
        assertEquals(KernelVerificationState.VERIFICATION_UNAVAILABLE, device.controller.verify())
        assertNull(device.evidence.read())
    }

    @Test fun rebootInvalidatesProofEvenIfInodeIsReused() = runBlocking {
        val device = Device().apply { bootId = "20cba013-95db-46a7-b3cb-0a1b3a592ff7" }
        assertEquals(KernelVerificationState.VERIFICATION_UNAVAILABLE, device.controller.verify())
        assertNull(device.evidence.read())
    }

    @Test fun legacyLoadWithMissingLogsCanRefreshAndEstablishNewProof() = runBlocking {
        val device = Device().apply { evidence.clear(); activeLine = "" }
        val result = device.controller.ensureLoaded {
            assertFalse(device.loaded)
            device.loaded = true
            device.inode++
            device.activeCount++
            device.activeLine = active
            RootCommandResult("insmod", 0, "", "", false)
        }
        assertTrue(result.success)
        assertTrue(device.commands.contains("rmmod scr01_idc36"))
        device.activeLine = ""
        assertTrue(device.controller.ensureLoaded { error("Already verified") }.success)
    }

    @Test fun legacyRefreshCannotLoadAfterUnconfirmedRollback() = runBlocking {
        val device = Device().apply { evidence.clear(); activeLine = ""; unloadExit = 1 }
        val result = device.controller.ensureLoaded { error("Must not load") }
        assertFalse(result.success)
        assertEquals(KernelRollbackState.ROLLBACK_FAILED, result.rollback?.state)
    }

    @Test fun unavailableLogReaderWithoutProofCannotStartRefresh() = runBlocking {
        val device = Device().apply { evidence.clear(); activeLine = ""; countsAvailable = false }
        assertFalse(device.controller.ensureLoaded { error("Must not load") }.success)
        assertFalse(device.commands.contains("rmmod scr01_idc36"))
    }

    @Test fun invalidIdentityCannotReviveEvidence() {
        assertNull(KernelLoadIdentity.parse("invalid|42"))
        assertNull(KernelLoadIdentity.parse("10cba013-95db-46a7-b3cb-0a1b3a592ff7|0"))
        val identity = KernelLoadIdentity("10cba013-95db-46a7-b3cb-0a1b3a592ff7", 47200)
        assertEquals(identity, KernelLoadIdentity.parse(identity.encode()))
    }
}
