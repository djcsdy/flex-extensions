package flex2.compiler.media.mp3;

import flash.swf.tags.DefineSound;
import flex2.compiler.TranscoderException;
import flex2.compiler.io.VirtualFile;
import flex2.compiler.media.AbstractTranscoder;
import flex2.compiler.media.SoundTranscoder;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MP3TranscodeJob
{
    private static final String US_ASCII = "US-ASCII";
    private static final String XING_TAG_SIGNATURE = "Xing";
    private static final String INFO_TAG_SIGNATURE = "Info";
    private static final int GAPLESS_INFO_OFFSET = 141;
    private static final int INFO_TAG_LENGTH = 348;
    private static final int DECODER_DELAY = 529;

    private int encoderDelay = 0;
    private int fill = 0;

    private boolean processInfoTag (MP3Frame frame) {
        byte[] frameData = frame.getFrameData();

        int i;
        for (i=4; i<frameData.length - INFO_TAG_LENGTH; ++i) {
            if (frameData[i] != 0) {
                break;
            }
        }

        if (i >= frameData.length - INFO_TAG_LENGTH) {
            return false;
        }

        String signature;
        try {
            signature = new String(frameData, i, 4, US_ASCII);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }

        if (!XING_TAG_SIGNATURE.equals(signature) &&
                !INFO_TAG_SIGNATURE.equals(signature)) {
            return false;
        }

        byte b0 = frameData[i+GAPLESS_INFO_OFFSET];
        byte b1 = frameData[i+GAPLESS_INFO_OFFSET+1];
        byte b2 = frameData[i+GAPLESS_INFO_OFFSET+2];

        encoderDelay = ((b0 << 4) & 0xff0) | ((b1 >> 4) & 0xf);
        fill = ((b1 << 8) & 0xf00) | (b2 & 0xff);

        return true;
    }

    public DefineSound transcode (VirtualFile source, String symbolName)
            throws NotInMP3Format, UnsupportedMP3, AbstractTranscoder.ExceptionWhileTranscoding,
            SoundTranscoder.UnsupportedSamplingRate {
        BufferedInputStream in;
        MP3Frame firstFrame;
        byte[] soundData;
        long sampleCount = 0;

        try {
            int size = (int)source.size();

            in = new BufferedInputStream(source.getInputStream());
            ByteArrayOutputStream soundDataStream = new ByteArrayOutputStream(size + 2);

            // Placeholder for number of samples to skip.
            soundDataStream.write(0);
            soundDataStream.write(0);

            firstFrame = MP3Frame.readNextFrame(in);
            if (firstFrame == null) {
                throw new NotInMP3Format();
            }

            if (processInfoTag(firstFrame)) {
                firstFrame = MP3Frame.readNextFrame(in);
                if (firstFrame == null) {
                    throw new NotInMP3Format();
                }
            }

            MP3Frame frame = firstFrame;
            do {
                if (!firstFrame.compatibleWith(frame)) {
                    throw new UnsupportedMP3();
                }

                soundDataStream.write(frame.getFrameData());
                sampleCount += frame.getSampleCount();
            } while ((frame = MP3Frame.readNextFrame(in)) != null);

            soundData = soundDataStream.toByteArray();
        } catch (IOException e) {
            throw new AbstractTranscoder.ExceptionWhileTranscoding(e);
        }

        // Write number of samples to skip.
        final int totalDelay = encoderDelay + DECODER_DELAY;
        soundData[0] = (byte)(totalDelay & 255);
        soundData[1] = (byte)((totalDelay >> 8) & 255);

        DefineSound ds = new DefineSound();
        ds.format = 2; // MP3

        ds.data = soundData;
        ds.size = 1; // always 16-bit for compressed formats
        ds.name = symbolName;

        int frequency = firstFrame.getFrequency();

        switch (frequency)
        {
        case 11025:
            ds.rate = 1; // 11kHz
            break;
        case 22050:
            ds.rate = 2; // 22kHz
            break;
        case 44100:
            ds.rate = 3; // 44kHz
            break;
        default:
            throw new SoundTranscoder.UnsupportedSamplingRate( frequency );
        }

        if (firstFrame.getChannelMode() == MP3Frame.MODE_MONO) {
            ds.type = 0; // mono
        } else {
            ds.type = 1; // stereo
        }

        ds.sampleCount = sampleCount - encoderDelay - fill;

        return ds;
    }

    public static final class NotInMP3Format extends TranscoderException {
        private static final long serialVersionUID = -2480509003403956321L;
    }

    private class UnsupportedMP3 extends TranscoderException {
        private static final long serialVersionUID = 7779399583307651213L;
    }
}
