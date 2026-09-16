package com.samp.mobile.launcher.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
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
        /*File file2 = new File(externalFilesDir, "gta_sa.set");
        if (!file2.exists()) {
            file2.getParentFile().mkdirs();
            copyAsset(context.getAssets(), "gta_sa.set", file2.toString());
        }*/
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
