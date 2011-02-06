package flex2.compiler.media.mp3;

import java.io.BufferedInputStream;
import java.io.IOException;

public class MP3Frame {

    public static final int MODE_MONO = 3;

    public static final int MPEG_LAYER_1 = 3;

    private static final int[][] FREQUENCIES;
	private static final int[][] BITRATES;
    private static final int[][] BITRATE_VERSION_INDICES;

    static
	{
		// [frequency_index][version_index]
		FREQUENCIES = new int[][]
		{
			{11025, 0, 22050, 44100},
			{12000, 0, 24000, 48000},
			{8000, 0, 16000, 32000},
			{0, 0, 0, 0}
		};

		// [bits][version,layer]
		BITRATES = new int[][]
		{
			{0, 0, 0, 0, 0},
			{32000, 32000, 32000, 32000, 8000},
			{64000, 48000, 40000, 48000, 16000},
			{96000, 56000, 48000, 56000, 24000},
			{128000, 64000, 56000, 64000, 32000},
			{160000, 80000, 64000, 80000, 40000},
			{192000, 96000, 80000, 96000, 48000},
			{224000, 112000, 96000, 112000, 56000},
			{256000, 128000, 112000, 128000, 64000},
			{288000, 160000, 128000, 144000, 80000},
			{320000, 192000, 160000, 160000, 96000},
			{352000, 224000, 192000, 176000, 112000},
			{384000, 256000, 224000, 192000, 128000},
			{416000, 320000, 256000, 224000, 144000},
			{448000, 384000, 320000, 256000, 160000},
			{-1, -1, -1, -1, -1}
		};

		BITRATE_VERSION_INDICES = new int[][]
		{
			// reserved, layer III, layer II, layer I
			{-1, 4, 4, 3}, // MPEG version 2.5
			{-1, -1, -1, -1}, // reserved
			{-1, 4, 4, 3}, // MPEG version 2
			{-1, 2, 1, 0}  // MPEG version 1
		};
	}

    private int mpegVersion;
    private int layer;
    private int frequencyIndex;
    private int channelMode;

    private byte[] frameData;

    static MP3Frame readNextFrame (BufferedInputStream in) throws IOException {
        int pos = 0, b;

        while ((b = in.read()) != -1) {
            if (pos == 0) {
                if (b == 0xff) {
                    pos = 1;
                }
            } else {
                if ((b >> 5 & 0x7) == 0x7) {
                    MP3Frame frame = doReadFrame(b, in);
                    if (frame == null) {
                        // The frame header turned out to be invalid.
                        // Ignore that header and continue searching from the next byte after b.
                        pos = 0;
                    } else {
                        return frame;
                    }
                } else {
                    pos = 0;
                }
            }
        }

        return null;
    }

    private static MP3Frame doReadFrame (int b1, BufferedInputStream in) throws IOException {
        // Mark the current position in the buffer so we can continue from here if the
        // frame header is invalid.
        in.mark(2);

        int b2, b3, pos;
        if ((b2 = in.read()) == -1 || (b3 = in.read()) == -1) {
            return null;
        }

        MP3Frame frame = new MP3Frame();

        frame.mpegVersion = b1 >> 3 & 0x3;
        frame.layer = b1 >> 1 & 0x3;
        int bitrateIndex = b2 >> 4 & 0xf;
        frame.frequencyIndex = b2 >> 2 & 0x3;
        int padding = b2 >> 1 & 0x1;
        frame.channelMode = b3 >> 6 & 0x3;

        int bitrateVersionIndex = BITRATE_VERSION_INDICES[frame.mpegVersion][frame.layer];
        if (bitrateVersionIndex == -1) {
            in.reset();
            return null;
        }
        int bitrate = BITRATES[bitrateIndex][bitrateVersionIndex];

        int frequency = frame.getFrequency();

        if (bitrate <= 0 || frequency == 0) {
            in.reset();
            return null;
        }

        int byteLength;
        if (frame.layer == MPEG_LAYER_1) {
            byteLength = (12 * bitrate / frequency + padding) * 4;
        } else {
            byteLength = 144 * bitrate / frequency + padding;
        }

        if (byteLength < 4) {
            in.reset();
            return null;
        }

        frame.frameData = new byte[byteLength];

        frame.frameData[0] = (byte)0xff;
        frame.frameData[1] = (byte)b1;
        frame.frameData[2] = (byte)b2;
        frame.frameData[3] = (byte)b3;

        pos = 4;
        while (pos < byteLength) {
            int bytesRead = in.read(frame.frameData, pos, byteLength-pos);
            if (bytesRead == -1) {
                // LAME < 3.80 truncates the last frame instead of properly padding it
                // with ancillary data. Our frameData is already padded with zeroes,
                // so we repair the problem by breaking here.
                break;
            }
            pos += bytesRead;
        }

        return frame;
    }

    public int getFrequency () {
        return FREQUENCIES[frequencyIndex][mpegVersion];
    }

    public int getChannelMode () {
        return channelMode;
    }

    public int getSampleCount () {
        if (layer == MPEG_LAYER_1) {
            return 384;
        } else {
            return 1152;
        }
    }

    public byte[] getFrameData () {
        return frameData;
    }

    public boolean compatibleWith (MP3Frame frame) {
        return layer == frame.layer &&
                mpegVersion == frame.mpegVersion &&
                frequencyIndex == frame.frequencyIndex &&
                channelMode == frame.channelMode;
    }

}
