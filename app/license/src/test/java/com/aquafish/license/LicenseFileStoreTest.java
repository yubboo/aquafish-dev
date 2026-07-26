package com.aquafish.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesReadsAndDeletesLicenseAtomically() {
        LicenseFileStore store = new LicenseFileStore(
            tempDir.resolve("licenses/platform.license")
        );

        store.save("AQF1.payload.signature");
        assertEquals("AQF1.payload.signature", store.read().orElseThrow());

        store.delete();
        assertFalse(store.read().isPresent());
    }
}
