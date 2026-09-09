package researchflow.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stage next to the destination; never fall back to an unsafe overwrite. */
public final class AtomicFiles {
    private AtomicFiles() { }

    public static Path stage(Path destination) throws IOException {
        var parent = destination.toAbsolutePath().normalize().getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Choose an existing writable destination directory.");
        return Files.createTempFile(parent, ".researchflow-", ".staging");
    }

    public static void publish(Path staged, Path destination) throws IOException {
        try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.move(staged, destination.toAbsolutePath().normalize(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                var buffer = new byte[8192]; int length;
                while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static void discard(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) { /* A staging orphan is safer than touching the destination. */ }
    }
}
