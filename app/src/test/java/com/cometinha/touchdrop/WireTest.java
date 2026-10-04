package com.cometinha.touchdrop;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Tests real data integrity and failure cleanup without Android or third-party dependencies. */
public final class WireTest {
    interface Task { void run() throws Exception; }
    static void fail(Task t) throws Exception {try{t.run();}catch(IOException expected){return;}throw new AssertionError("Expected rejection");}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("touchdrop-test-");
        try{
            byte[] image=new byte[1024*1024+7];new Random(7).nextBytes(image);
            File original=dir.resolve("original").toFile();Files.write(original.toPath(),image);
            String hash=Wire.hash(original);File received=dir.resolve("received").toFile();
            long[] count={0};Wire.receive(new ByteArrayInputStream(image),received,image.length,hash,(a,b)->count[0]=a);
            check(Arrays.equals(image,Files.readAllBytes(received.toPath()))&&count[0]==image.length);
            System.out.println("PASS: streamed image and SHA-256 verification");
            fail(()->Wire.receive(new ByteArrayInputStream(Arrays.copyOf(image,100)),received,image.length,hash,null));
            check(!received.exists());System.out.println("PASS: truncated image rejected and partial file removed");
            byte[] corrupt=image.clone();corrupt[200]^=1;
            fail(()->Wire.receive(new ByteArrayInputStream(corrupt),received,image.length,hash,null));check(!received.exists());
            System.out.println("PASS: altered image rejected and partial file removed");
            fail(()->Wire.checkSize(0));fail(()->Wire.checkSize(-1));fail(()->Wire.checkSize(Wire.MAX_BYTES+1));Wire.checkSize(Wire.MAX_BYTES);
            System.out.println("PASS: empty/oversized data rejected before transfer");
            fail(()->Wire.receive(new ByteArrayInputStream(image),received,image.length,"invalid",null));
            String token="0123456789abcdef0123456789abcdef";check(Wire.tokenMatches(token,token));check(!Wire.tokenMatches(token,"1123456789abcdef0123456789abcdef"));check(!Wire.tokenMatches(null,token));check(!Wire.tokenMatches("",token));
            System.out.println("PASS: invalid digest and unknown session tokens rejected");
            ByteArrayInputStream input=new ByteArrayInputStream(new byte[]{1,2,3,99});ByteArrayOutputStream output=new ByteArrayOutputStream();Wire.copyExact(input,output,3,null);check(input.read()==99&&output.size()==3);
            System.out.println("PASS: exact payload boundary preserved");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ex){throw new UncheckedIOException(ex);}});}}
    }
}
