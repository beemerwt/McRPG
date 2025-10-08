package com.github.beemerwt.mcrpg.data;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.CRC32;

public final class BinaryPlayerStore implements PlayerStore, Closeable {
    private static final int  MAGIC       = 0x52504746; // "RPGF"
    private static final short VERSION    = 1;          // now writing v3
    private static final short HEADER_SIZE= 8;
    private static final byte REC_PLAYER  = 1;

    // TLV tags for v3
    private static final byte TAG_SKILL_XP      = 0x10;

    private final Path dbPath = FabricLoader.getInstance().getConfigDir()
            .resolve("McRPG").resolve("players.db");

    private final RandomAccessFile raf;
    private final FileChannel chan;

    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final Map<UUID, Long> latestOffset = new HashMap<>();

    private final int skillCount = SkillType.values().length;
    private short fileVersion = VERSION;

    public BinaryPlayerStore() {
        try {
            Files.createDirectories(dbPath.getParent());
            boolean exists = Files.exists(dbPath);
            raf = new RandomAccessFile(dbPath.toFile(), "rw");
            chan = raf.getChannel();

            if (!exists || chan.size() == 0) {
                writeHeader();
            } else {
                verifyHeader();
            }
            buildIndex();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open players.db", e);
        }
    }

    @Override
    public synchronized @NotNull PlayerData get(UUID id) {
        var pd = cache.get(id);
        if (pd != null) return pd;
        var off = latestOffset.get(id);
        if (off != null) {
            var loaded = readAt(off);
            if (loaded != null) { cache.put(id, loaded); return loaded; }
        }
        pd = new PlayerData(id);
        cache.put(id, pd);
        return pd;
    }

    @Override
    public List<PlayerData> all() {
        List<PlayerData> all = new ArrayList<>();
        for (var id : latestOffset.keySet()) {
            all.add(get(id));
        }
        return all;
    }

    @Override
    public synchronized void save(UUID id) {
        var pd = cache.get(id);
        if (pd == null) return;
        long off = appendRecord(pd);
        latestOffset.put(id, off);
    }

    @Override
    public synchronized void saveAll() {
        for (var id : cache.keySet()) save(id);
    }

    public synchronized void compact() {
        Path tmp = dbPath.resolveSibling("players.compacting.db");
        try (RandomAccessFile outRaf = new RandomAccessFile(tmp.toFile(), "rw");
             FileChannel outChan = outRaf.getChannel()) {
            writeHeader(outChan); // writes VERSION=3
            for (var e : latestOffset.entrySet()) {
                var pd = cache.get(e.getKey());
                if (pd == null) pd = readAt(e.getValue());
                if (pd != null) appendRecord(pd, outChan); // writes v3 payloads
            }
            outChan.force(true);
        } catch (IOException ex) {
            throw new RuntimeException("Compaction failed", ex);
        }

        try {
            Path bak = dbPath.resolveSibling("players.db.bak");
            Files.deleteIfExists(bak);
            Files.move(dbPath, bak, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, dbPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new RuntimeException("Compaction swap failed", ex);
        }

        try { chan.close(); raf.close(); } catch (IOException ignored) {}
    }

    @Override
    public synchronized void close() {
        try { saveAll(); chan.force(true); } catch (IOException ignored) {}
        try { chan.close(); } catch (IOException ignored) {}
        try { raf.close(); } catch (IOException ignored) {}
    }

    private void writeHeader() throws IOException {
        writeHeader(chan);
        chan.force(true);
        fileVersion = VERSION;
    }

    private static void writeHeader(FileChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(MAGIC);
        buf.putShort(VERSION);
        buf.putShort(HEADER_SIZE);
        buf.flip();
        while (buf.hasRemaining()) out.write(buf);
    }

    private void verifyHeader() throws IOException {
        chan.position(0);
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        readFully(buf);
        int magic = buf.getInt();
        short version = buf.getShort();
        short headerSize = buf.getShort();
        if (magic != MAGIC || headerSize != HEADER_SIZE || (version != 1 && version != 2 && version != 3)) {
            throw new IOException("players.db header mismatch");
        }
        this.fileVersion = version;
    }

    private void buildIndex() throws IOException {
        latestOffset.clear();
        cache.clear();

        long pos = HEADER_SIZE;
        long size = chan.size();
        ByteBuffer hdr = ByteBuffer.allocate(1 + 4).order(ByteOrder.BIG_ENDIAN);

        while (pos + 5 <= size) {
            hdr.clear();
            readAt(pos, hdr);
            byte recType = hdr.get();
            int payloadLen = hdr.getInt();
            long recordStart = pos;
            long payloadStart = pos + 5;
            long payloadEnd = payloadStart + payloadLen;
            long crcPos = payloadEnd;
            long nextPos = crcPos + 4;

            if (recType != REC_PLAYER || payloadEnd > size || nextPos > size) break;

            ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.BIG_ENDIAN);
            readAt(payloadStart, payload);

            ByteBuffer crcBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            readAt(crcPos, crcBuf);
            int storedCrc = crcBuf.getInt();
            int calcCrc = crc32(payload.array());
            if (storedCrc != calcCrc) break;

            payload.rewind();
            long time = payload.getLong(); // consumed here but we don’t index by it
            long most = payload.getLong();
            long least = payload.getLong();
            UUID id = new UUID(most, least);

            latestOffset.put(id, recordStart);
            var pd = parsePayload(id, payload);
            cache.put(id, pd);

            pos = nextPos;
        }

        chan.position(size);
    }

    private PlayerData readAt(long recordOffset) {
        try {
            ByteBuffer hdr = ByteBuffer.allocate(1 + 4).order(ByteOrder.BIG_ENDIAN);
            readAt(recordOffset, hdr);
            byte recType = hdr.get();
            int payloadLen = hdr.getInt();
            if (recType != REC_PLAYER) return null;

            long payloadStart = recordOffset + 5;
            long crcPos = payloadStart + payloadLen;

            ByteBuffer payload = ByteBuffer.allocate(payloadLen).order(ByteOrder.BIG_ENDIAN);
            readAt(payloadStart, payload);

            ByteBuffer crcBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            readAt(crcPos, crcBuf);
            int storedCrc = crcBuf.getInt();
            int calcCrc = crc32(payload.array());
            if (storedCrc != calcCrc) return null;

            payload.rewind();
            payload.getLong(); // time
            long most = payload.getLong();
            long least = payload.getLong();
            UUID id = new UUID(most, least);
            return parsePayload(id, payload);
        } catch (IOException e) {
            return null;
        }
    }

    // ---- Versioned payload parsing ----

    private PlayerData parsePayload(UUID id, ByteBuffer payload) {
        return switch (fileVersion) {
            case 1 -> parsePayloadV1(id, payload);
            default -> parsePayloadV1(id, payload); // be liberal
        };
    }

    // v3 (variable TLV with PD_SIZE)
    // Layout:
    // [time i64][uuidMost i64][uuidLeast i64]
    // [pdSize u32]  // size in bytes of the TLV region below
    // TLV... until consumed pdSize bytes:
    //   [tag u8][len u32][payload ...len bytes...]
    // TAG_SKILL_XP payload:  [id u2][xp i64]
    // TAG_ABILITY_WIN payload:[id u2][cooldown i64][active i64]
    private PlayerData parsePayloadV1(UUID id, ByteBuffer p) {
        PlayerData pd = new PlayerData(id);
        int pdSize = p.getInt();
        int startPos = p.position();
        int endPos = startPos + pdSize;

        while (p.position() < endPos) {
            if (endPos - p.position() < 5) { // need tag(1) + len(4)
                p.position(endPos);
                break;
            }
            byte tag = p.get();
            int len = p.getInt();
            int bodyStart = p.position();
            int bodyEnd = bodyStart + len;
            if (bodyEnd > endPos || len < 0) { // corrupt; bail out of this entry
                p.position(endPos);
                break;
            }

            try {
                switch (tag) {
                    case TAG_SKILL_XP -> {
                        short skillId = p.getShort();
                        long xp = p.getLong();

                        SkillType s = safeSkill(skillId);
                        if (s != null) pd.xp.put(s, xp);
                    }
                    default -> {
                        // Unknown tag: skip
                        p.position(bodyEnd);
                    }
                }
            } finally {
                // Ensure we land exactly on bodyEnd even if a branch under-read
                p.position(bodyEnd);
            }
        }
        // If future fields follow TLVs, they can be read here without breaking older readers.
        return pd;
    }

    private SkillType safeSkill(short id) {
        try { return SkillType.fromId(id); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private ActiveAbilityType safeAbility(short id) {
        try { return ActiveAbilityType.fromId(id); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private long appendRecord(PlayerData pd) { return appendRecord(pd, chan); }

    private long appendRecord(PlayerData pd, FileChannel out) {
        try {
            byte[] payload = buildPayloadV1(pd);
            int crc = crc32(payload);

            long startPos = out.size();
            out.position(startPos);

            ByteBuffer head = ByteBuffer.allocate(1 + 4).order(ByteOrder.BIG_ENDIAN);
            head.put(REC_PLAYER);
            head.putInt(payload.length);
            head.flip();
            writeFully(out, head);

            writeFully(out, ByteBuffer.wrap(payload));

            ByteBuffer crcBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            crcBuf.putInt(crc).flip();
            writeFully(out, crcBuf);

            out.force(true);
            return startPos;
        } catch (IOException e) {
            throw new RuntimeException("Append failed", e);
        }
    }

    // Build v3 payload per the variable TLV structure
    private byte[] buildPayloadV1(PlayerData pd) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
             DataOutputStream out = new DataOutputStream(baos)) {

            // Common fixed header
            out.writeLong(System.currentTimeMillis());
            out.writeLong(pd.uuid.getMostSignificantBits());
            out.writeLong(pd.uuid.getLeastSignificantBits());

            // We will buffer the TLV region to compute PD_SIZE
            ByteArrayOutputStream tlvBaos = new ByteArrayOutputStream(128);
            DataOutputStream tlv = new DataOutputStream(tlvBaos);

            // SKILL_XP entries (only those present; you can switch to all Skills if you prefer)
            for (var e : pd.xp.entrySet()) {
                SkillType s = e.getKey();
                long xp = e.getValue();
                byte[] body = buildSkillXpBody(s.id(), xp);
                putTLV(tlv, TAG_SKILL_XP, body);
            }

            tlv.flush();
            byte[] tlvBytes = tlvBaos.toByteArray();

            // PD_SIZE
            out.writeInt(tlvBytes.length);
            out.write(tlvBytes);

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] buildSkillXpBody(short id, long xp) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
             DataOutputStream out = new DataOutputStream(baos)) {
            out.writeShort(Short.toUnsignedInt(id));
            out.writeLong(xp);
            out.flush();
            return baos.toByteArray();
        }
    }

    private static void putTLV(DataOutputStream out, byte tag, byte[] body) throws IOException {
        out.writeByte(tag);
        out.writeInt(body.length);
        out.write(body);
    }

    private static int crc32(byte[] data) {
        CRC32 c = new CRC32();
        c.update(data, 0, data.length);
        long v = c.getValue();
        return (int) v;
    }

    private void readFully(ByteBuffer dst) throws IOException {
        dst.clear();
        while (dst.hasRemaining()) {
            if (chan.read(dst) < 0) throw new EOFException();
        }
        dst.flip();
    }

    private void readAt(long pos, ByteBuffer dst) throws IOException {
        dst.clear();
        long p = pos;
        while (dst.hasRemaining()) {
            int r = chan.read(dst, p);
            if (r < 0) throw new EOFException();
            p += r;
        }
        dst.flip();
    }

    private static void writeFully(FileChannel ch, ByteBuffer src) throws IOException {
        while (src.hasRemaining()) ch.write(src);
    }
}
