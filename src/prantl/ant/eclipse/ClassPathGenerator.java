// Copyright 2005-2006 Ferdinand Prantl <prantl@users.sourceforge.net>
// Copyright 2001-2004 The Apache Software Foundation
// All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// See http://ant-eclipse.sourceforge.net for the most recent version
// and more information.

package prantl.ant.eclipse;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

/**
 * Provides the functionality generating the file <tt>.classpath</tt> for the
 * supplied task object. It is expected to be used within the class EclipseTask.
 * 
 * @see EclipseTask
 * @since Ant-Eclipse 1.0
 * @author Ferdinand Prantl &lt;prantl@users.sourceforge.net&gt;
 */
final class ClassPathGenerator {

  /**
   * Contains a ready-to write information about a binary classpath entry -
   * element kinds "lib" or "var". Fields of this class match attributes of the
   * element <tt>classpath</tt>.
   * 
   * @see ClassPathGenerator#writeProcessedBinaryClassPathEntries(XmlWriter,
   *      String, Vector)
   * @since Ant-Eclipse 1.0
   * @author Ferdinand Prantl &lt;prantl@users.sourceforge.net&gt;
   */
  static class ProcessedBinaryClassPathEntry {

    String kind;
    String path;
    boolean exported;
    String sourcepath;
    String javadoc_location;

  }

  private EclipseTask task;

  /**
   * Creates a new instance of the generating object.
   * 
   * @param parent
   *          The parent task.
   * @since Ant-Eclipse 1.0
   */
  ClassPathGenerator(EclipseTask parent) {
    task = parent;
  }

  /**
   * Generates the file <tt>.classpath</tt> using the supplied output object.
   * 
   * @since Ant-Eclipse 1.0
   */
  void generate() {
    ClassPathElement classPath = task.getEclipse().getClassPath();
    if (classPath == null) {
      task.log("There was no description of a classpath found.", Project.MSG_WARN);
      return;
    }
    EclipseOutput output = task.getOutput();
    if (output.isClassPathUpToDate()) {
      task.log("The classpath definition is up-to-date.", Project.MSG_WARN);
      return;
    }
    task.log("Writing the classpath definition.");
    XmlWriter writer = null;
    try {
      writer = new XmlWriter(new OutputStreamWriter(new BufferedOutputStream(output.createClassPath()), "UTF-8"));
      writer.writeXmlDeclaration("UTF-8");
      writer.openElement("classpath");
      checkClassPathEntries(classPath);
      generateContainerClassPathEntry(writer);
      generateSourceClassPathEntries(writer);
      Vector<ProcessedBinaryClassPathEntry> entries = new Vector<ProcessedBinaryClassPathEntry>();
      processVariableClassPathEntries(entries, classPath.getVariables());
      processLibraryClassPathEntries(entries, classPath.getLibraries());
      writeProcessedBinaryClassPathEntries(writer, entries);
      generateOutputClassPathEntry(writer);
      writer.closeElement("classpath");
    } catch (UnsupportedEncodingException exception) {
      throw new BuildException("Encoder to UTF-8 is not supported.", exception);
    } catch (IOException exception) {
      throw new BuildException("Writing the classpath definition failed.", exception);
    } finally {
      if (writer != null)
        try {
          writer.close();
        } catch (IOException exception1) {
          throw new BuildException("Closing the classpath definition failed.", exception1);
        }
    }
  }

  private void generateContainerClassPathEntry(XmlWriter writer) throws IOException {
    ClassPathEntryContainerElement container = task.getEclipse().getClassPath().getContainer();
    if (container == null) {
      task.log("No container found, a default one added.", Project.MSG_VERBOSE);
      container = new ClassPathEntryContainerElement();
    }
    container.validate();
    String path = container.getPath();
    if (path.indexOf('/') < 0 && !path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
      task.log("Prepending the container class name to the container path \"" + path + "\".", Project.MSG_VERBOSE);
      path = "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/" + path;
    }
    task.log("Adding container \"" + path + "\".", Project.MSG_VERBOSE);
    openClassPathEntry(writer, "con", path);
    writer.closeDegeneratedElement();
  }

  private void generateSourceClassPathEntries(XmlWriter writer) throws IOException {
    Vector<ClassPathEntrySourceElement> entries = task.getEclipse().getClassPath().getSources();
    if (entries.size() == 0) {
      task.log("No source found, the current directory added.", Project.MSG_VERBOSE);
      entries.addElement(new ClassPathEntrySourceElement());
    }
    for (int i = 0, size = entries.size(); i != size; ++i) {
      ClassPathEntrySourceElement entry = entries.get(i);
      entry.validate();
      String excluding = entry.getExcluding();
      String output = entry.getOutput();
      Path path = new Path(task.getProject());
      Reference reference = entry.getPathRef();
      String[] items = path.list();
      if (reference != null) {
        path.setRefid(reference);
        items = path.list();
      } else {
        String value = entry.getPath();
        if (value.length() == 0)
          task.log("Using the current directory as a default source path.", Project.MSG_VERBOSE);
        items = new String[] { value };
      }
      String baseDirectory = task.getProject().getBaseDir().getAbsolutePath();
      for (int j = 0; j != items.length; ++j) {
        String item = cutBaseDirectory(items[j], baseDirectory);
        task.log("Adding sources from \"" + item + "\".", Project.MSG_VERBOSE);
        openClassPathEntry(writer, "src", item);
        if (excluding != null)
          writer.appendAttribute("excluding", excluding);
        if (output != null)
          writer.appendAttribute("output", output);
        writer.closeDegeneratedElement();
      }
    }
  }

  private void processVariableClassPathEntries(Vector<ProcessedBinaryClassPathEntry> entries, Vector<ClassPathEntryVariableElement> paths) {
    processBinaryClassPathEntries(entries, "var", paths);
  }

  private void processLibraryClassPathEntries(Vector<ProcessedBinaryClassPathEntry> entries, Vector<ClassPathEntryLibraryElement> paths) {
    processBinaryClassPathEntries(entries, "lib", paths);
  }

  private void processBinaryClassPathEntries(Vector<ProcessedBinaryClassPathEntry> entries, String kind, Vector<? extends ClassPathEntryBinaryElement> binaries) {
    for (int i = 0, size = binaries.size(); i != size; ++i) {
      ClassPathEntryBinaryElement entry = binaries.get(i);
      entry.validate();
      Path path = new Path(task.getProject());
      Reference reference = entry.getPathRef();
      if (reference != null)
        path.setRefid(reference);
      else {
        String value = entry.getPath();
        path.setPath(value);
      }
      processBinaryClassPathEntries(entries, kind, entry.getExported(), entry.getSource(), entry.getSourcePattern(), entry.getJavadoc(), path.list());
    }
  }

  private void processBinaryClassPathEntries(Vector<ProcessedBinaryClassPathEntry> entries, String kind, boolean exported, String source, String sourcePattern,
      String javadoc_location, String[] items) {
    String baseDirectory = task.getProject().getBaseDir().getAbsolutePath();
    for (int j = 0; j != items.length; ++j) {
      String item = cutBaseDirectory(items[j], baseDirectory);
      ProcessedBinaryClassPathEntry element = getProcessedBinaryClassPathEntry(entries, item);
      if (element == null) {
        task.log("Processing binary dependency \"" + item + "\" of the kind \"" + kind + "\".", Project.MSG_VERBOSE);
        element = new ProcessedBinaryClassPathEntry();
        element.kind = kind;
        element.path = item;
        element.exported = exported;
        element.sourcepath = discoverSource(baseDirectory, item, sourcePattern, source);
        element.javadoc_location = javadoc_location;
        entries.addElement(element);
      } else {
        task.log("Updating binary dependency \"" + item + "\" of the kind \"" + kind + "\".", Project.MSG_VERBOSE);
        element.kind = kind;
        element.path = item;
        element.exported = exported;
        element.sourcepath = discoverSource(baseDirectory, item, sourcePattern, source);
        element.javadoc_location = javadoc_location;
      }
    }
  }

  private String discoverSource(String baseDirectory, String item, String sourcePattern, String source) {
    if (source != null) {
      task.log(String.format("Skipping source discovery as source was set (%s) so using it", source), Project.MSG_VERBOSE);
      return source;
    }
    if (baseDirectory == null) {
      task.log("Skipping source discovery as baseDirectory was null", Project.MSG_VERBOSE);
      return null;
    }
    if (item == null || item.trim().length() < 1) {
      task.log(String.format("Skipping source discovery as item was null"), Project.MSG_VERBOSE);
      return null;
    }
    File baseDir = new File(baseDirectory);
    if (!baseDir.canRead()) {
      task.log(String.format("Skipping source discovery as I can't read baseDirectory (%s)", baseDir.getAbsolutePath()), Project.MSG_VERBOSE);
      return null;
    }
    if (!baseDir.isDirectory()) {
      task.log(String.format("Skipping source discovery as baseDirectory (%s) is not a dir", baseDir.getAbsolutePath()), Project.MSG_VERBOSE);
      return null;
    }
    if (sourcePattern == null || sourcePattern.trim().length() < 1) {
      task.log(String.format("Skipping source discovery as sourcePattern is blank"), Project.MSG_VERBOSE);
      return null;
    }
    String[] sourcePatters = sourcePattern.split(",");
    for (String pattern : sourcePatters) {
      task.log(String.format("trying source pattern (%s) in source discovery", pattern), Project.MSG_DEBUG);
      String sourceItem = String.format("%s-%s.jar", removeExtension(item), pattern);
      File itemSourceFile = new File(baseDir, sourceItem);
      if (itemSourceFile.canRead()) {
        task.log(String.format("Discovered source jar (%s) for item %s", sourceItem, item), Project.MSG_VERBOSE);
        return sourceItem;
      }
      sourceItem = String.format("%s-%s.zip", removeExtension(item), pattern);
      itemSourceFile = new File(baseDir, sourceItem);
      if (itemSourceFile.canRead()) {
        task.log(String.format("Discovered source zip (%s) for item %s", sourceItem, item), Project.MSG_VERBOSE);
        return sourceItem;
      }
    }

    task.log(String.format("Discovered no sources for item %s", item), Project.MSG_DEBUG);
    return null;
  }

  /**
   * Extract the path part of a path, dropping the file on the end.
   * 
   * @param item
   * @return a string in all cases, may be empty but will always terminate in a
   *         separator if not blank.
   */
  protected String extractPath(String item) {
    String separator = "/";
    if (item == null) {
      return "";
    }
    StringTokenizer stok = new StringTokenizer(item, separator);
    // just the filename, no path.
    if (stok.countTokens() < 2) {
      if (item.startsWith(separator)) {
        return separator;
      }
      return "";
    }
    // path and file, return path.
    if (stok.countTokens() == 2) {
      return String.format("%s%s", stok.nextToken(), separator);
    }
    StringBuilder buf = new StringBuilder();
    if (item.startsWith(separator)) {
      buf.append(separator);
    }
    String path = stok.nextToken();
    // we know we want this one, were not sure of more.
    buf.append(path);
    buf.append(separator);
    String possibleFile = stok.nextToken();
    // if we have more, start appending
    while (stok.hasMoreTokens()) {
      buf.append(path);
      buf.append(separator);
      path = possibleFile;
      possibleFile = stok.nextToken();
    }

    return buf.toString();
  }

  private String removeExtension(String aFileName) {
    if (aFileName == null || !aFileName.contains(".")) {
      return aFileName;
    }
    int lastIndex = aFileName.lastIndexOf(".");
    return aFileName.substring(0, lastIndex);
  }

  private void writeProcessedBinaryClassPathEntries(XmlWriter writer, Vector<ProcessedBinaryClassPathEntry> entries) throws IOException {
    for (int i = 0, size = entries.size(); i != size; ++i) {
      ProcessedBinaryClassPathEntry element = entries.get(i);
      task.log("Adding binary dependency \"" + element.path + "\" of the kind \"" + element.kind + "\".", Project.MSG_VERBOSE);
      openClassPathEntry(writer, element.kind, element.path);
      if (element.exported)
        writer.appendAttribute("exported", "true");
      if (element.sourcepath != null)
        writer.appendAttribute("sourcepath", element.sourcepath);
      if (element.javadoc_location != null) {
        writer.closeOpeningTag();
        writer.openElement("attributes");
        writer.openOpeningTag("attribute");
        writer.appendAttribute("value", element.javadoc_location);
        writer.appendAttribute("name", "javadoc_location");
        writer.closeDegeneratedElement();
        writer.closeElement("attributes");
        writer.closeElement("classpathentry");
      } else
        writer.closeDegeneratedElement();
    }
  }

  private void generateOutputClassPathEntry(XmlWriter writer) throws IOException {
    ClassPathEntryOutputElement output = task.getEclipse().getClassPath().getOutput();
    if (output == null) {
      task.log("No output found, the current directory added.", Project.MSG_VERBOSE);
      output = new ClassPathEntryOutputElement();
    }
    output.validate();
    String path = cutBaseDirectory(output.getPath(), task.getProject().getBaseDir().getAbsolutePath());
    task.log("Adding output into \"" + path + "\".", Project.MSG_VERBOSE);
    openClassPathEntry(writer, "output", path);
    writer.closeDegeneratedElement();
  }

  private void openClassPathEntry(XmlWriter writer, String kind, String path) throws IOException {
    writer.openOpeningTag("classpathentry");
    writer.appendAttribute("kind", kind);
    writer.appendAttribute("path", path);
  }

  private String cutBaseDirectory(String path, String base) {
    if (!path.startsWith(base))
      return path;
    task.log("Cutting base directory \"" + base + "\" from the path \"" + path + "\".", Project.MSG_VERBOSE);
    return path.substring(base.length() + 1);
  }

  private void checkClassPathEntries(ClassPathElement classPath) {
    if (task.getEclipse().getMode().getIndex() == EclipseElement.Mode.ASPECTJ && getClassPathEntry(classPath.getVariables(), "ASPECTJRT_LIB") == null) {
      ClassPathEntryVariableElement variable = classPath.createVariable();
      variable.setPath("ASPECTJRT_LIB");
      variable.setSource("ASPECTJRT_SRC");
    }
  }

  private static ClassPathEntryElement getClassPathEntry(Vector<ClassPathEntryVariableElement> entries, String path) {
    for (int i = 0, size = entries.size(); i != size; ++i) {
      ClassPathEntryElement entry = entries.get(i);
      if (path.equals(entry.getPath()))
        return entry;
    }
    return null;
  }

  private static ProcessedBinaryClassPathEntry getProcessedBinaryClassPathEntry(Vector<ProcessedBinaryClassPathEntry> entries, String path) {
    for (int i = 0, size = entries.size(); i < size; ++i) {
      ProcessedBinaryClassPathEntry entry = entries.get(i);
      if (entry.path.equals(path))
        return entry;
    }
    return null;
  }
}
