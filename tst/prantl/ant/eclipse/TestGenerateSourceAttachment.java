package prantl.ant.eclipse;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.tools.ant.Project;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestGenerateSourceAttachment {

  
  @Test
  public void testGenerateSourceForLib() {
    assertHasSource("lib/dist/", "source");
    assertHasSource("lib/dist/", "src");
  }

  private void assertHasSource(String path, String filePattern) {

    final String expectedLibraryName = "someLibrary";
    final String expecedLibNode = String.format("<classpathentry kind=\"lib\" path=\"%1$s%2$s.jar\" sourcepath=\"%1$s%2$s-%3$s.jar\" />", path,
        expectedLibraryName, filePattern);
    final EclipseElement eclipseElement = new EclipseElement();
    final StringBuilder buf = new StringBuilder();
    final EclipseOutput eclipseOutput = mock(EclipseOutput.class);
    when(eclipseOutput.getEclipse()).thenReturn(eclipseElement);
    when(eclipseOutput.isClassPathUpToDate()).thenReturn(false);
    when(eclipseOutput.createClassPath()).thenAnswer(new Answer<OutputStream>() {

      public OutputStream answer(InvocationOnMock invocation) throws Throwable {
        return new AppendableOutputStream(buf);
      }
    });

    EclipseTask eclipseTask = new EclipseTask(eclipseOutput);
    Project project = new Project();
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    project.setBaseDir(baseDir);
    eclipseTask.setProject(project);

    ClassPathElement classPathElement = new ClassPathElement();
    ClassPathEntryLibraryElement classPathEntryLibraryElement = new ClassPathEntryLibraryElement();
    classPathEntryLibraryElement.setSourcePattern(filePattern);
    //Reference reference = new Reference(project, "compile.classpath");
    //classPathEntryLibraryElement.setPathRef(reference);
    classPathEntryLibraryElement.setPath(String.format("%s%s.jar", path, expectedLibraryName));
    classPathElement.getLibraries().add(classPathEntryLibraryElement);
    eclipseTask.getEclipse().setClassPath(classPathElement);

    generateSourcesJarFile(baseDir, path, filePattern, expectedLibraryName);
    eclipseTask.execute();
    removeSourcesJarFile(baseDir, path, filePattern, expectedLibraryName);

    assertContains(buf.toString(), expecedLibNode);

  }

  private void removeSourcesJarFile(File rootDir, String path, String postfix, String fileName) {
    File baseDir = new File(rootDir, path);
    while (!baseDir.equals(rootDir)) {
      File[] files = baseDir.listFiles();
      // remove the files first
      for (File file : files) {
        if (!file.isDirectory() && file.canWrite()) {
          assertTrue(String.format("Failed to delete file %s, you may need to clean this up", file.getAbsolutePath()), file.delete());
        }
      }
      // now remove any directories.
      for (File file : files) {
        if (file.isDirectory() && file.canWrite()) {
          assertTrue(String.format("Failed to delete dir %s, you may need to clean this up", file.getAbsolutePath()), file.delete());
        }
      }
      File parent = baseDir.getParentFile();
      // delete myself
      assertTrue(String.format("Failed to delete basedir %s, you may need to clean this up", baseDir.getAbsolutePath()), baseDir.delete());
      baseDir = parent;
    }
  }

  private void generateSourcesJarFile(File rootDir, String path, String postfix, String fileName) {
    assertNotNull("Please provide a path", path);
    File baseDir = new File(rootDir, path);
    if (!baseDir.exists()) {
      assertTrue(String.format("We should have created the path %s", path), baseDir.mkdirs());
    }
    File sourceFile = new File(baseDir, String.format("%s-%s.jar", fileName, postfix));
    if (sourceFile.exists()) {
      assertTrue(String.format("We should have deleted %s", sourceFile.getAbsolutePath()), sourceFile.delete());
    }
    try {
      assertTrue(String.format("We should have created a new file %s", sourceFile.getAbsolutePath()), sourceFile.createNewFile());
    } catch (IOException e) {
      fail(String.format("We failed to create new file %s", sourceFile.getAbsolutePath()));
      return;
    }
  }

  private void assertContains(String haystack, String needle) {
    assertNotNull("We need a string, not null", haystack);
    if (!haystack.contains(needle)) {
      fail(String.format("We expected string to contain %s: %s", needle, haystack));
    }
  }
}
