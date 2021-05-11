/*
 * Copyright 2004-2021 Guinsoo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Guinsoo Group
 */
package org.guinsoo.build.doc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.guinsoo.build.indexer.HtmlConverter;
import org.guinsoo.store.fs.FileUtils;
import org.guinsoo.util.IOUtils;
import org.guinsoo.util.StringUtils;

/**
 * Converts a HTML or Unicode encoded file to UTF-8.
 */
public class FileConverter {

    private String inFile = "~/temp/in.txt";
    private String outFile = "~/temp/out.txt";

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new FileConverter().run(args);
    }

    private void run(String... args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-in")) {
                inFile = args[++i];
            } else if (args[i].equals("-out")) {
                outFile = args[++i];
            }
        }
        String languageCode = Locale.KOREA.getLanguage();
        String language = new Locale(languageCode)
                .getDisplayLanguage(new Locale(languageCode));
        System.out.println(language);
        System.out.println(StringUtils.javaEncode(language));
        convert();
    }

    private void convert() throws IOException {
        InputStream in = FileUtils.newInputStream(inFile);
        byte[] bytes = IOUtils.readBytesAndClose(in, -1);
        String s = new String(bytes, StandardCharsets.UTF_8);
        String s2 = HtmlConverter.convertHtmlToString(s);
        String s3 = StringUtils.javaDecode(s2);
        byte[] result = s3.getBytes(StandardCharsets.UTF_8);
        OutputStream out = FileUtils.newOutputStream(outFile, false);
        out.write(result);
        out.close();
    }

}