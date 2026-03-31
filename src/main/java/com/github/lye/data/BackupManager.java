package com.github.lye.data;

import com.github.lye.TradeFlow;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Level;

public class BackupManager {

    private final TradeFlow plugin;
    private final File dataFile;
    private final File backupDir;

    public BackupManager(TradeFlow plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.db");
        this.backupDir = new File(plugin.getDataFolder(), "backups");
    }

    public void performStartupBackup() {
        if (!dataFile.exists()) {
            return; // Rien à sauvegarder
        }

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // Nettoyage des vieux backups (garder les 10 derniers)
        cleanupOldBackups();

        // Création du nouveau backup
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backupFile = new File(backupDir, "data_" + timestamp + ".db.bak");

        try {
            plugin.getLogger().info("[Backup] Création d'une sauvegarde de sécurité : " + backupFile.getName());
            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // Sauvegarder aussi le fichier .p (wal) et .t (transaction log) si ils existent (MapDB artifacts)
            File walFile = new File(plugin.getDataFolder(), "data.db.p");
            if (walFile.exists()) {
                Files.copy(walFile.toPath(), new File(backupDir, "data_" + timestamp + ".db.p.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Backup] Échec de la sauvegarde !", e);
        }
    }

    private void cleanupOldBackups() {
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".db.bak"));
        if (files == null || files.length <= 10) return;

        // Trier par date (modification), plus vieux en premier
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int toDelete = files.length - 10;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                // Try deleting associated .p.bak file too
                File wal = new File(backupDir, files[i].getName().replace(".db.bak", ".db.p.bak"));
                if (wal.exists()) wal.delete();
            }
        }
        plugin.getLogger().info("[Backup] Nettoyage effectué : " + toDelete + " anciennes sauvegardes supprimées.");
    }
}
