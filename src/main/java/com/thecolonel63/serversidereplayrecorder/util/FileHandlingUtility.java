package com.thecolonel63.serversidereplayrecorder.util;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileHandlingUtility {

    private static final int BUFFER_SIZE = 4096;

    public static void zip(List<File> listFiles, String destZipFile, boolean deleteFolder, File folderToDelete) throws IOException {
        FileOutputStream fos = new FileOutputStream(destZipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);
        for (File file : listFiles) {
            if (file.isDirectory()) {
                zipDirectory(file, file.getName(), zos);
            } else {
                zipFile(file, zos);
            }
        }
        zos.flush();
        zos.close();
        fos.close();

        if (deleteFolder) deleteRecursively(folderToDelete);

    }

    private static void zipDirectory(File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), zos);
                continue;
            }
            zos.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            long bytesRead = 0;
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = bis.read(bytesIn)) != -1) {
                zos.write(bytesIn, 0, read);
                bytesRead += read;
            }
            zos.closeEntry();
        }
    }


    private static void zipFile(File file, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(file.getName()));
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        long bytesRead = 0;
        byte[] bytesIn = new byte[BUFFER_SIZE];
        int read = 0;
        while ((read = bis.read(bytesIn)) != -1) {
            zos.write(bytesIn, 0, read);
            bytesRead += read;
        }
        bis.close();
        zos.closeEntry();
    }

    //Deletes a folder and its files recursively.
    public static void deleteRecursively(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteRecursively(f);
                } else {
                    f.delete();
                }
            }
        }
        file.delete();
    }
}
