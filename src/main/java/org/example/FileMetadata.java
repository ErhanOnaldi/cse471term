package org.example;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class FileMetadata {

    private File file;
    private String fileHash;
    private long fileSize;

    public FileMetadata(File f) {
        this.file = f;
        this.fileSize = f.length();
        this.fileHash = calculateHash(f);
    }

    private String calculateHash(File f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            FileInputStream fis = new FileInputStream(f);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            fis.close();

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getFileHash() {
        return fileHash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return file.getName();
    }

    public File getFile() {
        return file;
    }
}
