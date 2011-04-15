/*
 * Copyright 2011 Daniel J. Cassidy.
 * Copyright 2004-2007 Adobe Systems Incorporated.
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is "Adobe Flex SDK".
 *
 * The Initial Developer of the Original Code is Adobe Systems Incorporated.
 *
 * Portions created by Adobe Systems Incorporated are Copyright 2004-2007 Adobe
 * Systems Incorporated. All Rights Reserved.
 *
 * Contributor(s): Daniel J. Cassidy.
 *
 */

package flex2.compiler.media;

import flex2.compiler.SymbolTable;
import flex2.compiler.Transcoder;
import flex2.compiler.TranscoderException;
import flex2.compiler.common.PathResolver;
import net.noiseinstitute.flexextensions.compiler.media.mp3.MP3TranscodeJob;
import flex2.compiler.util.MimeMappings;
import flash.swf.tags.DefineSound;

import java.util.Map;

/**
 * Transcodes sounds into DefineSounds for embedding.
 *
 * @author Clement Wong
 * @author Daniel J. Cassidy
 */
public class SoundTranscoder extends AbstractTranscoder {

    public SoundTranscoder () {
        super(new String[]{MimeMappings.MP3}, DefineSound.class, true);
    }

    public boolean isSupportedAttribute (String attr) {
        return false;
    }

    public TranscodingResults doTranscode (PathResolver context, SymbolTable symbolTable,
            Map<String, Object> args, String className, boolean generateSource)
            throws TranscoderException {
        TranscodingResults results = new TranscodingResults(resolveSource(context, args));
        String newName = (String) args.get(Transcoder.NEWNAME);

        MP3TranscodeJob job = new MP3TranscodeJob();
        results.defineTag = job.transcode(results.assetSource, newName);

        if (generateSource)
            generateSource(results, className, args);

        return results;
    }

}
