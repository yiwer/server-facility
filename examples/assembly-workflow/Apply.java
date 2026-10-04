import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Fixed example overlay, only on an unchanged independent copy of the declared template. */
class Apply {
 public static void main(String[] args) throws Exception {
  if (args.length != 2) throw new IllegalArgumentException("Supply example directory and fresh independent application");
  Path example=path(args[0]).toRealPath(), app=path(args[1]).toRealPath();
  if (app.startsWith(example) || example.startsWith(app)) throw new IllegalArgumentException("Independent application required");
  var origin=new Properties();
  try(var reader=Files.newBufferedReader(app.resolve("template-origin.properties"))){origin.load(reader);}
  if(!"secured-api".equals(origin.getProperty("template.id")) || !"2026.10.0".equals(origin.getProperty("template.revision"))
      || !"cn.code91:server-facility:0.2.0-SNAPSHOT".equals(origin.getProperty("runtime.coordinate")))
   throw new IllegalArgumentException("Template lineage mismatch");
  var preimages=new Properties();
  try(var reader=Files.newBufferedReader(example.resolve("preimages.properties"))){preimages.load(reader);}
  // Validate every input before touching any destination. Text hashes normalize CRLF for cross-OS checkouts.
  for(String name:new TreeSet<>(preimages.stringPropertyNames())) {
   Path relative=Path.of(name), target=app.resolve(relative).normalize();
   if(relative.isAbsolute() || !target.startsWith(app) || name.contains("..")) throw new IllegalArgumentException("Invalid overlay path");
   for(Path ancestor=target;ancestor.startsWith(app);ancestor=ancestor.getParent())
    if(Files.isSymbolicLink(ancestor)) throw new IllegalArgumentException("Symbolic links unsupported");
   String expected=preimages.getProperty(name);
   if(expected.equals("absent") ? Files.exists(target) : !Files.isRegularFile(target) || !hash(target).equals(expected))
    throw new IllegalArgumentException("Template preimage mismatch: "+name);
   if(!Files.isRegularFile(example.resolve("overlay").resolve(relative))) throw new IllegalArgumentException("Missing overlay source: "+name);
  }
  for(String name:new TreeSet<>(preimages.stringPropertyNames())) {
   Path target=app.resolve(name);Files.createDirectories(target.getParent());
   Files.copy(example.resolve("overlay").resolve(name),target,StandardCopyOption.REPLACE_EXISTING);
  }
  System.out.println("WORKFLOW_OVERLAY_PASS files="+preimages.size());
 }
 static String hash(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readString(file).replace("\r\n","\n").getBytes(StandardCharsets.UTF_8)));}
 static Path path(String value){return value.startsWith("file:")?Path.of(URI.create(value)):Path.of(value);}
}
