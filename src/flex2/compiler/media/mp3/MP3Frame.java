package flex2.compiler.media.mp3;

import java.io.BufferedInputStream;
import java.io.IOException;

public class MP3Frame {

    private static final int MPEG_LAYER_1 = 3;

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
			{32, 32, 32, 32, 8},
			{64, 48, 40, 48, 16},
			{96, 56, 48, 56, 24},
			{128, 64, 56, 64, 32},
			{160, 80, 64, 80, 40},
			{192, 96, 80, 96, 48},
			{224, 112, 96, 112, 56},
			{256, 128, 112, 128, 64},
			{288, 160, 128, 144, 80},
			{320, 192, 160, 160, 96},
			{352, 224, 192, 176, 112},
			{384, 256, 224, 192, 128},
			{416, 320, 256, 224, 144},
			{448, 384, 320, 256, 160},
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
        int pos = 0, b1, b2, b3;

        while ((b1 = in.read()) != -1) {
            if (pos == 0) {
                if (b1 == 0xff) {
                    pos = 1;
                }
            } else {
                if ((b1 >> 5 & 0x7) == 0x7) {
                    pos = 2;
                    break;
                }
            }
        }

        if (pos < 2) {
            return null;
        }

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
            return null;
        }
        int bitrate = BITRATES[bitrateIndex][bitrateVersionIndex];

        int frequency = frame.getFrequency();

        int byteLength = frame.layer == MPEG_LAYER_1 ?
                (12 * bitrate / frequency + padding) * 4 :
                144 * bitrate / frequency + padding;

        if (byteLength < 4) {
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
                // lame < 3.80 truncates the last frame instead of properly padding it
                // with ancillary data. Our frameData is already padded with zeroes,
                // so we repair the problem by breaking here.
                break;
            }
            pos += bytesRead;
        }

        return frame;
    }

    public int getMPEGVersion () {
        return mpegVersion;
    }

    public int getLayer () {
        return layer;
    }

    public int getFrequencyIndex () {
        return frequencyIndex;
    }

    private int getFrequency () {
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
