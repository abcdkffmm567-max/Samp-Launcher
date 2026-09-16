package com.samp.mobile.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class GpuDataManager {
    private static final String PREFS = "infinity_gpu_data";
    private static final String KEY_SUFFIX = "texture_suffix";

    private GpuDataManager() { }

    public static String detectSuffix(String renderer, String extensions) {
        String rendererText = renderer == null ? "" : renderer.toLowerCase();
        String extensionText = extensions == null ? "" : extensions.toLowerCase();

        if (extensionText.contains("gl_img_texture_compression_pvrtc") ||
                rendererText.contains("powervr")) {
            return "pvr";
        }

        if (extensionText.contains("gl_ext_texture_compression_dxt1") ||
                extensionText.contains("gl_ext_texture_compression_s3tc") ||
                extensionText.contains("gl_amd_compressed_atc_texture") ||
                rendererText.contains("adreno")) {
            return "dxt";
        }

        // Mali and most modern Android GPUs support ETC/ETC2.
        return "etc";
    }

    public static int prepare(Context context, String suffix) {
        if (!"etc".equals(suffix) && !"dxt".equals(suffix) && !"pvr".equals(suffix)) {
            return 0;
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_SUFFIX, suffix).apply();

        File root = context.getExternalFilesDir(null);
        if (root == null) return 0;
        int renamed = renameGpuFiles(root, suffix);
        File texdbRoot = new File(root, "texdb");
        renamed += ensureSelectedVariant(texdbRoot, suffix);

        // This GTA build uses the renderer-selected format for the world/UI
        // databases, but its player database is opened as DXT. Keep this
        // database-specific exception instead of forcing every database to DXT.
        renamed += ensureDatabaseVariant(new File(texdbRoot, "player"), "player", "dxt");
        return renamed;
    }

    public static int prepareUsingStoredGpu(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prepare(context, preferences.getString(KEY_SUFFIX, "etc"));
    }

    private static int renameGpuFiles(File file, String suffix) {
        if (file == null || !file.exists()) return 0;

        if (file.isDirectory()) {
            int renamed = 0;
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) renamed += renameGpuFiles(child, suffix);
            }
            return renamed;
        }

        String name = file.getName();
        if (!name.contains(".RENAME.")) return 0;

        File destination = new File(file.getParentFile(),
                name.replace(".RENAME.", "." + suffix + "."));

        // Never overwrite an already prepared texture database.
        if (destination.exists()) return 0;
        return copyVariant(file, destination) ? 1 : 0;
    }

    /**
     * Some community data packs contain only one named variant for databases
     * such as player (for example player.pvr.*), while the GTA driver asks for
     * another supported suffix (for example player.dxt.*). The three files are
     * a database set, so expose the available set under the suffix selected by
     * the current OpenGL driver instead of letting native code dereference a
     * missing .tmb file and crash.
     */
    private static int ensureSelectedVariant(File texdbRoot, String suffix) {
        if (texdbRoot == null || !texdbRoot.isDirectory()) return 0;

        int renamed = 0;
        File[] databaseFolders = texdbRoot.listFiles();
        if (databaseFolders == null) return 0;

        for (File folder : databaseFolders) {
            if (!folder.isDirectory()) continue;
            String database = folder.getName();
            for (String extension : new String[]{"dat", "tmb", "toc"}) {
                File target = new File(folder, database + "." + suffix + "." + extension);
                if (target.exists()) continue;

                File source = firstExisting(folder, database, extension,
                        new String[]{"RENAME", "360", "dxt", "etc", "pvr", "unc"}, suffix);
                if (source != null && copyVariant(source, target)) renamed++;
            }
        }
        return renamed;
    }

    private static int ensureDatabaseVariant(File folder, String database, String suffix) {
        if (folder == null || !folder.isDirectory()) return 0;
        int renamed = 0;
        for (String extension : new String[]{"dat", "tmb", "toc"}) {
            File target = new File(folder, database + "." + suffix + "." + extension);
            if (target.exists()) continue;
            File source = firstExisting(folder, database, extension,
                    new String[]{"RENAME", "360", "dxt", "etc", "pvr", "unc"}, suffix);
            if (source != null && copyVariant(source, target)) renamed++;
        }
        return renamed;
    }

    private static File firstExisting(File folder, String database, String extension,
                                      String[] variants, String excludedVariant) {
        for (String variant : variants) {
            if (variant.equalsIgnoreCase(excludedVariant)) continue;
            File candidate = new File(folder,
                    database + "." + variant + "." + extension);
            if (candidate.exists()) return candidate;
        }
        return null;
    }

    private static boolean copyVariant(File source, File destination) {
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            return destination.length() == source.length();
        } catch (IOException e) {
            if (destination.exists()) destination.delete();
            return false;
        }
    }

    public static String getStoredSuffix(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SUFFIX, "etc");
    }

    /** Returns the first native texture file that would crash on load. */
    public static File findMissingCoreTexture(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) return new File("external-files-unavailable");
        String suffix = getStoredSuffix(context);
        File txd = new File(root, "texdb/txd/txd." + suffix + ".tmb");
        if (!txd.isFile() || txd.length() == 0) return txd;
        File gta3 = new File(root, "texdb/gta3/gta3." + suffix + ".tmb");
        if (!gta3.isFile() || gta3.length() == 0) return gta3;
        File gtaInt = new File(root, "texdb/gta_int/gta_int." + suffix + ".tmb");
        if (!gtaInt.isFile() || gtaInt.length() == 0) return gtaInt;
        File player = new File(root, "texdb/player/player.dxt.tmb");
        if (!player.isFile() || player.length() == 0) return player;
        return null;
    }

    /** Base GTA expansion files required by CdStreamThread. */
    public static File findMissingBaseGameFile(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) return new File("external-files-unavailable");
        File cinfo = new File(root, "CINFO.BIN");
        if (!cinfo.isFile() || cinfo.length() == 0) return cinfo;
        File expansion = new File(root, "GTASAsf10.b");
        if (!expansion.isFile() || expansion.length() == 0) return expansion;
        File mainScm = new File(root, "SAMP/main.scm");
        if (!mainScm.isFile() || mainScm.length() == 0) return mainScm;
        File scriptImg = new File(root, "SAMP/script.img");
        if (!scriptImg.isFile() || scriptImg.length() == 0) return scriptImg;
        return null;
    }
}
