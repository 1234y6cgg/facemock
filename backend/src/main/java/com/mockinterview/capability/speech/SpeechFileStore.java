package com.mockinterview.capability.speech;
import com.mockinterview.config.SpeechProperties;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.IOException;

@Component
public class SpeechFileStore {
    private final Path root;
    public SpeechFileStore(SpeechProperties properties) { root=Path.of(properties.getStoragePath()).toAbsolutePath().normalize(); }
    private Path path(String id) {
        if(!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("录音标识不正确");
        var path=root.resolve(id+".wav").normalize();
        if(!path.getParent().equals(root)) throw new IllegalArgumentException("录音路径不正确");
        return path;
    }
    public void write(String id,byte[] bytes) {
        Path temp=null;
        try { Files.createDirectories(root); temp=Files.createTempFile(root,"upload-",".tmp"); Files.write(temp,bytes);
            try { Files.move(temp,path(id),StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(temp,path(id)); }
        } catch(IOException e) { throw new IllegalStateException("录音保存失败，请重试",e); }
        finally { if(temp!=null) try { Files.deleteIfExists(temp); } catch(IOException ignored) {} }
    }
    public byte[] read(String id) { try { return Files.readAllBytes(path(id)); } catch(IOException e) { throw new IllegalStateException("录音文件暂时无法读取",e); } }
    public void delete(String id) { try { Files.deleteIfExists(path(id)); } catch(IOException e) { throw new IllegalStateException("录音文件清理失败，请重试",e); } }
}
