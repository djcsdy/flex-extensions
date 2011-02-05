////////////////////////////////////////////////////////////////////////////////
//
//  ADOBE SYSTEMS INCORPORATED
//  Copyright 2004-2007 Adobe Systems Incorporated
//  All Rights Reserved.
//
//  NOTICE: Adobe permits you to use, modify, and distribute this file
//  in accordance with the terms of the license agreement accompanying it.
//
////////////////////////////////////////////////////////////////////////////////

package flex2.compiler.media;

import flex2.compiler.SymbolTable;
import flex2.compiler.Transcoder;
import flex2.compiler.TranscoderException;
import flex2.compiler.common.PathResolver;
import flex2.compiler.io.VirtualFile;
import flex2.compiler.util.MimeMappings;
import flash.swf.tags.DefineSound;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Transcodes sounds into DefineSounds for embedding.
 *
 * @author Clement Wong
 */
public class SoundTranscoder extends AbstractTranscoder
{
	// MP3 sampling rate...
	private static final int[][] mp3frequencies;

	// Bitrate
	private static final int[][] mp3bitrates;
	private static final int[][] mp3bitrateIndices;

    private static final int DECODER_DELAY = 529;


    static
	{
		// [frequency_index][version_index]
		mp3frequencies = new int[][]
		{
			{11025, 0, 22050, 44100},
			{12000, 0, 24000, 48000},
			{8000, 0, 16000, 32000},
			{0, 0, 0, 0}
		};

		// [bits][version,layer]
		mp3bitrates = new int[][]
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

		mp3bitrateIndices = new int[][]
		{
			// reserved, layer III, layer II, layer I
			{-1, 4, 4, 3}, // version 2.5
			{-1, -1, -1, -1}, // reserved
			{-1, 4, 4, 3}, // version 2
			{-1, 2, 1, 0}  // version 1
		};
	}

	public SoundTranscoder()
	{
		super(new String[]{MimeMappings.MP3}, DefineSound.class, true);
	}

    public boolean isSupportedAttribute( String attr )
    {
        return false;   // no attributes yet
    }

	public TranscodingResults doTranscode( PathResolver context, SymbolTable symbolTable,
                                           Map<String, Object> args, String className, boolean generateSource )
        throws TranscoderException
	{
        TranscodingResults results = new TranscodingResults( resolveSource( context, args ));
        String newName = (String) args.get( Transcoder.NEWNAME );

        TranscodeJob job = new TranscodeJob();
        results.defineTag = job.transcode(results.assetSource, newName);

        if (generateSource)
            generateSource( results, className, args );
        
        return results;
	}

    private class TranscodeJob
    {
        private BufferedInputStream in = null;
        private int encoderDelay = 0;
        private int fill = 0;
        private ByteArrayOutputStream soundData = null;

        private int countFrames (byte[] data)
        {
            int count = 0, start = 2, b1, b2, b3;//, b4;
            boolean skipped = false;

            while (start + 2 < data.length)
            {
                b1 = data[start] & 0xff;
                b2 = data[start + 1] & 0xff;
                b3 = data[start + 2] & 0xff;

                // check frame sync
                if (b1 != 255 || (b2 >> 5 & 0x7) != 7)
                {
                    if (!skipped && start > 0)  // LAME has a bug where they do padding wrong sometimes
                    {
                        b3 = b2;
                        b2 = b1;
                        b1 = data[start-1] & 0xff;
                        if (b1 != 255 || (b2 >> 5 & 0x7) != 7)
                        {
                            ++start;
                            continue;
                        }
                        else
                        {
                            --start;
                        }
                    }
                    else
                    {
                        ++start;
                        continue;
                    }
                }

                /**
                 * 0 - version 2.5
                 * 1 - reserved
                 * 2 - version 2
                 * 3 - version 1
                 */
                int version = b2 >> 3 & 0x3;

                /**
                 * 0 - reserved
                 * 1 - layer III => 1152 samples
                 * 2 - layer II  => 1152 samples
                 * 3 - layer I   => 384  samples
                 */
                int layer = b2 >> 1 & 0x3;

                int bits = b3 >> 4 & 0xf;
                int bitrateIndex = mp3bitrateIndices[version][layer];
                int bitrate = bitrateIndex != -1 ? mp3bitrates[bits][bitrateIndex] * 1000 : -1;

                if (bitrate == -1)
                {
                    skipped = true;
                    ++start;
                    continue;
                }

                int samplingRate = b3 >> 2 & 0x3;

                int frequency = mp3frequencies[samplingRate][version];

                if (frequency == 0)
                {
                    skipped = true;
                    ++start;
                    continue;
    //                return -1;
                }

                int padding = b3 >> 1 & 0x1;

                int frameLength = layer == 3 ?
                        (12 * bitrate / frequency + padding) * 4 :
                        144 * bitrate / frequency + padding;

                if (frameLength == 0)
                {
                    // just in case. if we don't check frameLength, we may end up running an infinite loop!
                    break;
                }
                else
                {
                    start += frameLength;
                }

                skipped = false;
                count += 1;
            }

            return count;
        }

        private void processInfoTag () throws IOException, NotInMP3Format {
            in.mark(40);
            in.skip(4);

            byte[][] signatures = new byte[][]{
                    "Xing".getBytes("US-ASCII"),
                    "Info".getBytes("US-ASCII")};

            int pos = 0;
            byte[] signature = null;
            int b;

            while ((b = in.read()) == 0)
            {
                ++pos;
            }

            pos = 0;

            do
            {
                if (pos == 0) {
                    for (byte[] s: signatures)
                    {
                        if (s[0] == b)
                        {
                            signature = s;
                            break;
                        }
                    }

                    if (signature == null) {
                        in.reset();
                        return;
                    }
                }
                else
                {
                    if (signature[pos] != b)
                    {
                        in.reset();
                        return;
                    }
                }
                ++pos;
            } while (pos < 4 && (b = in.read()) != -1);

            in.skip(137);

            if ((b = in.read()) == -1)
            {
                throw new NotInMP3Format();
            }
            else
            {
                encoderDelay = b << 4;
            }
            if ((b = in.read()) == -1)
            {
                throw new NotInMP3Format();
            }
            else
            {
                encoderDelay |= (b >> 4) & 15;
                fill = b << 8;
            }
            if ((b = in.read()) == -1)
            {
                throw new NotInMP3Format();
            }
            else
            {
                fill |= b;
            }
        }

        private void processRemainingFrames () throws IOException {
            int b;
            while ((b = in.read()) != -1)
            {
                soundData.write(b);
            }
        }

        private boolean skipToNextFrame () throws IOException {
            // look for the first 11-bit frame sync. skip everything before the frame sync
            int b, state = 0;

            in.mark(3);
            while ((b = in.read()) != -1)
            {
                if (state == 0)
                {
                    if (b == 255)
                    {
                        state = 1;
                    }
                    else
                    {
                        in.mark(3);
                    }
                }
                else if (state == 1)
                {
                    if ((b >> 5 & 0x7) == 7)
                    {
                        in.reset();
                        return true;
                    }
                    else
                    {
                        state = 0;
                    }
                }
            }

            return false;
        }

        private DefineSound transcode (VirtualFile source, String symbolName) throws NotInMP3Format, ExceptionWhileTranscoding, UnsupportedSamplingRate, CouldNotDetermineSampleFrameCount {
            InputStream in = null;
            byte[] sound;
            try {
                int size = (int) source.size();
                in = source.getInputStream();

                this.in = new BufferedInputStream(in);
                soundData = new ByteArrayOutputStream(size + 2);

                // placeholder for number of samples to skip.
                soundData.write(0);
                soundData.write(0);

                if (skipToNextFrame()) {
                    processInfoTag();

                    skipToNextFrame();
                    processRemainingFrames();
                } else {
                    throw new NotInMP3Format();
                }

                sound = soundData.toByteArray();
            } catch (IOException ex) {
                throw new ExceptionWhileTranscoding(ex);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ex) {
                    }
                }
            }

            if (sound == null || sound.length < 5)
            {
                throw new NotInMP3Format();
            }

            // write number of samples to skip.
            final int totalDelay = encoderDelay + DECODER_DELAY;
            sound[0] = (byte)(totalDelay & 255);
            sound[1] = (byte)((totalDelay >> 8) & 255);


            DefineSound ds = new DefineSound();
            ds.format = 2; // MP3

            ds.data = sound;
            ds.size = 1; // always 16-bit for compressed formats
            ds.name = symbolName;

            /**
             * 0 - version 2.5
             * 1 - reserved
             * 2 - version 2
             * 3 - version 1
             */
            int version = ds.data[3] >> 3 & 0x3;

            /**
             * 0 - reserved
             * 1 - layer III => 1152 samples
             * 2 - layer II  => 1152 samples
             * 3 - layer I   => 384  samples
             */
            int layer = ds.data[3] >> 1 & 0x3;

            int samplingRate = ds.data[4] >> 2 & 0x3;

            /**
             * 0 - stereo
             * 1 - joint stereo
             * 2 - dual channel
             * 3 - single channel
             */
            int channelMode = ds.data[5] >> 6 & 0x3;

            int frequency = mp3frequencies[samplingRate][version];

            /**
             * 1 - 11kHz
             * 2 - 22kHz
             * 3 - 44kHz
             */
            switch (frequency)
            {
            case 11025:
                ds.rate = 1;
                break;
            case 22050:
                ds.rate = 2;
                break;
            case 44100:
                ds.rate = 3;
                break;
            default:
                throw new UnsupportedSamplingRate( frequency );
            }

            /**
             * 0 - mono
             * 1 - stereo
             */
            ds.type = channelMode == 3 ? 0 : 1;

            /**
             * assume that the whole thing plays in one SWF frame
             *
             * sample count = number of MP3 frames * number of samples per MP3
             */
            ds.sampleCount = countFrames(ds.data) * (layer == 3 ? 384 : 1152) - fill - encoderDelay;

            if (ds.sampleCount < 0)
            {
                // frame count == -1, error!
                throw new CouldNotDetermineSampleFrameCount();
            }

            return ds;
        }
    }

    public static final class CouldNotDetermineSampleFrameCount extends TranscoderException {

        private static final long serialVersionUID = 6530096120116320212L;}
    public static final class UnsupportedSamplingRate extends TranscoderException
    {
        private static final long serialVersionUID = -7440513635414375590L;
        public UnsupportedSamplingRate( int frequency )
        {
            this.frequency = frequency;
        }
        public int frequency;
    }

	public static final class NotInMP3Format extends TranscoderException {

        private static final long serialVersionUID = -2480509003403956321L;}
}
