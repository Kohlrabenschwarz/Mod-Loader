package dev.modloader.engine;
import android.os.ParcelFileDescriptor;
import dev.modloader.engine.IProgress;
interface IFileEngine {
    String prepare(in ParcelFileDescriptor zip, String packageName, IProgress progress) = 0;
    String apply(String packageName, String transactionId, boolean overwriteApproved, IProgress progress) = 1;
    String recover(String packageName, IProgress progress) = 2;
    String restore(String packageName, String transactionId, IProgress progress) = 3;
    String managedMods() = 4;
    String storeMod(in ParcelFileDescriptor zip, String id, String legacyTransactions, IProgress progress) = 5;
    String setModActive(String id, boolean active, IProgress progress) = 6;
    String deleteStoredMod(String id, IProgress progress) = 7;
    ParcelFileDescriptor openStoredMod(String id) = 8;
    String stopGame() = 9;
    String warningAction(String id, boolean recover, IProgress progress) = 10;
    ParcelFileDescriptor openManagedMods() = 11;
    String updateMod(in ParcelFileDescriptor zip, String id, String expectedArchiveHash, String manifest, IProgress progress) = 12;
    String overwriteMod(in ParcelFileDescriptor zip, String id, String expectedArchiveHash, String incomingHash, boolean developerMode, String publicationBaseUrl, IProgress progress) = 13;
    String writeDeveloperFiles(String id, String publicationBaseUrl) = 14;
    void destroy() = 16777114;
}
