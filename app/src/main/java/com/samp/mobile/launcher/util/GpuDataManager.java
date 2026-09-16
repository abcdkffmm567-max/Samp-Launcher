package com.samp.mobile.launcher.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

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
        return renameGpuFiles(root, suffix);
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
        return file.renameTo(destination) ? 1 : 0;
    }
}
