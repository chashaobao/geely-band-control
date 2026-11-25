package com.garage.bandcontrol;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CarImageTest {
    @Test public void packagesPngBytesIntoDeterministicHashAndBoundedChunks() throws Exception {
        byte[] sample = new byte[3500];
        for (int i = 0; i < sample.length; i++) sample[i] = (byte) (i * 31 + 7);

        CarImage.Packaged first = CarImage.packagePng(sample);
        CarImage.Packaged second = CarImage.packagePng(sample);
        assertNotNull(first);
        assertEquals(first.hash(), second.hash());
        assertEquals(12, first.hash().length());
        assertTrue(first.chunks().size() >= 3);

        StringBuilder reassembled = new StringBuilder();
        for (int i = 0; i < first.chunks().size(); i++) {
            String chunk = first.chunks().get(i);
            assertTrue(chunk.length() <= CarImage.CHUNK_CHARS);
            String envelope = BandMessage.carImageReply("img_" + i, first.hash(), i, first.chunks().size(), chunk).toString();
            assertTrue(envelope.getBytes(StandardCharsets.UTF_8).length <= BandMessage.MAX_BYTES);
            reassembled.append(chunk);
        }
        assertArrayEquals(sample, Base64.getDecoder().decode(reassembled.toString()));
    }
}
