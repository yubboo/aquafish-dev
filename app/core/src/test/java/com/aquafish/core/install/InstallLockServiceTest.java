package com.aquafish.core.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallLockServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void writingLockPermanentlyClosesInstallation() {
        InstallLockService service = new InstallLockService(tempDir.resolve("workdir").toString());

        assertFalse(service.status().installed());
        assertTrue(service.status().canInstall());

        service.writeInstallLock("installed=true");

        assertTrue(service.status().installed());
        assertTrue(service.status().locked());
        assertFalse(service.status().canInstall());
    }
}
