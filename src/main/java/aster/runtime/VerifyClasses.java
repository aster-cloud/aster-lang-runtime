package aster.runtime;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public final class VerifyClasses {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: VerifyClasses <classes-dir>");
      System.exit(2);
    }
    File dir = new File(args[0]);
    if (!dir.isDirectory()) {
      System.err.println("Not a directory: " + dir);
      System.exit(2);
    }
    List<String> classes = new ArrayList<>();
    collect(dir, dir, classes);
    URLClassLoader ldr = new URLClassLoader(new URL[] { dir.toURI().toURL() });
    int ok = 0;
    for (String cn : classes) {
      try {
        Class.forName(cn, false, ldr);
        ok++;
      } catch (Throwable t) {
        System.err.println("VERIFY FAIL: " + cn + ": " + t);
        System.exit(1);
      }
    }
    System.out.println("Verified classes: " + ok);
  }

  private static void collect(File root, File d, List<String> out) {
    for (File f : d.listFiles()) {
      if (f.isDirectory()) collect(root, f, out);
      else if (f.getName().endsWith(".class")) {
        String rel = f.getAbsolutePath().substring(root.getAbsolutePath().length()+1);
        String cn = rel.substring(0, rel.length()-6).replace(File.separatorChar, '.');
        out.add(cn);
      }
    }
  }
}

