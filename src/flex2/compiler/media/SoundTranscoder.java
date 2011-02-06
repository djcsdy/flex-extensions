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
import flex2.compiler.media.mp3.MP3TranscodeJob;
import flex2.compiler.util.MimeMappings;
import flash.swf.tags.DefineSound;

import java.util.Map;

/**
 * Transcodes sounds into DefineSounds for embedding.
 *
 * @author Clement Wong
 */
public class SoundTranscoder extends AbstractTranscoder
{

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

        MP3TranscodeJob job = new MP3TranscodeJob();
        results.defineTag = job.transcode(results.assetSource, newName);

        if (generateSource)
            generateSource( results, className, args );
        
        return results;
	}

    public static final class UnsupportedSamplingRate extends TranscoderException
    {
        private static final long serialVersionUID = -7440513635414375590L;
        public UnsupportedSamplingRate( int frequency )
        {
            this.frequency = frequency;
        }
        public int frequency;
    }

}
