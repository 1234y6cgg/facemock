package com.mockinterview.service.model;

import com.mockinterview.service.question.QuestionJson;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** Local personal deployment. The master key stays in its own persistent volume, never in API/export. */
public final class ModelSecretStore {
    private final Path directory;
    private final QuestionJson json;
    public ModelSecretStore(Path directory, QuestionJson json) { this.directory=directory; this.json=json; }
    public record Stored(String providerId, String baseUrl, String modelName, boolean jsonMode, long revision, String encryptedKey) {}
    public Optional<Stored> read() {
        var file=directory.resolve("model.json"); if (!Files.exists(file)) return Optional.empty();
        try { return Optional.of(json.read(Files.readString(file), Stored.class)); }
        catch (Exception e) { throw new IllegalStateException("模型配置无法读取，请检查配置卷和文件权限"); }
    }
    private byte[] master(boolean create) throws Exception {
        var file=directory.resolve("master.key");
        if (!Files.exists(file)) {
            if (!create) throw new IllegalStateException("模型解密密钥丢失");
            Files.createDirectories(directory); var bytes=new byte[32];new SecureRandom().nextBytes(bytes);
            Files.write(file,bytes,StandardOpenOption.CREATE_NEW); protect(file);
        }
        var bytes=Files.readAllBytes(file);if (bytes.length!=32) throw new IllegalStateException("模型解密密钥无效");return bytes;
    }
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return "";
        try {var data=Base64.getDecoder().decode(encrypted);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(master(false),"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(data,0,12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(data,12,data.length)),java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("模型密钥无法解密，请检查配置卷或重新配置"); }
    }
    public void write(ModelConnection c) {
        Path temporary=null;
        try {Files.createDirectories(directory);String encrypted="";
            if(c.configured()){var nonce=new byte[12];new SecureRandom().nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(master(true),"AES"),new GCMParameterSpec(128,nonce));
                var payload=cipher.doFinal(c.apiKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));var out=new byte[nonce.length+payload.length];
                System.arraycopy(nonce,0,out,0,nonce.length);System.arraycopy(payload,0,out,nonce.length,payload.length);encrypted=Base64.getEncoder().encodeToString(out);}
            temporary=Files.createTempFile(directory,"model-",".tmp");protect(temporary);
            Files.writeString(temporary,json.write(new Stored(c.providerId(),c.baseUrl(),c.modelName(),c.jsonMode(),c.revision(),encrypted)));
            try{Files.move(temporary,directory.resolve("model.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,directory.resolve("model.json"),StandardCopyOption.REPLACE_EXISTING);}
            protect(directory.resolve("model.json"));
        } catch(Exception e){throw new IllegalStateException("模型配置保存失败，原配置仍保留，请检查配置卷权限");}
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){}}
    }
    private void protect(Path file) throws java.io.IOException {
        try {Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
    }
}
