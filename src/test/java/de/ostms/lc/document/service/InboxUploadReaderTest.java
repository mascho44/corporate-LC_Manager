package de.ostms.lc.document.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class InboxUploadReaderTest {
    @Test void expandsFoldersAndIgnoresMacMetadata() throws Exception {
        var result=InboxUploadReader.read(List.of(zip(List.of("Rechnungen/invoice.txt","Packlisten/packing.txt","__MACOSX/._invoice.txt",".DS_Store"),new byte[]{1,2})));
        assertThat(result).extracting(InboxUploadReader.Upload::filename).containsExactly("Rechnungen/invoice.txt","Packlisten/packing.txt");
        assertThat(result.get(0).content()).containsExactly(1,2);
    }
    @Test void supportsZipAndOrdinaryDocumentsInSameBatch() throws Exception {
        var result=InboxUploadReader.read(List.of(new MockMultipartFile("file","letter.txt","text/plain",new byte[]{1}),zip(List.of("invoice.txt"),new byte[]{2})));
        assertThat(result).extracting(InboxUploadReader.Upload::filename).containsExactly("letter.txt","invoice.txt");
    }
    @Test void rejectsUnsafePathsAndNestedArchives() throws Exception {
        for(String name:List.of("../secret.txt","/secret.txt","C:/secret.txt","folder/../secret.txt","folder/nested.zip")){
            var archive=zip(List.of(name),new byte[]{1});
            assertThatThrownBy(()->InboxUploadReader.read(List.of(archive))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsMoreThanOneHundredExpandedDocuments() throws Exception {
        List<String> names=new ArrayList<>();for(int i=0;i<101;i++)names.add("doc"+i+".txt");
        var archive=zip(names,new byte[]{1});
        assertThatThrownBy(()->InboxUploadReader.read(List.of(archive))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100");
    }
    @Test void limitsRealDecompressedSizeRatherThanCompressedSize() throws Exception {
        var oversizedFile=zip(List.of("large.txt"),new byte[10*1024*1024+1]);
        assertThatThrownBy(()->InboxUploadReader.read(List.of(oversizedFile))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 MB");
        var oversizedBatch=zip(List.of("a.txt","b.txt","c.txt","d.txt","e.txt","f.txt"),new byte[9*1024*1024]);
        assertThatThrownBy(()->InboxUploadReader.read(List.of(oversizedBatch))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50 MB");
    }
    @Test void rejectsInvalidAndEmptyArchives() throws Exception {
        var invalid=new MockMultipartFile("file","bad.zip","application/zip",new byte[]{1,2});
        var empty=zip(List.of(),new byte[]{1});
        assertThatThrownBy(()->InboxUploadReader.read(List.of(invalid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->InboxUploadReader.read(List.of(empty))).isInstanceOf(IllegalArgumentException.class);
    }
    private MockMultipartFile zip(List<String> names,byte[] content) throws IOException {
        try(var output=new ByteArrayOutputStream();var zip=new ZipOutputStream(output)){
            for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write(content);zip.closeEntry();}zip.finish();
            return new MockMultipartFile("file","documents.zip","application/zip",output.toByteArray());
        }
    }
}
