package com.thecolonel63.serversidereplayrecorder.util;

import com.thecolonel63.serversidereplayrecorder.ServerSideReplayRecorderServer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static void zipDirectory(@NotNull File folder, String parentFolder, ZipOutputStream zos) throws IOException {
        File[] list = folder.listFiles();
        assert list != null: "%s is not a directory".formatted(folder);
        for (File file : list) {
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
        new OutputStreamWriter(zos);
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

    public static Text uploadToTemp(File file) throws IOException{
        String attachmentName = "file";
        String attachmentFileName = file.getName();
        String crlf = "\r\n";
        String twoHyphens = "--";
        String boundary =  "123456";

        HttpsURLConnection httpUrlConnection = null;
        URL url = ServerSideReplayRecorderServer.config.getFile_storage_url();
        httpUrlConnection = (HttpsURLConnection) url.openConnection();
        httpUrlConnection.setUseCaches(false);
        httpUrlConnection.setDoOutput(true);
        httpUrlConnection.setChunkedStreamingMode(4096);

        httpUrlConnection.setRequestMethod("POST");
        httpUrlConnection.setRequestProperty("Connection", "Keep-Alive");
        httpUrlConnection.setRequestProperty("Cache-Control", "no-cache");
        httpUrlConnection.setRequestProperty(
                "Content-Type", "multipart/form-data;boundary=" + boundary);

        DataOutputStream request = new DataOutputStream(
                httpUrlConnection.getOutputStream());

        request.writeBytes(twoHyphens + boundary + crlf);
        request.writeBytes("Content-Disposition: form-data; name=\"" +
                attachmentName + "\";filename=\"" +
                attachmentFileName + "\"" + crlf);
        request.writeBytes(crlf);
        request.flush();

        Files.copy(file.toPath(), request);

        request.writeBytes(crlf);
        request.writeBytes(twoHyphens + boundary +
                twoHyphens + crlf);

        request.flush();
        request.close();

        InputStream responseStream = new
                BufferedInputStream(httpUrlConnection.getInputStream());

        BufferedReader responseStreamReader =
                new BufferedReader(new InputStreamReader(responseStream));

        String line = "";
        StringBuilder stringBuilder = new StringBuilder();

        while ((line = responseStreamReader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        responseStreamReader.close();

        String response = stringBuilder.toString();

        responseStream.close();

        httpUrlConnection.disconnect();

        Text response_text = Text.literal(response).formatted(Formatting.YELLOW);

        if (ServerSideReplayRecorderServer.upload_sites.containsKey(url.getHost())){
            String regex = ServerSideReplayRecorderServer.upload_sites.getString(url.getHost());
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()){
                String download_url = matcher.group(1);
                response_text = Text.literal("%s download link: ".formatted(file.getName())).formatted(Formatting.YELLOW)
                        .append(Text.literal(download_url)
                                .formatted(Formatting.UNDERLINE,Formatting.BLUE)
                                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, download_url))));
            }
        }

        return response_text;
    }
}
