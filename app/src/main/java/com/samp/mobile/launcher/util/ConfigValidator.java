package com.samp.mobile.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConfigValidator {
    public static void validateConfigFiles(Context context) {
        File externalFilesDir = context.getExternalFilesDir(null);
        File file = new File(externalFilesDir, "SAMP/settings.ini");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            copyAsset(context.getAssets(), "settings.ini", file.toString());
        }
        // The native game expects these APK assets in the app's external files
        // directory. Extract them once so Android 10-14 can load them reliably.
        copyAssetTree(context.getAssets(), "Text", new File(externalFilesDir, "Text"));
        copyAssetTree(context.getAssets(), "Textures", new File(externalFilesDir, "Textures"));
        copyAssetTree(context.getAssets(), "Fonts", new File(externalFilesDir, "SAMP/fonts"));

        // GTA reads these files directly from the external-files root. Keeping
        // them only inside the APK makes NVFOpen return null later in CGame::Init1.
        copyAssetIfMissing(context.getAssets(), "scache.txt",
                new File(externalFilesDir, "scache.txt"));
        copyAssetIfMissing(context.getAssets(), "scache_small.txt",
                new File(externalFilesDir, "scache_small.txt"));
        copyAssetIfMissing(context.getAssets(), "scache_small_low.txt",
                new File(externalFilesDir, "scache_small_low.txt"));

        // If Android allows access, import the two base GTA files from the
        // original Rockstar installation. On newer Android versions scoped
        // storage can block this; the connect screen will then name the file
        // the user must copy manually.
        File originalRoot = new File("/storage/emulated/0/Android/data/com.rockstargames.gtasa/files");
        copyFileIfMissing(new File(originalRoot, "CINFO.BIN"),
                new File(externalFilesDir, "CINFO.BIN"));
        copyFileIfMissing(new File(originalRoot, "GTASAsf10.b"),
                new File(externalFilesDir, "GTASAsf10.b"));

        // Community texture packs normally call their index <database>.txt,
        // while this 64-bit GTA build opens <database>.ini from the root. Make
        // a non-destructive compatibility copy for every known database.
        for (String database : new String[]{"gta3", "gta_int", "txd", "player", "samp"}) {
            File source = new File(externalFilesDir,
                    "texdb/" + database + "/" + database + ".txt");
            File destination = new File(externalFilesDir, database + ".ini");
            copyFileIfMissing(source, destination);
        }

        removeMissingCutsceneArchiveEntry(externalFilesDir);
        /*File file2 = new File(externalFilesDir, "gta_sa.set");
        if (!file2.exists()) {
            file2.getParentFile().mkdirs();
            copyAsset(context.getAssets(), "gta_sa.set", file2.toString());
        }*/
    }

    /**
     * Some modified packs reference TEXDB/CUTSCENE.IMG in gta.dat but do not
     * include that optional archive. GTA's streaming worker later seeks on the
     * null file handle and crashes. Remove only that stale IMG entry and keep a
     * one-time backup beside gta.dat.
     */
    private static void removeMissingCutsceneArchiveEntry(File root) {
        File cutsceneUpper = new File(root, "TEXDB/CUTSCENE.IMG");
        File cutsceneLower = new File(root, "texdb/cutscene.img");
        if (cutsceneUpper.isFile() || cutsceneLower.isFile()) return;

        File gtaDat = new File(root, "SAMP/gta.dat");
        if (!gtaDat.isFile()) gtaDat = new File(root, "data/gta.dat");
        if (!gtaDat.isFile()) return;

        StringBuilder filtered = new StringBuilder();
        boolean changed = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(gtaDat))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toUpperCase().contains("CUTSCENE.IMG")) {
                    changed = true;
                    continue;
                }
                filtered.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (!changed) return;
        File backup = new File(gtaDat.getParentFile(), "gta.dat.infinity.bak");
        copyFileIfMissing(gtaDat, backup);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(gtaDat, false))) {
            writer.write(filtered.toString());
        } catch (IOException e) {
            e.printStackTrace();
            if (backup.isFile()) copyFileReplacing(backup, gtaDat);
        }
    }

    private static void copyFileReplacing(File source, File destination) {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination, false)) {
            copyFile(input, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copyAssetIfMissing(AssetManager assets, String asset, File destination) {
        if (destination.exists() && destination.length() > 0) return;
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        copyAsset(assets, asset, destination.toString());
    }

    private static void copyFileIfMissing(File source, File destination) {
        if (!source.isFile() || source.length() == 0 ||
                (destination.exists() && destination.length() > 0)) return;
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copyFile(input, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void copyAssetTree(AssetManager assetManager, String assetPath, File output) {
        try {
            String[] children = assetManager.list(assetPath);
            if (children == null || children.length == 0) {
                if (!output.exists()) {
                    File parent = output.getParentFile();
                    if (parent != null) parent.mkdirs();
                    copyAsset(assetManager, assetPath, output.toString());
                }
                return;
            }

            output.mkdirs();
            for (String child : children) {
                copyAssetTree(assetManager, assetPath + "/" + child, new File(output, child));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean copyAsset(AssetManager assetManager, String str, String str2) {
        try {
            InputStream open = assetManager.open(str);
            new File(str2).createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(str2);
            copyFile(open, fileOutputStream);
            open.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    static void copyFile(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[1024];
        while (true) {
            int read = inputStream.read(bArr);
            if (read != -1) {
                outputStream.write(bArr, 0, read);
            } else {
                return;
            }
        }
    }
}
